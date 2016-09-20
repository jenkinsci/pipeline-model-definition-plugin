/*
 * The MIT License
 *
 * Copyright (c) 2016, CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */


package org.jenkinsci.plugins.pipeline.modeldefinition.steps

import com.cloudbees.groovy.cps.impl.CpsClosure
import hudson.FilePath
import hudson.Launcher
import hudson.model.Result
import org.jenkinsci.plugins.pipeline.modeldefinition.ClosureModelTranslator
import org.jenkinsci.plugins.pipeline.modeldefinition.Utils
import org.jenkinsci.plugins.pipeline.modeldefinition.model.MethodMissingWrapper
import org.jenkinsci.plugins.pipeline.modeldefinition.model.Stage
import org.jenkinsci.plugins.pipeline.modeldefinition.model.Agent
import org.jenkinsci.plugins.pipeline.modeldefinition.model.Root
import org.jenkinsci.plugins.pipeline.modeldefinition.model.Tools
import org.jenkinsci.plugins.workflow.cps.steps.ingroovy.GroovyStepExecution
import org.jenkinsci.plugins.workflow.steps.MissingContextVariableException

/**
 * CPS-transformed code for actually performing the build.
 *
 * @author Andrew Bayer
 */
public class PipelineModelStepExecution extends GroovyStepExecution implements MethodMissingWrapper {
    def call() {
        CpsClosure closure = ((PipelineModelStep)getStep()).closure

        // Attach the stages model to the run for introspection etc.
        Utils.attachExecutionModel(currentBuild)
        ClosureModelTranslator translator = new ClosureModelTranslator(Root.class)
        echo "Instantiated translator"
        closure.delegate = translator
        closure.resolveStrategy = Closure.DELEGATE_ONLY
        closure.call()
        echo "Ran translator"
        Root root = translator.toNestedModel()
        echo "Hey look, root: ${root}"

        Throwable firstError

        if (root != null) {
            // Entire build, including notifications, runs in the withEnv.
            withEnv(root.getEnvVars()) {
                // Stage execution and post-build actions run in try/catch blocks, so we still run post-build actions
                // even if the build fails, and we still send notifications if the build and/or post-build actions fail.
                // We save the caught error, if any, for throwing at the end of the build.
                nodeOrDockerOrNone(root.agent) {
                    toolsBlock(root.agent, root.tools) {
                            // If we have an agent and scm isn't null, run checkout scm
                            if (root.agent.hasAgent() && scm != null) {
                                checkout scm
                            }

                            for (int i = 0; i < root.stages.getStages().size(); i++) {
                                Stage thisStage = root.stages.getStages().get(i)

                                stage(thisStage.name) {
                                    if (firstError == null) {
                                        try {
                                            catchRequiredContextForNode(root.agent) {
                                                Closure closureToCall = thisStage.closureWrapper.closure
                                                closureToCall.delegate = this
                                                closureToCall.resolveStrategy = Closure.DELEGATE_FIRST
                                                closureToCall.call()
                                            }.call()
                                        } catch (Exception e) {
                                            echo "Error in stages execution: ${e.getMessage()}"
                                            currentBuild.result = Result.FAILURE
                                            if (firstError == null) {
                                                firstError = e
                                            }
                                        }
                                    }
                                }
                            }

                        try {
                            catchRequiredContextForNode(root.agent) {
                                List<Closure> postBuildClosures = root.satisfiedPostBuilds(currentBuild)
                                if (postBuildClosures.size() > 0) {
                                    stage("Post Build Actions") {
                                        for (int i = 0; i < postBuildClosures.size(); i++) {
                                            Closure c = postBuildClosures.get(i)
                                            c.delegate = this
                                            c.resolveStrategy = Closure.DELEGATE_FIRST
                                            c.call()
                                        }
                                    }
                                }
                            }.call()
                        } catch (Exception e) {
                            echo "Error in postBuild execution: ${e.getMessage()}"
                            currentBuild.result = Result.FAILURE
                            if (firstError == null) {
                                firstError = e
                            }
                        }
                    }.call()
                }.call()

                try {
                    // And finally, run the notifications.
                    List<Closure> notificationClosures = root.satisfiedNotifications(currentBuild)

                    catchRequiredContextForNode(root.agent, true) {
                        if (notificationClosures.size() > 0) {
                            stage("Notifications") {
                                for (int i = 0; i < notificationClosures.size(); i++) {
                                    Closure c = notificationClosures.get(i)
                                    c.delegate = this
                                    c.resolveStrategy = Closure.DELEGATE_FIRST
                                    c.call()
                                }
                            }
                        }
                    }.call()
                } catch (Exception e) {
                    echo "Error in notifications execution: ${e.getMessage()}"
                    currentBuild.result = Result.FAILURE
                    if (firstError == null) {
                        firstError = e
                    }
                }
            }
            if (firstError != null) {
                throw firstError
            }
        }
    }

    def catchRequiredContextForNode(Agent agent, boolean inNotifications = false, Closure body) throws Exception {
        return {
            try {
                body.call()
            } catch (MissingContextVariableException e) {
                if (FilePath.class.equals(e.type) || Launcher.class.equals(e.type)) {
                    if (inNotifications) {
                        error("Attempted to execute a notification step that requires a node context. Notifications do not run inside a 'node { ... }' block.")
                    } else if (!agent.hasAgent()) {
                        error("Attempted to execute a step that requires a node context while 'agent none' was specified. " +
                            "Be sure to specify your own 'node { ... }' blocks when using 'agent none'.")
                    } else {
                        throw e
                    }
                } else {
                    throw e
                }
            }
        }
    }

    def toolsBlock(Agent agent, Tools tools, Closure body) {
        // If there's no agent, don't install tools in the first place.
        if (agent.hasAgent() && tools != null) {
            def toolEnv = []
            def toolsList = tools.getToolEntries()
            for (int i = 0; i < toolsList.size(); i++) {
                def entry = toolsList.get(i)
                String k = entry.get(0)
                String v= entry.get(1)

                String toolPath = tool(name:v, type:Tools.typeForKey(k))

                toolEnv.addAll(envVarsForTool(toolId: Tools.typeForKey(k), toolVersion: v))
            }

            return {
                withEnv(toolEnv) {
                    body.call()
                }
            }
        } else {
            return {
                body.call()
            }
        }
    }

    /*
    TODO: The agent handling stuff here is just waiting for step-in-Groovy support..
     */
    def nodeOrDockerOrNone(Agent agent, Closure body) {
        if (agent.hasAgent()) {
            return {
                nodeWithLabelOrWithout(agent) {
                    dockerOrWithout(agent, body).call()
                }.call()
            }
        } else {
            return {
                body.call()
            }
        }
    }

    def dockerOrWithout(Agent agent, Closure body) {
        if (agent.docker != null) {
            return {
                docker.image(agent.docker).inside {
                    body.call()
                }
            }
        } else {
            return {
                body.call()
            }
        }
    }

    def nodeWithLabelOrWithout(Agent agent, Closure body) {
        if (agent?.label != null) {
            return {
                node(agent.label) {
                    body.call()
                }
            }
        } else {
            return {
                node {
                    body.call()
                }
            }
        }
    }
}