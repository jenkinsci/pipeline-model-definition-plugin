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

import com.cloudbees.groovy.cps.NonCPS
import com.cloudbees.groovy.cps.impl.CpsClosure
import hudson.FilePath
import hudson.Launcher
import hudson.model.Result
import org.jenkinsci.plugins.pipeline.modeldefinition.model.*
import org.jenkinsci.plugins.pipeline.modeldefinition.steps.CredentialWrapper
import org.jenkinsci.plugins.workflow.cps.CpsScript
import org.jenkinsci.plugins.workflow.steps.MissingContextVariableException

import javax.annotation.Nonnull

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
        // Attach the stages model to the run for introspection etc.
        Utils.attachExecutionModel(script)

        ClosureModelTranslator m = new ClosureModelTranslator(Root.class, script)

        closure.delegate = m
        closure.resolveStrategy = Closure.DELEGATE_FIRST
        closure.call()

        Root root = m.toNestedModel()
        Throwable firstError

        if (root != null) {
            def jobProps = []

            if (root.jobProperties != null) {
                jobProps.addAll(root.jobProperties.properties)
            }
            if (root.triggers != null) {
                jobProps.add(script.pipelineTriggers(root.triggers.triggers))
            }
            if (root.parameters != null) {
                jobProps.add(script.parameters(root.parameters.parameters))
            }
            if (!jobProps.isEmpty()) {
                script.properties(jobProps)
            }

            // Entire build, including notifications, runs in the withEnv.
            withEnvBlock(root.getEnvVars()) {
                inWrappers(root.wrappers) {
                    // Stage execution and post-build actions run in try/catch blocks, so we still run post-build actions
                    // even if the build fails, and we still send notifications if the build and/or post-build actions fail.
                    // We save the caught error, if any, for throwing at the end of the build.
                    inDeclarativeAgent(root.agent) {
                        withCredentialsBlock(root.getEnvCredentials()) {
                            toolsBlock(root.agent, root.tools) {
                                // If we have an agent and script.scm isn't null, run checkout scm
                                if (root.agent.hasAgent() && Utils.hasScmContext(script)) {
                                    script.checkout script.scm
                                }

                                for (int i = 0; i < root.stages.getStages().size(); i++) {
                                    Stage thisStage = root.stages.getStages().get(i)

                                runStageOrNot(thisStage, firstError) {
                                    script.stage(thisStage.name) {
                                        withEnvBlock(thisStage.getEnvVars()) {
                                            if (firstError == null) {
                                                inDeclarativeAgent(thisStage.agent) {
                                                    withCredentialsBlock(thisStage.getEnvCredentials()) {
                                                        toolsBlock(thisStage.agent ?: root.agent, thisStage.tools) {
                                                            try {
                                                                catchRequiredContextForNode(root.agent) {
                                                                    setUpDelegate(thisStage.steps.closure).call()
                                                                }.call()
                                                            } catch (Exception e) {
                                                                script.echo "Error in stages execution: ${e.getMessage()}"
                                                                script.getProperty("currentBuild").result = Result.FAILURE
                                                                if (firstError == null) {
                                                                    firstError = e
                                                                }
                                                            } finally {
                                                                // And finally, run the post stage steps.
                                                                List<Closure> postClosures = thisStage.satisfiedPostStageConditions(root, script.getProperty("currentBuild"))
                                                                catchRequiredContextForNode(thisStage.agent != null ? thisStage.agent : root.agent, false) {
                                                                    if (postClosures.size() > 0) {
                                                                        script.echo("Post stage")
                                                                        //TODO should this be a nested stage instead?
                                                                        try {
                                                                            for (int ni = 0; ni < postClosures.size(); ni++) {
                                                                                setUpDelegate(postClosures.get(ni)).call()
                                                                            }
                                                                        } catch (Exception e) {
                                                                            script.echo "Error in stage post: ${e.getMessage()}"
                                                                            script.getProperty("currentBuild").result = Result.FAILURE
                                                                            if (firstError == null) {
                                                                                firstError = e
                                                                            }
                                                                        }
                                                                    }
                                                                }.call()
                                                            }
                                                        }.call()
                                                    }.call()
                                                }.call()
                                            }
                                        }.call()
                                    }
                                }.call()
                            }

                            try {
                                catchRequiredContextForNode(root.agent) {
                                    List<Closure> postBuildClosures = root.satisfiedPostBuilds(script.getProperty("currentBuild"))
                                    if (postBuildClosures.size() > 0) {
                                        script.stage("Post Build Actions") {
                                            for (int i = 0; i < postBuildClosures.size(); i++) {
                                                setUpDelegate(postBuildClosures.get(i)).call()
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
                                        setUpDelegate(notificationClosures.get(i)).call()
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
                }.call()

            }.call()

            if (firstError != null) {
                throw firstError
            }
        }
    }

    Closure setUpDelegate(Closure c) {
        c.delegate = script
        c.resolveStrategy = Closure.DELEGATE_FIRST
        return c
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

    def withEnvBlock(List<String> envVars, Closure body) {
        if (envVars != null && !envVars.isEmpty()) {
            return {
                script.withEnv(envVars) {
                    body.call()
                }
            }
        } else {
            return {
                body.call()
            }
        }
    }

    def withCredentialsBlock(@Nonnull Map<String, CredentialWrapper> credentials, Closure body) {
        if (!credentials.isEmpty()) {
            List<Map<String, Object>> parameters = createWithCredentialsParameters(credentials)
            return {
                script.withCredentials(parameters) {
                    body.call()
                }
            }
        } else {
            return {
                body.call()
            }
        }
    }

    @NonCPS
    private List<Map<String, Object>> createWithCredentialsParameters(
            @Nonnull Map<String, CredentialWrapper> credentials) {
        List<Map<String, Object>> parameters = []
        Set<Map.Entry<String, CredentialWrapper>> set = credentials.entrySet()
        for (Map.Entry<String, CredentialWrapper> entry : set) {
            entry.value.addParameters(entry.key, parameters)
        }
        parameters
    }

    def toolsBlock(Agent agent, Tools tools, Closure body) {
        // If there's no agent, don't install tools in the first place.
        if (agent.hasAgent() && tools != null) {
            def toolEnv = []
            def toolsList = tools.getToolEntries()
            for (int i = 0; i < toolsList.size(); i++) {
                def entry = toolsList.get(i)
                String k = entry.get(0)
                String v = entry.get(1)

                String toolPath = script.tool(name: v, type: Tools.typeForKey(k))

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

    def inDeclarativeAgent(Agent agent, Closure body) {
        if (agent == null) {
            return {
                body.call()
            }
        } else {
            return agent.getDeclarativeAgent().getScript(script).run {
                body.call()
            }
        }
    }

    def inWrappers(Wrappers wrappers, Closure body) {
        if (wrappers != null) {
            return {
                recursiveWrappers(wrappers.keySet().toList(), wrappers, body).call()
            }
        } else {
            return {
                body.call()
            }
        }
    }

    def recursiveWrappers(List<String> wrapperNames, Wrappers wrappers, Closure body) {
        if (wrapperNames.isEmpty()) {
            return {
                body.call()
            }
        } else {
            def thisWrapper = wrapperNames.remove(0)

            def wrapperArgs = wrappers.get(thisWrapper)
            if (wrapperArgs != null) {
                return {
                    script."${thisWrapper}"(wrapperArgs) {
                        recursiveWrappers(wrapperNames, wrappers, body).call()
                    }
                }
            } else {
                return {
                    script."${thisWrapper}"() {
                        recursiveWrappers(wrapperNames, wrappers, body).call()
                    }
                }
            }
        }
    }

    def runStageOrNot(Stage stage, Throwable firstError, Closure body) {
        if (stage.when != null && firstError == null) {
            return {
                if (setUpDelegate(stage.when.closure).call()) {
                    body.call()
                }
            }
        } else {
            return {
                body.call()
            }
        }
    }
}
