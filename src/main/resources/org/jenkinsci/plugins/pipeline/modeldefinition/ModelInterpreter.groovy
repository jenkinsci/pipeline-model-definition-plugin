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
package org.jenkinsci.plugins.pipeline.modeldefinition

import com.cloudbees.groovy.cps.impl.CpsClosure
import hudson.FilePath
import hudson.Launcher
import hudson.model.Result
import org.jenkinsci.plugins.pipeline.modeldefinition.model.Stage
import org.jenkinsci.plugins.pipeline.modeldefinition.model.Agent
import org.jenkinsci.plugins.pipeline.modeldefinition.model.Root
import org.jenkinsci.plugins.pipeline.modeldefinition.model.Tools
import org.jenkinsci.plugins.workflow.cps.CpsScript
import org.jenkinsci.plugins.workflow.steps.MissingContextVariableException

/**
 * CPS-transformed code for actually performing the build.
 *
 * @author Andrew Bayer
 */
public class ModelInterpreter implements Serializable {
    private CpsScript script

    public ModelInterpreter(CpsScript script) {
        this.script = script
    }

    def call(CpsClosure closure) {
        ClosureModelTranslator m = new ClosureModelTranslator(Root.class)

        closure.delegate = m
        closure.resolveStrategy = Closure.DELEGATE_ONLY
        closure.call()

        Root root = m.toNestedModel()
        Throwable firstError

        if (root != null) {
            // Entire build, including notifications, runs in the withEnv.
            script.withEnv(root.getEnvVars()) {
                // Stage execution and post-build actions run in separate catchErrors, so we still run post-build actions
                // even if the build fails, and we still send notifications if the build and/or post-build actions fail.
                nodeOrDockerOrNone(root.agent) {
                    toolsBlock(root.agent, root.tools) {
                        try {
                            catchRequiredContextForNode(root.agent) {
                                // If we have an agent and script.scm isn't null, run checkout scm
                                if (root.agent.hasAgent() && Utils.hasScmContext(script)) {
                                    script.checkout script.scm
                                }

                                for (int i = 0; i < root.stages.getStages().size(); i++) {
                                    Stage thisStage = root.stages.getStages().get(i)

                                    script.stage(thisStage.name) {
                                        Closure closureToCall = thisStage.closureWrapper.closure
                                        closureToCall.delegate = script
                                        closureToCall.resolveStrategy = Closure.DELEGATE_FIRST
                                        closureToCall.call()
                                    }
                                }
                            }.call()
                        } catch (Exception e) {
                            script.echo "Error in stages execution: ${e.getMessage()}"
                            script.getProperty("currentBuild").result = Result.FAILURE
                            if (firstError == null) {
                                firstError = e
                            }
                        }

                        try {
                            // Now run the post-build actions wrapped in catchError.
                            catchRequiredContextForNode(root.agent) {
                                List<Closure> postBuildClosures = root.satisfiedPostBuilds(script.getProperty("currentBuild"))
                                if (postBuildClosures.size() > 0) {
                                    script.stage("Post Build Actions") {
                                        for (int i = 0; i < postBuildClosures.size(); i++) {
                                            Closure c = postBuildClosures.get(i)
                                            c.delegate = script
                                            c.resolveStrategy = Closure.DELEGATE_FIRST
                                            c.call()
                                        }
                                    }
                                }
                            }.call()
                        } catch (Exception e) {
                            script.echo "Error in postBuild execution: ${e.getMessage()}"
                            script.getProperty("currentBuild").result = Result.FAILURE
                            if (firstError == null) {
                                firstError = e
                            }
                        }
                    }.call()
                }.call()

                try {
                    // And finally, run the notifications.
                    List<Closure> notificationClosures = root.satisfiedNotifications(script.getProperty("currentBuild"))

                    catchRequiredContextForNode(root.agent, true) {
                        if (notificationClosures.size() > 0) {
                            script.stage("Notifications") {
                                for (int i = 0; i < notificationClosures.size(); i++) {
                                    Closure c = notificationClosures.get(i)
                                    c.delegate = script
                                    c.resolveStrategy = Closure.DELEGATE_FIRST
                                    c.call()
                                }
                            }
                        }
                    }.call()
                } catch (Exception e) {
                    script.echo "Error in notifications execution: ${e.getMessage()}"
                    script.getProperty("currentBuild").result = Result.FAILURE
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
                        script.error("Attempted to execute a notification step that requires a node context. Notifications do not run inside a 'node { ... }' block.")
                    } else if (!agent.hasAgent()) {
                        script.error("Attempted to execute a step that requires a node context while 'agent none' was specified. " +
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

                String toolPath = script.tool(name:v, type:Tools.typeForKey(k))

                toolEnv.addAll(script.envVarsForTool(toolId: Tools.typeForKey(k), toolVersion: v))
            }

            return {
                script.withEnv(toolEnv) {
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
                script.getProperty("docker").image(agent.docker).inside {
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
                script.node(agent.label) {
                    body.call()
                }
            }
        } else {
            return {
                script.node {
                    body.call()
                }
            }
        }
    }
}