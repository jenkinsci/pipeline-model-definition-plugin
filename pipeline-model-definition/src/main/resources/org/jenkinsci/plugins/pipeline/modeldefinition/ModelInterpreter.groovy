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
            executeProperties(root)

            // Entire build, including notifications, runs in the withEnv.
            withEnvBlock(root.getEnvVars()) {
                inWrappers(root.wrappers) {
                    // Stage execution and post-build actions run in try/catch blocks, so we still run post-build actions
                    // even if the build fails.
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

                                    try {
                                        runStageOrNot(thisStage) {
                                            script.stage(thisStage.name) {
                                                withEnvBlock(thisStage.getEnvVars()) {
                                                    if (firstError == null) {
                                                        inDeclarativeAgent(thisStage.agent) {
                                                            withCredentialsBlock(thisStage.getEnvCredentials()) {
                                                                toolsBlock(thisStage.agent ?: root.agent, thisStage.tools) {
                                                                    // Execute the actual stage and potential post-stage actions
                                                                    executeSingleStage(root, thisStage)
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    } catch (Exception e) {
                                        if (firstError == null) {
                                            firstError = e
                                        }
                                    }
                                }

                                // Execute post-build actions now that we've finished all stages.
                                try {
                                    executePostBuild(root)
                                } catch (Exception e) {
                                    if (firstError == null) {
                                        firstError = e
                                    }
                                }
                            }.call()
                        }.call()
                    }.call()
                }.call()
            }.call()
            if (firstError != null) {
                script.echo "An error was encountered in execution: ${firstError.getMessage()}"
                throw firstError
            }
        }
    }

    /**
     * Actually execute a closure for a stage, conditional or post action.
     *
     * @param c The closure to execute
     */
    def setUpDelegate(Closure c) {
        c.delegate = script
        c.resolveStrategy = Closure.DELEGATE_FIRST
        c.call()
    }

    /**
     * Execute the given body closure while watching for errors that will specifically show up when there's an attempt to
     * run a step that needs a node context but doesn't have one.
     *
     * @param agent The {@link Agent} that applies to this execution. Used to clarify error message.
     * @param inNotifications Whether we're currently in the notifications section, for error message clarification.
     * @param body The closure to call
     * @return The return of the resulting executed closure
     * @throws Exception
     */
    def catchRequiredContextForNode(Agent agent, Closure body) throws Exception {
        return {
            try {
                body.call()
            } catch (MissingContextVariableException e) {
                if (FilePath.class.equals(e.type) || Launcher.class.equals(e.type)) {
                    if (!agent.hasAgent()) {
                        script.error("Attempted to execute a step that requires a node context while 'agent none' was specified. " +
                                "Be sure to specify your own 'node { ... }' blocks when using 'agent none'.")
                    } else {
                        throw e
                    }
                } else {
                    throw e
                }
            }
        }.call()
    }

    /**
     * Execute a body closure within a "withEnv" block.
     *
     * @param envVars A list of "FOO=BAR" environment variables. Can be null.
     * @param body The closure to execute
     * @return The return of the resulting executed closure
     */
    def withEnvBlock(List<String> envVars, Closure body) {
        if (envVars != null && !envVars.isEmpty()) {
            return {
                script.withEnv(envVars) {
                    body.call()
                }
            }.call()
        } else {
            return {
                body.call()
            }.call()
        }
    }

    /**
     * Execute a given closure within a "withCredentials" block.
     *
     * @param credentials A map of strings to {@link CredentialWrapper}s
     * @param body The closure to execute
     * @return The return of the resulting executed closure
     */
    def withCredentialsBlock(@Nonnull Map<String, CredentialWrapper> credentials, Closure body) {
        if (!credentials.isEmpty()) {
            List<Map<String, Object>> parameters = createWithCredentialsParameters(credentials)
            return {
                script.withCredentials(parameters) {
                    body.call()
                }
            }.call()
        } else {
            return {
                body.call()
            }.call()
        }
    }

    /**
     * Takes a map of credential wrappers and generates the proper output for the "withCredentials" block argument.
     * @param credentials A map of strings to {@link CredentialWrapper}s
     * @return A list of string->object maps suitable for passing to "withCredentials"
     */
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

    /**
     * Executes a given closure in a "withEnv" block after installing the specified tools
     * @param agent The agent context we're running in
     * @param tools The tools configuration we're using
     * @param body The closure to execute
     * @return The return of the resulting executed closure
     */
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
            }.call()
        } else {
            return {
                body.call()
            }.call()
        }
    }

    /**
     * Executes the given closure inside a declarative agent block, if appropriate.
     *
     * @param agent The agent context we're running in
     * @param body The closure to execute
     * @return The return of the resulting executed closure
     */
    def inDeclarativeAgent(Agent agent, Closure body) {
        if (agent == null) {
            return {
                body.call()
            }.call()
        } else {
            return agent.getDeclarativeAgent().getScript(script).run {
                body.call()
            }.call()
        }
    }

    /**
     * Executes the given closure inside 0 or more wrapper blocks if appropriate
     * @param wrappers The wrapper configuration we're executing in
     * @param body The closure to execute
     * @return The return of the resulting executed closure
     */
    def inWrappers(Wrappers wrappers, Closure body) {
        if (wrappers != null) {
            return {
                recursiveWrappers(wrappers.keySet().toList(), wrappers, body)
            }.call()
        } else {
            return {
                body.call()
            }.call()
        }
    }

    /**
     * Generates and executes a single (or no) wrapper block, recursively calling itself on any remaining wrapper names.
     * @param wrapperNames A list of wrapper names remaining to run
     * @param wrappers The wrappers configuration we're executing in
     * @param body The closure to execute
     * @return The return of the resulting executed closure
     */
    def recursiveWrappers(List<String> wrapperNames, Wrappers wrappers, Closure body) {
        if (wrapperNames.isEmpty()) {
            return {
                body.call()
            }.call()
        } else {
            def thisWrapper = wrapperNames.remove(0)

            def wrapperArgs = wrappers.get(thisWrapper)
            if (wrapperArgs != null) {
                return {
                    script."${thisWrapper}"(wrapperArgs) {
                        recursiveWrappers(wrapperNames, wrappers, body)
                    }
                }.call()
            } else {
                return {
                    script."${thisWrapper}"() {
                        recursiveWrappers(wrapperNames, wrappers, body)
                    }
                }.call()
            }
        }
    }

    /**
     * Executes a given closure if there is no error and either there is no when condition on the stage or the when condition
     * passes.
     *
     * @param stage The stage we're executing
     * @param body The closure to execute
     * @return The return of the resulting executed closure
     */
    def runStageOrNot(Stage stage, Closure body) {
        if (stage.when == null || setUpDelegate(stage.when.closure)) {
            return {
                body.call()
            }.call()
        }
    }

    /**
     * Executes a single stage and post-stage actions, and returns any error it may have generated.
     *
     * @param root The root context we're running in
     * @param thisStage The stage context we're running in
     */
    def executeSingleStage(Root root, Stage thisStage) throws Throwable {
        Throwable stageError
        try {
            catchRequiredContextForNode(thisStage.agent ?: root.agent) {
                setUpDelegate(thisStage.steps.closure)
            }
        } catch (Exception e) {
            script.getProperty("currentBuild").result = Result.FAILURE
            if (stageError == null) {
                stageError = e
            }
        } finally {
            // And finally, run the post stage steps.
            List<Closure> postClosures = thisStage.satisfiedPostStageConditions(root, script.getProperty("currentBuild"))
            catchRequiredContextForNode(thisStage.agent ?: root.agent, false) {
                if (postClosures.size() > 0) {
                    script.echo("Post stage")
                    //TODO should this be a nested stage instead?
                    try {
                        for (int ni = 0; ni < postClosures.size(); ni++) {
                            setUpDelegate(postClosures.get(ni))
                        }
                    } catch (Exception e) {
                        script.getProperty("currentBuild").result = Result.FAILURE
                        if (stageError == null) {
                            stageError = e
                        }
                    }
                }
            }
        }

        if (stageError != null) {
            throw stageError
        }
    }

    /**
     * Executes the post build actions for this build
     * @param root The root context we're executing in
     */
    def executePostBuild(Root root) throws Throwable {
        Throwable stageError
        List<Closure> postBuildClosures = root.satisfiedPostBuilds(script.getProperty("currentBuild"))
        if (postBuildClosures.size() > 0) {
            try {
                script.stage("Post Build Actions") {
                    catchRequiredContextForNode(root.agent) {
                        for (int i = 0; i < postBuildClosures.size(); i++) {
                            setUpDelegate(postBuildClosures.get(i))
                        }
                    }
                }
            } catch (Exception e) {
                script.getProperty("currentBuild").result = Result.FAILURE
                stageError = e
            }
        }

        if (stageError != null) {
            throw stageError
        }
    }

    /**
     * Sets any appropriate job properties for this build.
     *
     * @param root The root context we're running in
     */
    def executeProperties(Root root) {
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
    }
}
