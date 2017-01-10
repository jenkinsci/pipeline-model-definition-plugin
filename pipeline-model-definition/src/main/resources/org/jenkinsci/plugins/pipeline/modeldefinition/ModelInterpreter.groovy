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
import org.jenkinsci.plugins.pipeline.modeldefinition.options.impl.SkipDefaultCheckout
import org.jenkinsci.plugins.pipeline.modeldefinition.steps.CredentialWrapper
import org.jenkinsci.plugins.pipeline.modeldefinition.when.DeclarativeStageConditional
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
            boolean postBuildRun = false

            try {
                executeProperties(root)

                // Entire build, including notifications, runs in the withEnv.
                withEnvBlock(root.getEnvVars()) {
                    inWrappers(root.options) {
                        // Stage execution and post-build actions run in try/catch blocks, so we still run post-build actions
                        // even if the build fails.
                        // We save the caught error, if any, for throwing at the end of the build.
                        inDeclarativeAgent(root.agent) {
                            withCredentialsBlock(root.getEnvCredentials()) {
                                toolsBlock(root.agent, root.tools) {
                                    // If we have an agent and script.scm isn't null, run checkout scm
                                    if (root.agent.hasAgent()
                                        && Utils.hasScmContext(script)
                                        && !((SkipDefaultCheckout)root.options?.options?.get("skipDefaultCheckout"))?.isSkipDefaultCheckout()) {
                                        script.stage(SyntheticStageNames.checkout()) {
                                            Utils.markSyntheticStage(SyntheticStageNames.checkout(), Utils.getSyntheticStageMetadata().pre)
                                            script.checkout script.scm
                                        }
                                    }

                                    for (int i = 0; i < root.stages.getStages().size(); i++) {
                                        Stage thisStage = root.stages.getStages().get(i)
                                        try {
                                            script.stage(thisStage.name) {
                                                if (firstError == null) {
                                                    withEnvBlock(thisStage.getEnvVars()) {
                                                        if (evaluateWhen(thisStage.when)) {
                                                            inDeclarativeAgent(thisStage.agent) {
                                                                withCredentialsBlock(thisStage.getEnvCredentials()) {
                                                                    toolsBlock(thisStage.agent ?: root.agent, thisStage.tools) {
                                                                        // Execute the actual stage and potential post-stage actions
                                                                        executeSingleStage(root, thisStage)
                                                                    }
                                                                }
                                                            }
                                                        } else {
                                                            script.echo("Stage skipped due to when conditional")
                                                            Utils.markStageSkippedForConditional(thisStage.name)
                                                        }
                                                    }
                                                } else {
                                                    script.echo("Stage skipped due to earlier failure(s)")
                                                    Utils.markStageSkippedForFailure(thisStage.name)
                                                }
                                            }
                                        } catch (Exception e) {
                                            script.getProperty("currentBuild").result = Result.FAILURE
                                            Utils.markStageFailedAndContinued(thisStage.name)
                                            if (firstError == null) {
                                                firstError = e
                                            }
                                        }
                                    }

                                    // Execute post-build actions now that we've finished all stages.
                                    try {
                                        postBuildRun = true
                                        executePostBuild(root)
                                    } catch (Exception e) {
                                        if (firstError == null) {
                                            firstError = e
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } finally {
                // If we hit an exception somewhere *before* we got to stages, we still need to do post-build tasks.
                if (!postBuildRun) {
                    executePostBuild(root)
                }
            }
            if (firstError != null) {
                throw firstError
            }
        }
    }

    /**
     * Actually execute a closure for a stage, conditional or post action.
     *
     * @param c The closure to execute
     */
    def delegateAndExecute(Closure c) {
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
                        script.error(Messages.ModelInterpreter_NoNodeContext())
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
            if (!Utils.withinAStage()) {
                script.stage(SyntheticStageNames.toolInstall()) {
                    Utils.markSyntheticStage(SyntheticStageNames.toolInstall(), Utils.getSyntheticStageMetadata().pre)
                    toolEnv = actualToolsInstall(toolsList)
                }
            } else {
                toolEnv = actualToolsInstall(toolsList)
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

    def actualToolsInstall(List<List<Object>> toolsList) {
        def toolEnv = []
        for (int i = 0; i < toolsList.size(); i++) {
            def entry = toolsList.get(i)
            String k = entry.get(0)
            String v = entry.get(1)

            String toolPath = script.tool(name: v, type: Tools.typeForKey(k))

            toolEnv.addAll(script.envVarsForTool(toolId: Tools.typeForKey(k), toolVersion: v))
        }

        return toolEnv
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
     * @param options The options configuration we're executing in
     * @param body The closure to execute
     * @return The return of the resulting executed closure
     */
    def inWrappers(Options options, Closure body) {
        if (options?.wrappers != null) {
            return {
                recursiveWrappers(options.wrappers.keySet().toList(), options.wrappers, body)
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
    def recursiveWrappers(List<String> wrapperNames, Map<String,Object> wrappers, Closure body) {
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
     * Executes a single stage and post-stage actions, and returns any error it may have generated.
     *
     * @param root The root context we're running in
     * @param thisStage The stage context we're running in
     */
    def executeSingleStage(Root root, Stage thisStage) throws Throwable {
        Throwable stageError = null
        try {
            catchRequiredContextForNode(thisStage.agent ?: root.agent) {
                delegateAndExecute(thisStage.steps.closure)
            }
        } catch (Exception e) {
            script.getProperty("currentBuild").result = Result.FAILURE
            Utils.markStageFailedAndContinued(thisStage.name)
            if (stageError == null) {
                stageError = e
            }
        } finally {
            // And finally, run the post stage steps.
            if (root.hasSatisfiedConditions(thisStage.post, script.getProperty("currentBuild"))) {
                script.echo("Post stage")
                stageError = runPostConditions(thisStage.post, thisStage.agent ?: root.agent, stageError, thisStage.name)
            }
        }

        if (stageError != null) {
            throw stageError
        }
    }

    /**
     *
     */
    def evaluateWhen(StageConditionals when) {
        if (when == null) {
            return true
        } else {
            for (int i = 0; i < when.conditions.size(); i++) {
                DeclarativeStageConditional c = when.conditions.get(i)
                if (!c.getScript(script).evaluate()) {
                    return false
                }
            }
            return true
        }
    }

    /**
     * Executes the post build actions for this build
     * @param root The root context we're executing in
     */
    def executePostBuild(Root root) throws Throwable {
        Throwable stageError = null
        if (root.hasSatisfiedConditions(root.post, script.getProperty("currentBuild"))) {
            script.stage(SyntheticStageNames.postBuild()) {
                Utils.markSyntheticStage(SyntheticStageNames.postBuild(), Utils.getSyntheticStageMetadata().post)
                stageError = runPostConditions(root.post, root.agent, stageError)
            }
        }

        if (stageError != null) {
            throw stageError
        }
    }

    /**
     * Actually does the execution of post actions, both post-stage and post-build.
     * @param responder The {@link AbstractBuildConditionResponder} we're pulling conditions from.
     * @param agentContext The {@link Agent} context we're running in.
     * @param stageError Any existing error from earlier parts of the stage we're in, or null.
     * @param stageName Optional - the name of the stage we're running in, so we can mark it as failed if needed.
     * @return The stageError, which, if null when passed in and an error is hit, will be set to the first error encountered.
     */
    def runPostConditions(AbstractBuildConditionResponder responder,
                          Agent agentContext,
                          Throwable stageError,
                          String stageName = null) {
        List<String> orderedConditions = BuildCondition.orderedConditionNames
        for (int i = 0; i < orderedConditions.size(); i++) {
            try {
                String conditionName = orderedConditions.get(i)

                Closure c = responder.closureForSatisfiedCondition(conditionName, script.getProperty("currentBuild"))
                if (c != null) {
                    catchRequiredContextForNode(agentContext) {
                        delegateAndExecute(c)
                    }
                }
            } catch (Exception e) {
                script.getProperty("currentBuild").result = Result.FAILURE
                if (stageName != null) {
                    Utils.markStageFailedAndContinued(stageName)
                }
                if (stageError == null) {
                    stageError = e
                }
            }
        }

        return stageError
    }

    /**
     * Sets any appropriate job properties for this build.
     *
     * @param root The root context we're running in
     */
    def executeProperties(Root root) {
        def jobProps = []

        if (root.options != null) {
            jobProps.addAll(root.options.properties)
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
