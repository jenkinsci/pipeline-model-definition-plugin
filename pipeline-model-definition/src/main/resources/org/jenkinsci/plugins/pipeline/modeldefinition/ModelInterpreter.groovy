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
import org.jenkinsci.plugins.pipeline.StageStatus
import org.jenkinsci.plugins.pipeline.modeldefinition.agent.AbstractDockerAgent
import org.jenkinsci.plugins.pipeline.modeldefinition.model.*
import org.jenkinsci.plugins.pipeline.modeldefinition.options.DeclarativeOption
import org.jenkinsci.plugins.pipeline.modeldefinition.steps.CredentialWrapper
import org.jenkinsci.plugins.pipeline.modeldefinition.when.DeclarativeStageConditional
import org.jenkinsci.plugins.workflow.cps.CpsScript
import org.jenkinsci.plugins.workflow.steps.MissingContextVariableException
import org.jenkinsci.plugins.workflow.support.steps.build.RunWrapper

import javax.annotation.CheckForNull
import javax.annotation.Nonnull

import static org.apache.commons.lang.exception.ExceptionUtils.getFullStackTrace

/**
 * CPS-transformed code for actually performing the build.
 *
 * @author Andrew Bayer
 */
class ModelInterpreter implements Serializable {
    private CpsScript script

    ModelInterpreter(CpsScript script) {
        this.script = script
    }

    def call(CpsClosure closure) {
        Root root = (Root) closure.call()
        Throwable firstError

        if (root != null) {
            boolean postBuildRun = false

            Utils.updateRunAndJobActions(script, root.astUUID)

            try {
                loadLibraries(root)

                executeProperties(root)

                String restartedStage = Utils.getRestartedStage(script)

                // Entire build, including notifications, runs in the agent.
                inDeclarativeAgent(root, root, root.agent) {
                    withCredentialsBlock(root.environment) {
                        withEnvBlock(root.getEnvVars(script)) {
                            inWrappers(root.options?.wrappers) {
                                toolsBlock(root.tools, root.agent, null) {
                                    firstError = evaluateSequentialStages(root, root.stages, firstError, null, restartedStage, null).call()

                                    // Execute post-build actions now that we've finished all parallel.
                                    try {
                                        postBuildRun = true
                                        executePostBuild(root)
                                    } catch (Throwable e) {
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
            } catch (Throwable e) {
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
                    } catch (Throwable e) {
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

    /**
     * Evaluate a list of sequential stages.
     *
     * @param root The root of the Declarative model
     * @param stages The list of stages
     * @param firstError An error that's already occurred earlier in the build. Can be null.
     * @param parent The parent stage for this list of stages. Can be null.
     * @param restartedStageName the name of the stage we're restarting at. Null if this is not a restarted build or this is
     *     called from a nested stage.
     * @param skippedReason Possibly null reason the container for the stages was skipped.
     * @return A closure to execute
     */
    def evaluateSequentialStages(Root root, Stages stages, Throwable firstError, Stage parent, String restartedStageName,
                                 SkippedStageReason skippedReason) {
        return {
            try {
                boolean skippedForRestart = restartedStageName != null
                stages.stages.each { thisStage ->
                    if (skippedForRestart) {
                        // Check if we're skipping for restart but are now on the stage we're supposed to restart on.
                        if (thisStage.name == restartedStageName) {
                            // If so, set skippedForRestart to false, and if the skippedReason is for restart, wipe that out too.
                            skippedForRestart = false
                            if (skippedReason instanceof SkippedStageReason.Restart) {
                                skippedReason = null
                            }
                        } else {
                            // If we skipped for restart and this isn't the restarted name, create a new reason.
                            skippedReason = new SkippedStageReason.Restart(thisStage.name, restartedStageName)
                        }
                    }
                    try {
                        evaluateStage(root, thisStage.agent ?: root.agent, thisStage, firstError, parent, skippedReason).call()
                    } catch (Throwable e) {
                        script.getProperty("currentBuild").result = Utils.getResultFromException(e)
                        Utils.markStageFailedAndContinued(thisStage.name)
                        if (firstError == null) {
                            firstError = e
                        }
                    }
                    if (skippedForRestart) {
                        Utils.markStartAndEndNodesInStageAsNotExecuted(thisStage.name)
                    }
                }
            } finally {
                // And finally, run the post stage steps if this was a parallel parent.
                if (skippedReason == null && parent != null &&
                    root.hasSatisfiedConditions(parent.post, script.getProperty("currentBuild"), parent, firstError)) {
                    Utils.logToTaskListener("Post stage")
                    firstError = runPostConditions(parent.post, parent.agent ?: root.agent, firstError, parent.name, parent)
                }
            }

            return firstError
        }
    }

    /**
     * Get the map to pass to the parallel step of nested stages to run in parallel for the given stage.
     *
     * @param root The root of the Declarative model
     * @param parentAgent The parent agent definition. Can be null.
     * @param thisStage The current stage we'll look in for parallel stages
     * @param firstError An error that's already occurred earlier in the build. Can be null.
     * @param skippedReason A possibly null reason this stage, its children, and therefore its grandchildren, will be skipped.
     * @return A map of parallel branch names to closures to pass to the parallel step
     */
    def getParallelStages(Root root, Agent parentAgent, Stage thisStage, Throwable firstError, SkippedStageReason skippedReason) {
        def parallelStages = [:]
        thisStage?.parallelContent?.each { content ->
            if (skippedReason != null) {
                parallelStages.put(content.name,
                    evaluateStage(root, parentAgent, content, firstError, thisStage, skippedReason.cloneWithNewStage(content.name)))
            } else {
                parallelStages.put(content.name,
                    evaluateStage(root, thisStage.agent ?: parentAgent, content, firstError, thisStage, null))
            }
        }
        if (!parallelStages.isEmpty() && thisStage.failFast) {
            parallelStages.put("failFast", thisStage.failFast)
        }

        return parallelStages

    }

    /**
     * Evaluate a stage, setting up agent, tools, env, etc, determining any nested stages to execute, skipping
     * if appropriate, etc, actually executing the stage via executeSingleStage, parallel, or evaluateSequentialStages.
     *
     * @param root The root of the Declarative model
     * @param parentAgent The parent agent definition, which can be null
     * @param thisStage The stage we're actually evaluating.
     * @param firstError An error that's already occurred earlier in the build. Can be null.
     * @param parent The possible parent stage, defaults to null.
     * @param skippedReason Possibly null reason this stage's parent, and therefore itself, is skipped.
     * @return
     */
    def evaluateStage(Root root, Agent parentAgent, Stage thisStage, Throwable firstError, Stage parent,
                      SkippedStageReason skippedReason) {
        return {
            def thisError = null

            script.stage(thisStage.name) {
                try {
                    if (skippedReason != null) {
                        skipStage(root, parentAgent, thisStage, firstError, skippedReason, parent).call()
                    } else if (firstError != null) {
                        skippedReason = new SkippedStageReason.Failure(thisStage.name)
                        skipStage(root, parentAgent, thisStage, firstError, skippedReason, parent).call()
                    } else if (skipUnstable(root.options)) {
                        skippedReason = new SkippedStageReason.Unstable(thisStage.name)
                        skipStage(root, parentAgent, thisStage, firstError, skippedReason, parent).call()
                    } else {
                        inWrappers(thisStage.options?.wrappers) {
                            if (thisStage?.parallelContent) {
                                stageInput(thisStage.input) {
                                    if (evaluateWhen(thisStage.when)) {
                                        withCredentialsBlock(thisStage.environment) {
                                            withEnvBlock(thisStage.getEnvVars(script)) {
                                                script.parallel(getParallelStages(root, parentAgent, thisStage, firstError, null))
                                            }
                                        }
                                    } else {
                                        skippedReason = new SkippedStageReason.When(thisStage.name)
                                        skipStage(root, parentAgent, thisStage, firstError, skippedReason, parent).call()
                                    }
                                }
                            } else {
                                stageInput(thisStage.input) {
                                    def stageBody = {
                                        withCredentialsBlock(thisStage.environment) {
                                            withEnvBlock(thisStage.getEnvVars(script)) {
                                                toolsBlock(thisStage.tools, thisStage.agent ?: root.agent, parent?.tools ?: root.tools) {
                                                    if (thisStage?.stages) {
                                                        def nestedError = evaluateSequentialStages(root, thisStage.stages, firstError, thisStage, null, null).call()

                                                        // Propagate any possible error from the sequential stages as if it were an error thrown directly.
                                                        if (nestedError != null) {
                                                            throw nestedError
                                                        }
                                                    } else {
                                                        // Execute the actual stage and potential post-stage actions
                                                        executeSingleStage(root, thisStage, parentAgent)
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    // If beforeAgent is true, evaluate the when before entering the agent.
                                    boolean whenPassed = false
                                    if (thisStage.when?.beforeAgent != null && thisStage.when?.beforeAgent) {
                                        whenPassed = evaluateWhen(thisStage.when)
                                        if (whenPassed) {
                                            inDeclarativeAgent(thisStage, root, thisStage.agent) {
                                                stageBody.call()
                                            }
                                        }
                                    } else {
                                        inDeclarativeAgent(thisStage, root, thisStage.agent) {
                                            whenPassed = evaluateWhen(thisStage.when)
                                            if (whenPassed) {
                                                stageBody.call()
                                            }
                                        }
                                    }

                                    if (!whenPassed) {
                                        skippedReason = new SkippedStageReason.When(thisStage.name)
                                        skipStage(root, parentAgent, thisStage, firstError, skippedReason, parent).call()
                                    }
                                }
                            }
                        }
                    }
                } catch (Throwable e) {
                    script.getProperty("currentBuild").result = Result.FAILURE
                    Utils.markStageFailedAndContinued(thisStage.name)
                    if (firstError == null) {
                        firstError = e
                    }
                    thisError = e
                } finally {
                    // And finally, run the post stage steps if this was a parallel parent.
                    if (skippedReason == null &&
                        root.hasSatisfiedConditions(thisStage.post, script.getProperty("currentBuild"), thisStage, firstError) &&
                        thisStage?.parallelContent) {
                        Utils.logToTaskListener("Post stage")
                        firstError = runPostConditions(thisStage.post, thisStage.agent ?: parentAgent, firstError, thisStage.name, thisStage)
                    }
                }

                if (firstError != null) {
                    throw firstError
                }
            }
        }
    }

    def stageInput(StageInput input, Closure body) {
        if (input != null) {
            return {
                def submitted = script.input(message: input.message, id: input.id, ok: input.ok, submitter: input.submitter,
                    submitterParameter: input.submitterParameter, parameters: input.parameters)
                if (input.parameters.isEmpty() && input.submitterParameter == null) {
                    // No parameters, so just proceed
                    body.call()
                } else {
                    def inputEnv = []
                    if (submitted instanceof Map) {
                        // Multiple parameters!
                        inputEnv = submitted.collect { k, v -> "${k}=${v}" }
                    } else if (input.submitterParameter != null) {
                        // Single parameter, it's the submitter.
                        inputEnv = ["${input.submitterParameter}=${submitted}"]
                    } else if (input.parameters.size() == 1) {
                        // One defined parameter, so we know its name.
                        inputEnv = ["${input.parameters.first().name}=${submitted}"]
                    }
                    script.withEnv(inputEnv) {
                        body.call()
                    }
                }
            }.call()
        } else {
            return {
                body.call()
            }.call()
        }
    }

    def skipStage(Root root, Agent parentAgent, Stage thisStage, Throwable firstError, SkippedStageReason reason,
                  Stage parentStage) {
        return {
            Utils.logToTaskListener(reason.message)
            Utils.markStageWithTag(thisStage.name, StageStatus.TAG_NAME, reason.stageStatus)
            if (thisStage?.parallelContent) {
                Map<String,Closure> parallelToSkip = getParallelStages(root, parentAgent, thisStage, firstError, reason)
                script.parallel(parallelToSkip)
                if (reason instanceof SkippedStageReason.Restart) {
                    parallelToSkip.keySet().each { k -> Utils.markStartAndEndNodesInStageAsNotExecuted(k) }
                }
            } else if (thisStage?.stages != null) {
                String restartedStage = null
                if (reason instanceof SkippedStageReason.Restart) {
                    restartedStage = reason.restartedStage
                }
                evaluateSequentialStages(root, thisStage.stages, firstError, thisStage, restartedStage, reason).call()
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
                if (FilePath.class == e.type || Launcher.class == e.type) {
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

    @Deprecated
    boolean skipUnstable(Options options) {
        return skipUnstable(options?.options)
    }

    boolean skipUnstable(Map<String,DeclarativeOption> options) {
        return script.getProperty("currentBuild").result == "UNSTABLE" &&
            options?.get("skipStagesAfterUnstable") != null
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
                RunWrapper currentBuild = (RunWrapper)script.getProperty("currentBuild")
                Utils.getCredsFromResolver(environment, script).each { k, v ->
                    String id = (String) v.call()
                    CredentialsBindingHandler handler = CredentialsBindingHandler.forId(id, currentBuild.rawBuild)
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
     * Legacy version to pass the root tools in, rather than directly passing in a tools. Only relevant for in-progress
     * runs.
     * TODO: Delete in 1.4? Or maybe just nuke now.
     */
    @Deprecated
    def toolsBlock(Agent agent, Tools tools, Root root = null, Closure body) {
        return toolsBlock(tools, agent, root?.tools, body)
    }

    /**
     * Executes a given closure in a "withEnv" block after installing the specified tools
     * @param tools The tools configuration we're using
     * @param agent The agent context we're running in
     * @param rootTools The parent level configuration, if we're called within a stage. Can be null.
     * @param body The closure to execute
     * @return The return of the resulting executed closure
     */
    def toolsBlock(Tools tools, Agent agent, Tools rootTools, Closure body) {
        def toolsList = []
        if (tools != null) {
            toolsList = tools.mergeToolEntries(rootTools)
        } else if (rootTools != null) {
            toolsList = rootTools.mergeToolEntries(null)
        }
        // If there's no agent, don't install tools in the first place.
        if (agent.hasAgent() && !toolsList.isEmpty()) {
            def toolEnv = []
            if (!Utils.withinAStage()) {
                script.stage(SyntheticStageNames.toolInstall()) {
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

        toolsList.each { l ->
            String k = l.get(0)
            Closure v = (Closure)l.get(1)
            String toolVer = v.call()

            script.tool(name: toolVer, type: Tools.typeForKey(k))

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
        if (agent == null
            && root.agent.getDeclarativeAgent(root, root) instanceof AbstractDockerAgent
            && root.options?.options?.get("newContainerPerStage") != null) {
            agent = root.agent
        }
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

    @Deprecated
    def inWrappers(Options options, Closure body) {
        return inWrappers(options?.wrappers, body)
    }

    /**
     * Executes the given closure inside 0 or more wrapper blocks if appropriate
     * @param wrappers A map of wrapper names to wrappers
     * @param body The closure to execute
     * @return The return of the resulting executed closure
     */
    def inWrappers(Map<String,Object> wrappers, Closure body) {
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
        } catch (Throwable e) {
            script.getProperty("currentBuild").result = Utils.getResultFromException(e)
            Utils.markStageFailedAndContinued(thisStage.name)
            if (stageError == null) {
                stageError = e
            }
        } finally {
            // And finally, run the post stage steps.
            if (root.hasSatisfiedConditions(thisStage.post, script.getProperty("currentBuild"), thisStage, stageError)) {
                Utils.logToTaskListener("Post stage")
                stageError = runPostConditions(thisStage.post, thisStage.agent ?: parentAgent, stageError, thisStage.name, thisStage)
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
                it?.getScript(script)?.evaluate()
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
     * @param context Optional - the context where we're being called
     * @return The stageError, which, if null when passed in and an error is hit, will be set to the first error encountered.
     */
    def runPostConditions(AbstractBuildConditionResponder responder,
                          Agent agentContext,
                          Throwable stageError,
                          String stageName = null,
                          Object context = null) {
        BuildCondition.orderedConditionNames.each { conditionName ->
            try {
                Closure c = responder.closureForSatisfiedCondition(conditionName, script.getProperty("currentBuild"),
                    context, stageError)
                if (c != null) {
                    catchRequiredContextForNode(agentContext) {
                        delegateAndExecute(c)
                    }
                }
            } catch (Throwable e) {
                script.getProperty("currentBuild").result = Utils.getResultFromException(e)
                if (stageName != null) {
                    Utils.markStageFailedAndContinued(stageName)
                }
                Utils.logToTaskListener("Error when executing ${conditionName} post condition:")
                Utils.logToTaskListener(getFullStackTrace(e))
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
        Utils.updateJobProperties(root.options?.properties, root.triggers?.triggers, root.parameters?.parameters, root.options?.options, script)
    }
}
