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
import org.jenkinsci.plugins.pipeline.modeldefinition.when.DeclarativeStageConditional
import org.jenkinsci.plugins.workflow.cps.CpsScript
import org.jenkinsci.plugins.workflow.steps.MissingContextVariableException
import org.jenkinsci.plugins.workflow.support.steps.build.RunWrapper

import javax.annotation.CheckForNull
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
        Root root = (Root) closure.call()
        Throwable firstError

        if (root != null) {
            boolean postBuildRun = false

            Utils.markExecutedStagesOnAction(script, root.astUUID)

            try {
                loadLibraries(root)

                executeProperties(root)

                // Entire build, including notifications, runs in the agent.
                inDeclarativeAgent(root, root, root.agent) {
                    withCredentialsBlock(root.environment) {
                        withEnvBlock(root.getEnvVars(script)) {
                            inWrappers(root.options) {
                                toolsBlock(root.agent, root.tools) {
                                    root.stages.stages.each { thisStage ->
                                        try {
                                            evaluateStage(root, thisStage.agent ?: root.agent, thisStage, firstError).call()
                                        } catch (Exception e) {
                                            script.getProperty("currentBuild").result = Utils.getResultFromException(e)
                                            Utils.markStageFailedAndContinued(thisStage.name)
                                            if (firstError == null) {
                                                firstError = e
                                            }
                                        }
                                    }

                                    // Execute post-build actions now that we've finished all parallel.
                                    try {
                                        postBuildRun = true
                                        executePostBuild(root)
                                    } catch (Exception e) {
                                        if (firstError == null) {
                                            firstError = e
                                        }
                                    }
                                }
                                // Throw any error we might have here to make sure that it gets caught and handled by
                                // wrappers.
                                if (firstError != null) {
                                    throw firstError
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                // Catch any errors that may have been thrown outside of the parallel proper and make sure we set
                // firstError accordingly.
                if (firstError == null) {
                    firstError = e
                }
            } finally {
                // If we hit an exception somewhere *before* we got to parallel, we still need to do post-build tasks.
                if (!postBuildRun) {
                    try {
                        executePostBuild(root)
                    } catch (Exception e) {
                        if (firstError == null) {
                            firstError = e
                        }
                    }
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

    def getParallelStages(Root root, Agent parentAgent, Stage thisStage, Throwable firstError, Stage parentStage,
                          boolean skippedForFailure, boolean skippedForUnstable, boolean skippedForWhen) {
        def parallelStages = [:]
        for (int i = 0; i < thisStage.parallel.stages.size(); i++) {
            Stage parallelStage = thisStage.parallel.getStages().get(i)
            if (skippedForFailure) {
                parallelStages.put(parallelStage.name, {
                    script.stage(parallelStage.name) {
                        Utils.logToTaskListener("Stage '${parallelStage.name}' skipped due to earlier failure(s)")
                        Utils.markStageSkippedForFailure(parallelStage.name)
                    }
                })
            } else if (skippedForUnstable) {
                parallelStages.put(parallelStage.name, {
                    script.stage(parallelStage.name) {
                        Utils.logToTaskListener("Stage '${parallelStage.name}' skipped due to earlier stage(s) marking the build as unstable")
                        Utils.markStageSkippedForUnstable(parallelStage.name)
                    }
                })
            } else if (skippedForWhen) {
                parallelStages.put(parallelStage.name, {
                    script.stage(parallelStage.name) {
                        Utils.logToTaskListener("Stage '${parallelStage.name}' skipped due to when conditional")
                        Utils.markStageSkippedForConditional(parallelStage.name)
                    }
                })
            } else {
                parallelStages.put(parallelStage.name,
                    evaluateStage(root, thisStage.agent ?: parentAgent, parallelStage, firstError, thisStage))
            }
        }
        if (!parallelStages.isEmpty() && thisStage.failFast) {
            parallelStages.put("failFast", thisStage.failFast)
        }

        return parallelStages

    }

    def evaluateStage(Root root, Agent parentAgent, Stage thisStage, Throwable firstError, Stage parentStage = null) {
        return {
            script.stage(thisStage.name) {
                try {
                    if (firstError != null) {
                        Utils.logToTaskListener("Stage '${thisStage.name}' skipped due to earlier failure(s)")
                        Utils.markStageSkippedForFailure(thisStage.name)
                        if (thisStage.parallel != null) {
                            script.parallel(getParallelStages(root, parentAgent, thisStage, firstError, parentStage, true, false, false))
                        }
                    } else if (skipUnstable(root.options)) {
                        Utils.logToTaskListener("Stage '${thisStage.name}' skipped due to earlier stage(s) marking the build as unstable")
                        Utils.markStageSkippedForUnstable(thisStage.name)
                        if (thisStage.parallel != null) {
                            script.parallel(getParallelStages(root, parentAgent, thisStage, firstError, parentStage, false, true, false))
                        }
                    } else {
                        if (thisStage.parallel != null) {
                            if (evaluateWhen(thisStage.when)) {
                                withCredentialsBlock(thisStage.environment) {
                                    withEnvBlock(thisStage.getEnvVars(script)) {
                                        script.parallel(getParallelStages(root, parentAgent, thisStage, firstError, parentStage, false, false, false))
                                    }
                                }
                            } else {
                                Utils.logToTaskListener("Stage '${thisStage.name}' skipped due to when conditional")
                                Utils.markStageSkippedForConditional(thisStage.name)
                                script.parallel(getParallelStages(root, parentAgent, thisStage, firstError, parentStage, false, false, true))
                            }
                        } else {
                            inDeclarativeAgent(thisStage, root, thisStage.agent) {
                                if (evaluateWhen(thisStage.when)) {
                                    withCredentialsBlock(thisStage.environment) {
                                        withEnvBlock(thisStage.getEnvVars(script)) {
                                            toolsBlock(thisStage.agent ?: root.agent, thisStage.tools, root) {
                                                // Execute the actual stage and potential post-stage actions
                                                executeSingleStage(root, thisStage, parentAgent)
                                            }
                                        }
                                    }
                                } else {
                                    Utils.logToTaskListener("Stage '${thisStage.name}' skipped due to when conditional")
                                    Utils.markStageSkippedForConditional(thisStage.name)
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    script.getProperty("currentBuild").result = Result.FAILURE
                    Utils.markStageFailedAndContinued(thisStage.name)
                    if (firstError == null) {
                        firstError = e
                    }
                } finally {
                    // And finally, run the post stage steps if this was a parallel parent.
                    if (thisStage.parallel != null && root.hasSatisfiedConditions(thisStage.post, script.getProperty("currentBuild"))) {
                        Utils.logToTaskListener("Post stage")
                        firstError = runPostConditions(thisStage.post, thisStage.agent ?: parentAgent, firstError, thisStage.name)
                    }
                }

                if (firstError != null) {
                    throw firstError
                }
            }
        }
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

    boolean skipUnstable(Options options) {
        return script.getProperty("currentBuild").result == "UNSTABLE" &&
            options?.options?.get("skipStagesAfterUnstable") != null
    }

    /**
     * Execute a body closure within a "withEnv" block.
     *
     * @param envVars A map of env vars to closures.
     * @param body The closure to execute
     * @return The return of the resulting executed closure
     */
    def withEnvBlock(Map<String,Closure> envVars, Closure body) {
        if (envVars != null && !envVars.isEmpty()) {
            List<String> evaledEnv = envVars.collect { k, v ->
                "${k}=${v.call()}"
            }
            return {
                script.withEnv(evaledEnv) {
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
     * @param environment The environment we're processing from
     * @param body The closure to execute
     * @return The return of the resulting executed closure
     */
    def withCredentialsBlock(@CheckForNull Environment environment, Closure body) {
        Map<String,CredentialWrapper> creds = new HashMap<>()
        
        if (environment != null) {
            try {
                RunWrapper currentBuild = script.getProperty("currentBuild")
                Utils.getCredsFromResolver(environment, script).each { k, v ->
                    String id = (String) v.call()
                    CredentialsBindingHandler handler = CredentialsBindingHandler.forId(id, currentBuild.rawBuild);
                    creds.put(k, new CredentialWrapper(id, handler.getWithCredentialsParameters(id)))
                }
            } catch (MissingMethodException e) {
                // This will only happen in a running upgrade situation, so check the legacy approach as well.
                creds.putAll(Utils.getLegacyEnvCredentials(environment))
            }
        }

        if (!creds.isEmpty()) {
            List<Map<String, Object>> parameters = createWithCredentialsParameters(creds)
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
     * Takes a map of keys to {@link CredentialWrapper}s and generates the proper output for the "withCredentials" block argument.
     * @param credentials A map of keys to {@link CredentialWrapper}s
     * @return A list of string->object maps suitable for passing to "withCredentials"
     */
    @NonCPS
    private List<Map<String, Object>> createWithCredentialsParameters(
            @Nonnull Map<String, CredentialWrapper> credentials) {
        List<Map<String, Object>> parameters = []
        credentials.each { k, v ->
            v.addParameters(k, parameters)
        }
        parameters
    }

    /**
     * Executes a given closure in a "withEnv" block after installing the specified tools
     * @param agent The agent context we're running in
     * @param tools The tools configuration we're using
     * @param root The root level configuration, if we're called within a stage. Can be null.
     * @param body The closure to execute
     * @return The return of the resulting executed closure
     */
    def toolsBlock(Agent agent, Tools tools, Root root = null, Closure body) {
        def toolsMap = new TreeMap<String,Closure>()
        if (tools != null) {
            toolsMap = tools.mergeToolEntries(root?.tools)
        } else if (root?.tools != null) {
            toolsMap = root.tools.mergeToolEntries(null)
        }
        // If there's no agent, don't install tools in the first place.
        if (agent.hasAgent() && !toolsMap.isEmpty()) {
            def toolEnv = []
            if (!Utils.withinAStage()) {
                script.stage(SyntheticStageNames.toolInstall()) {
                    toolEnv = actualToolsInstall(toolsMap)
                }
            } else {
                toolEnv = actualToolsInstall(toolsMap)
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

    def actualToolsInstall(Map<String,Closure> toolsMap) {
        def toolEnv = []

        toolsMap.each { k, v ->
            String toolVer = v.call()

            String toolPath = script.tool(name: toolVer, type: Tools.typeForKey(k))

            toolEnv.addAll(script.envVarsForTool(toolId: Tools.typeForKey(k), toolVersion: toolVer))
        }

        return toolEnv
    }

    /**
     * Executes the given closure inside a declarative agent block, if appropriate.
     *
     * @param context Either a stage or root object, the context we're running in.
     * @param root The root object for this pipeline
     * @param agent The agent context we're running in
     * @param body The closure to execute
     * @return The return of the resulting executed closure
     */
    def inDeclarativeAgent(Object context, Root root, Agent agent, Closure body) {
        if (agent == null) {
            return {
                body.call()
            }.call()
        } else {
            return agent.getDeclarativeAgent(root, context).getScript(script).run {
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
     * @param parentAgent the possible parent agent we should be running in
     */
    def executeSingleStage(Root root, Stage thisStage, Agent parentAgent) throws Throwable {
        Throwable stageError = null
        try {
            catchRequiredContextForNode(thisStage.agent ?: parentAgent) {
                delegateAndExecute(thisStage.steps.closure)
            }
        } catch (Exception e) {
            script.getProperty("currentBuild").result = Utils.getResultFromException(e)
            Utils.markStageFailedAndContinued(thisStage.name)
            if (stageError == null) {
                stageError = e
            }
        } finally {
            // And finally, run the post stage steps.
            if (root.hasSatisfiedConditions(thisStage.post, script.getProperty("currentBuild"))) {
                Utils.logToTaskListener("Post stage")
                stageError = runPostConditions(thisStage.post, thisStage.agent ?: parentAgent, stageError, thisStage.name)
            }
        }

        if (stageError != null) {
            throw stageError
        }
    }

    /**
     *
     */
    def evaluateWhen(StageConditionals when, boolean skipDueToParent = false) {
        if (skipDueToParent) {
            return false
        } else if (when == null) {
            return true
        } else {
            // To allow for referencing environment variables that have not yet been declared pre-parse time, we need
            // to actually instantiate the conditional now, via a closure.
            return instancesFromClosure(when.rawClosure, DeclarativeStageConditional.class).every {
                it.getScript(script).evaluate()
            }
        }
    }

    /**
     * Takes a closure that evaluates into a list of instances of a given class, sets that closure to delegate to our
     * CpsScript, calls it, and returns a list of the instances of that class.
     *
     * @param rawClosure
     * @param instanceType
     * @return A list of instances
     */
    private <Z> List<Z> instancesFromClosure(Closure rawClosure, Class<Z> instanceType) {
        rawClosure.delegate = script
        rawClosure.resolveStrategy = Closure.DELEGATE_FIRST

        List<Z> instanceList = []

        rawClosure.call().each { inst ->
            if (instanceType.isInstance(inst)) {
                instanceList.add(instanceType.cast(inst))
            }
        }

        return instanceList
    }
    /**
     * Executes the post build actions for this build
     * @param root The root context we're executing in
     */
    def executePostBuild(Root root) throws Throwable {
        Throwable stageError = null
        if (root.hasSatisfiedConditions(root.post, script.getProperty("currentBuild"))) {
            script.stage(SyntheticStageNames.postBuild()) {
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
        BuildCondition.orderedConditionNames.each { conditionName ->
            try {
                Closure c = responder.closureForSatisfiedCondition(conditionName, script.getProperty("currentBuild"))
                if (c != null) {
                    catchRequiredContextForNode(agentContext) {
                        delegateAndExecute(c)
                    }
                }
            } catch (Exception e) {
                script.getProperty("currentBuild").result = Utils.getResultFromException(e)
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
     * Load specified libraries.
     *
     * @param root The root context we're running in
     */
    def loadLibraries(Root root) {
        if (root.libraries != null) {
            root.libraries.libs.each { lib ->
                script.library(lib)
            }
        }
    }

    /**
     * Sets any appropriate job properties for this build.
     *
     * @param root The root context we're running in
     */
    def executeProperties(Root root) {
        Utils.updateJobProperties(root.options?.properties, root.triggers?.triggers, root.parameters?.parameters, script)
    }
}
