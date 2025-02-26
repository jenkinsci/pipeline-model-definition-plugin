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

package org.jenkinsci.plugins.pipeline.modeldefinition;

import java.io.IOException;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.fge.jsonschema.core.tree.JsonTree;
import com.github.fge.jsonschema.core.tree.SimpleJsonTree;
import com.github.fge.jackson.JsonLoader;
import com.google.common.base.Predicate;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.BulkChange;
import hudson.ExtensionList;
import hudson.model.*;
import hudson.util.Secret;
import hudson.triggers.Trigger;

import org.jenkinsci.plugins.pipeline.modeldefinition.actions.DisableRestartFromStageAction;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTStage;
import org.jenkinsci.plugins.pipeline.modeldefinition.options.impl.DisableRestartFromStage;
import org.jenkinsci.plugins.pipeline.modeldefinition.parser.JSONParser;

import java.util.function.Function;

import jenkins.model.Jenkins;

import org.apache.commons.codec.digest.DigestUtils;
import org.codehaus.groovy.ast.ASTNode;
import org.codehaus.groovy.ast.stmt.BlockStatement;
import org.codehaus.groovy.ast.stmt.Statement;
import org.codehaus.groovy.control.SourceUnit;
import org.jenkinsci.plugins.pipeline.StageStatus;
import org.jenkinsci.plugins.pipeline.StageTagsMetadata;
import org.jenkinsci.plugins.pipeline.SyntheticStage;
import org.jenkinsci.plugins.pipeline.modeldefinition.actions.DeclarativeJobAction;
import org.jenkinsci.plugins.pipeline.modeldefinition.actions.DeclarativeJobPropertyTrackerAction;
import org.jenkinsci.plugins.pipeline.modeldefinition.actions.ExecutionModelAction;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTBuildParameter;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTMethodCall;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTOption;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTTrigger;
import org.jenkinsci.plugins.pipeline.modeldefinition.causes.RestartDeclarativePipelineCause;
import org.jenkinsci.plugins.pipeline.modeldefinition.model.Environment;
import org.jenkinsci.plugins.pipeline.modeldefinition.model.StepsBlock;
import org.jenkinsci.plugins.pipeline.modeldefinition.options.DeclarativeOption;
import org.jenkinsci.plugins.pipeline.modeldefinition.options.impl.QuietPeriod;
import org.jenkinsci.plugins.pipeline.modeldefinition.steps.CredentialWrapper;
import org.jenkinsci.plugins.scriptsecurity.sandbox.whitelists.Whitelisted;
import org.jenkinsci.plugins.structs.SymbolLookup;
import org.jenkinsci.plugins.structs.describable.DescribableModel;
import org.jenkinsci.plugins.structs.describable.UninstantiatedDescribable;
import org.jenkinsci.plugins.workflow.actions.LabelAction;
import org.jenkinsci.plugins.workflow.actions.NotExecutedNodeAction;
import org.jenkinsci.plugins.workflow.actions.TagsAction;
import org.jenkinsci.plugins.workflow.actions.ThreadNameAction;
import org.jenkinsci.plugins.workflow.cps.CpsFlowExecution;
import org.jenkinsci.plugins.workflow.cps.CpsScript;
import org.jenkinsci.plugins.workflow.cps.CpsThread;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.graph.BlockEndNode;
import org.jenkinsci.plugins.workflow.graph.BlockStartNode;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.graphanalysis.*;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.job.properties.PipelineTriggersJobProperty;
import org.jenkinsci.plugins.workflow.steps.FlowInterruptedException;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;

import javax.lang.model.SourceVersion;

import java.util.concurrent.TimeUnit;
import java.util.*;
import java.util.stream.Collectors;

import groovy.lang.Closure;
import groovy.lang.MissingPropertyException;

/**
 * Utility methods for use primarily in CPS-transformed code to avoid excessive global whitelisting.
 *
 * @author Andrew Bayer
 */
@SuppressFBWarnings(value = {"SE_NO_SERIALVERSIONID", "LI_LAZY_INIT_STATIC"})
public class Utils {

    /**
     * Get the value for this name. First, check the script's properties, then parameters, and finally use the default
     * value, which is set at parse time.
     */
    public static Object getScriptPropOrParam(CpsScript script, String name) {
        try {
            return script.getProperty(name);
        } catch (MissingPropertyException e) {
            return script.getProperty("params") != null ? ((Map) script.getProperty("params")).get(name) : null;
        }
    }

    @Restricted(NoExternalUse.class)
    public static Map<String, Closure> getCredsFromResolver(Environment environment, CpsScript script) {
        if (environment != null) {
            environment.getCredsResolver().setScript(script);
            return environment.getCredsResolver().getClosureMap();
        } else {
            return new HashMap<>();
        }
    }

    // Note that we're not using StringUtils.strip(s, "'\"") here because we want to make sure we only get rid of
    // matched pairs of quotes/double-quotes.
    public static String trimQuotes(String s) {
        if ((s.startsWith("\"") && s.endsWith("\"")) || (s.startsWith("'") && s.endsWith("'"))) {
            return trimQuotes(s.substring(1, s.length() - 1));
        } else {
            return s;
        }
    }

    public static Predicate<FlowNode> nodeIdNotEquals(final FlowNode original) {
        return input -> input == null || original == null || !original.getId().equals(input.getId());
    }

    public static Predicate<FlowNode> endNodeForStage(final BlockStartNode startNode) {
        return input -> input != null && input instanceof BlockEndNode && ((BlockEndNode) input).getStartNode() == startNode;
    }

    public static String stringToSHA1(String s) {
        return DigestUtils.sha1Hex(s);
    }

    /**
     * Prints a log message to the Jenkins log, bypassing the echo step.
     * @param s The message to log
     */
    @Restricted(NoExternalUse.class)
    public static void logToTaskListener(String s) throws IOException {
        CpsThread thread = CpsThread.current();
        CpsFlowExecution execution = thread.getExecution();

        if (execution != null && execution.getOwner() != null && execution.getOwner().getListener() != null) {
            execution.getOwner().getListener().getLogger().println(s);
        }
    }

    /**
     * Returns true if we're currently children under a stage.
     *
     * @return true if we're in a stage and false otherwise
     */
    public static boolean withinAStage() {
        CpsThread thread = CpsThread.current();
        CpsFlowExecution execution = thread.getExecution();

        LinearBlockHoppingScanner scanner = new LinearBlockHoppingScanner();

        FlowNode stageNode = execution.getCurrentHeads().stream()
                .filter(h -> scanner.findFirstMatch(h, CommonUtils.isStageWithOptionalName()) != null)
                .findFirst()
                .orElse(null);

        return stageNode != null;
    }

    public static Predicate<FlowNode> isParallelBranchFlowNode(final String stageName) {
        return input -> {
            if (input != null) {
                LabelAction labelAction = input.getAction(LabelAction.class);
                ThreadNameAction threadNameAction = input.getAction(ThreadNameAction.class);
                if (labelAction != null && threadNameAction != null && (stageName == null || stageName.equals(threadNameAction.getThreadName()))) {
                    // This is actually a parallel block
                    return true;
                }
            }
            return false;
        };
    }

    public static List<FlowNode> findStageFlowNodes(String stageName, FlowExecution execution) {
        if (execution == null) {
            CpsThread thread = CpsThread.current();
            execution = thread.getExecution();
        }

        List<FlowNode> nodes = new ArrayList<>();

        ForkScanner scanner = new ForkScanner();

        FlowNode stage = scanner.findFirstMatch(execution.getCurrentHeads(), null, CommonUtils.isStageWithOptionalName(stageName));

        if (stage != null) {
            nodes.add(stage);

            // Additional check needed to get the possible enclosing parallel branch for a nested stage.
            Filterator<FlowNode> filtered = FlowScanningUtils.fetchEnclosingBlocks(stage)
                .filter(isParallelBranchFlowNode(stageName));

            filtered.forEachRemaining(f -> {
                if (f != null) {
                    nodes.add(f);
                }
            });
        }

        return nodes;
    }

    /**
     * Check if this run was caused by a restart.
     */
    @Whitelisted
    public static boolean isRestartedRun(CpsScript script) throws IOException {
        WorkflowRun r = (WorkflowRun) script.$build();
        RestartDeclarativePipelineCause cause = r.getCause(RestartDeclarativePipelineCause.class);

        return cause != null;
    }

    /**
     * Checks if this run was caused by a cause.
     */
    public static boolean isRunCausedBy(CpsScript script, String cause, String detail) throws IOException {
        WorkflowRun r = (WorkflowRun) script.$build();
        return r.getCauses().stream().anyMatch(c -> shouldRunBeAllowed(c, cause, detail));
    }

    /**
     * Checks if a specific cause must be aborted
     *
     * @param causeClass provided by the Run class
     * @param cause that should be allowed to continue
     * @param detail regarding the cause (currently the user in a UserIdCause)
     * @return if a specific cause must be aborted
     */
    public static boolean shouldRunBeAllowed(Cause causeClass, String cause, String detail) {
        if (causeClass instanceof Cause.UserIdCause && Cause.UserIdCause.class.getSimpleName().equals(cause)) {
            return detail == null || ((Cause.UserIdCause) causeClass).getUserId().equals(detail);
        } else {
            return causeClass.getClass().getSimpleName().matches("(?i).*" + cause + ".*");
        }
    }

    @Restricted(NoExternalUse.class)
    public static void updateRunAndJobActions(CpsScript script, String astUUID) throws Exception {
        WorkflowRun r = (WorkflowRun) script.$build();
        ExecutionModelAction action = r.getAction(ExecutionModelAction.class);
        if (action != null) {
            if (action.getStagesUUID() != null) {
                throw new IllegalStateException("Only one pipeline { ... } block can be executed in a single run.");
            }
            action.setStagesUUID(astUUID);
            r.save();
            Job<?, ?> job = r.getParent();
            if (job.getAction(DeclarativeJobAction.class) == null) {
                job.addAction(new DeclarativeJobAction());
            }
        }
    }

    public static void markStageWithTag(String stageName, String stepContextFlowNodeId, String tagName, String tagValue) throws IOException {
        CpsThread thread = CpsThread.current();
        FlowExecution execution = thread.getExecution();

        try {
            FlowNode stepContextFlowNode = execution.getNode(stepContextFlowNodeId);
            if (stepContextFlowNode == null) {
                return;
            }

            int count = 0;
            for (FlowNode node : stepContextFlowNode.iterateEnclosingBlocks()) {
                if (CommonUtils.isStageWithOptionalName(stageName).test(node) || isParallelBranchFlowNode(stageName).test(node)) {
                    addTagToFlowNode(node, tagName, tagValue);
                    count++;
                }
                if (count == 2) {
                    break;
                }
            }
        } catch (IOException e) {
            // Ignore.
        }
    }

    private static void addTagToFlowNode(FlowNode currentNode, String tagName, String tagValue) throws IOException {
        if (currentNode != null) {
            TagsAction tagsAction = currentNode.getAction(TagsAction.class);
            if (tagsAction == null) {
                tagsAction = new TagsAction();
                tagsAction.addTag(tagName, tagValue);
                currentNode.addAction(tagsAction);
            } else if (tagsAction.getTagValue(tagName) == null) {
                tagsAction.addTag(tagName, tagValue);
                currentNode.save();
            }
        }
    }

    public static void markStageWithTag(String stageName, String tagName, String tagValue) throws IOException {
        List<FlowNode> matched = findStageFlowNodes(stageName, null);

        for (FlowNode currentNode : matched) {
            addTagToFlowNode(currentNode, tagName, tagValue);
        }
    }

    public static void markStartAndEndNodesInStageAsNotExecuted(String stageName) throws IOException {
        CpsThread thread = CpsThread.current();
        FlowExecution execution = thread.getExecution();

        List<BlockStartNode> nodes = new ArrayList<>();

        ForkScanner scanner = new ForkScanner();

        FlowNode stage = scanner.findFirstMatch(execution.getCurrentHeads(), null, CommonUtils.isStageWithOptionalName(stageName));

        if (stage != null && stage instanceof BlockStartNode) {
            nodes.add((BlockStartNode) stage);

            // Additional check needed to get the possible enclosing parallel branch for a nested stage.
            Filterator<FlowNode> filtered = FlowScanningUtils.fetchEnclosingBlocks(stage)
                    .filter(isParallelBranchFlowNode(stageName));

            filtered.forEachRemaining(f -> {
                if (f != null && f instanceof BlockStartNode) {
                    nodes.add((BlockStartNode) f);
                }
            });
        }

        DepthFirstScanner depthFirstScanner = new DepthFirstScanner();

        for (BlockStartNode n : nodes) {
            n.addAction(new NotExecutedNodeAction());
            FlowNode endNode = depthFirstScanner.findFirstMatch(execution.getCurrentHeads(), null, endNodeForStage(n));
            if (endNode != null) {
                endNode.addAction(new NotExecutedNodeAction());
            }
        }
    }

    public static boolean stageHasStatusOf(@NonNull String stageName, @NonNull FlowExecution execution, @NonNull String... statuses) {
        return findStageFlowNodes(stageName, execution).stream()
                .allMatch(n -> n.getAction(TagsAction.class) != null && Arrays.asList(statuses).contains(n.getAction(TagsAction.class).getTagValue(StageStatus.TAG_NAME)));
    }

    public static <T extends StageTagsMetadata> T getTagMetadata(Class<T> c) {
        return ExtensionList.lookup(StageTagsMetadata.class).get(c);
    }

    public static StageStatus getStageStatusMetadata() {
        return getTagMetadata(StageStatus.class);
    }

    public static SyntheticStage getSyntheticStageMetadata() {
        return getTagMetadata(SyntheticStage.class);
    }

    @Restricted(NoExternalUse.class)
    public static void markStageFailedAndContinued(String stageName) throws IOException {
        markStageWithTag(stageName, getStageStatusMetadata().getTagName(), getStageStatusMetadata().getFailedAndContinued());
    }

    @Restricted(NoExternalUse.class)
    public static void markStageSkippedForFailure(String stageName) throws IOException {
        markStageWithTag(stageName, getStageStatusMetadata().getTagName(), getStageStatusMetadata().getSkippedForFailure());
    }

    @Restricted(NoExternalUse.class)
    public static void markStageSkippedForUnstable(String stageName) throws IOException {
        markStageWithTag(stageName, getStageStatusMetadata().getTagName(), getStageStatusMetadata().getSkippedForFailure());
    }

    @Whitelisted
    @Restricted(NoExternalUse.class)
    public static void markStageSkippedForConditional(String stageName) throws IOException {
        markStageWithTag(stageName, getStageStatusMetadata().getTagName(), getStageStatusMetadata().getSkippedForConditional());
    }

    /**
     * Creates and sets the loading for a cache of {@link Describable}s descending from the given descriptor type.
     *
     * @param type The {@link Descriptor} class whose extensions we want to find.
     * @param includeClassNames Optionally include class names as keys. Defaults to false.
     * @param excludedSymbols Optional list of symbol names to exclude from the cache.
     * @return A {@link LoadingCache} for looking up types from symbols.
     */
    @Restricted(NoExternalUse.class)
    public static LoadingCache<Object, Map<String, String>> generateTypeCache(Class<? extends Descriptor> type, boolean includeClassNames,
                                                                              List<String> excludedSymbols, Predicate<Descriptor> filter) {
        return CacheBuilder.newBuilder()
            .expireAfterWrite(10, TimeUnit.MINUTES)
            .build(new CacheLoader<Object, Map<String, String>>() {
            @Override
            public Map<String, String> load(Object key) throws Exception {
                return populateTypeCache(type, includeClassNames, excludedSymbols, filter);
            }
        });
    }

    @Restricted(NoExternalUse.class)
    public static LoadingCache<Object, Map<String, String>> generateTypeCache(Class<? extends Descriptor> type, boolean includeClassNames,
            List<String> excludedSymbols) {
        return generateTypeCache(type, includeClassNames, excludedSymbols, null);
    }

    @Restricted(NoExternalUse.class)
    public static LoadingCache<Object, Map<String, String>> generateTypeCache(Class<? extends Descriptor> type, boolean includeClassNames) {
        return generateTypeCache(type, includeClassNames, List.of());
    }

    @Restricted(NoExternalUse.class)
    public static LoadingCache<Object, Map<String, String>> generateTypeCache(Class<? extends Descriptor> type) {
        return generateTypeCache(type, false);
    }

    /**
     * Actually populates the type cache.
     *
     * @param type The {@link Descriptor} class whose extensions we want to find.
     * @param includeClassNames Optionally include class names as keys. Defaults to false.
     * @param excludedSymbols Optional list of symbol names to exclude from the cache.
     * @return A map of symbols or class names to class names.
     */
    @SuppressFBWarnings(value = "UPM_UNCALLED_PRIVATE_METHOD")
    private static Map<String, String> populateTypeCache(Class<? extends Descriptor> type,
                                                         boolean includeClassNames,
                                                         List<String> excludedSymbols,
                                                         Predicate<Descriptor> filter) {
        Map<String, String> knownTypes = new HashMap<>();

        for (Descriptor t : ExtensionList.lookup(type)) {
            if (filter == null || filter.apply(t)) {
                // Have to special-case StepDescriptor since it doesn't actually have symbols!
                if (t instanceof StepDescriptor) {
                    knownTypes.put(((StepDescriptor) t).getFunctionName(), t.clazz.getName());
                } else {
                    Set<String> symbolValue = SymbolLookup.getSymbolValue(t);
                    if (!symbolValue.isEmpty() && symbolValue.stream().noneMatch(excludedSymbols::contains)) {
                        knownTypes.put(symbolValue.iterator().next(), t.clazz.getName());
                    }
                }

                if (includeClassNames) {
                    // Add the class name mapping even if we also found the symbol, for backwards compatibility reasons.
                    knownTypes.put(t.clazz.getName(), t.clazz.getName());
                }
            }
        }

        return knownTypes;
    }

    /**
     * @param c The closure to wrap.
     */
    @Whitelisted
    public static StepsBlock createStepsBlock(Closure c) {
        // Jumping through weird hoops to get around the ejection for cases of JENKINS-26481.
        StepsBlock wrapper = new StepsBlock();
        wrapper.setClosure(c);

        return wrapper;
    }

    public static boolean validEnvIdentifier(String i) {
        if (!SourceVersion.isIdentifier(i)) {
            return false;
        } else if (!i.matches("[a-zA-Z_]+[a-zA-Z0-9_]*")) {
            return false;
        }
        return true;
    }

    /**
     * Get the appropriate result given an exception. If it's a {@link FlowInterruptedException}, return the result
     * on the exception, otherwise return {@link Result#FAILURE}.
     *
     * @param e The exception.
     * @return The result.
     */
    public static Result getResultFromException(Throwable e) {
        if (e instanceof FlowInterruptedException) {
            return ((FlowInterruptedException) e).getResult();
        } else {
            return Result.FAILURE;
        }
    }

    /**
     * Translate a list of objects which may either be instances of a given class or {@link UninstantiatedDescribable}s,
     * and return a list of those instances of the class and instantiated version of those {@link UninstantiatedDescribable}s.
     *
     * @param clazz The class we'll be instantiating, which must implement {@link Describable}.
     * @param toInstantiate The list of either instances of the class or {@link UninstantiatedDescribable}s that can be
     * instantiated to instances of the class.
     * @return The list of instances. May be empty.
     */
    @NonNull
    @Restricted(NoExternalUse.class)
    private static <T extends Describable> List<T> instantiateList(Class<T> clazz, List<Object> toInstantiate) throws Exception {
        List<T> l = new ArrayList<>();
        for (Object t : toInstantiate) {
            if (t instanceof UninstantiatedDescribable) {
                l.add(((UninstantiatedDescribable) t).instantiate(clazz));
            } else {
                l.add(clazz.cast(t));
            }
        }

        return l;
    }

    @Deprecated
    @Restricted(NoExternalUse.class)
    public static void updateJobProperties(@CheckForNull List<Object> propsOrUninstantiated,
                                           @CheckForNull List<Object> trigsOrUninstantiated,
                                           @CheckForNull List<Object> paramsOrUninstantiated,
                                           @NonNull CpsScript script) throws Exception {
        updateJobProperties(propsOrUninstantiated, trigsOrUninstantiated, paramsOrUninstantiated, null, script);
    }

    /**
     * Given the values from {@link org.jenkinsci.plugins.pipeline.modeldefinition.model.Options#getProperties()},
     * {@link org.jenkinsci.plugins.pipeline.modeldefinition.model.Triggers#getTriggers()}, and
     * {@link org.jenkinsci.plugins.pipeline.modeldefinition.model.Parameters#getParameters()}, figure out which job
     * properties, triggers, and parameters should be added/removed to the job, and actually do so, properly preserving
     * such job properties, triggers, and parameters which were defined outside of the Jenkinsfile.
     *
     * @param propsOrUninstantiated Newly-defined job properties, potentially a mix of {@link JobProperty}s and
     *   {@link UninstantiatedDescribable}s.
     * @param trigsOrUninstantiated Newly-defined triggers, potentially a mix of {@link Trigger}s and
     *   {@link UninstantiatedDescribable}s.
     * @param paramsOrUninstantiated Newly-defined parameters, potentially a mix of {@link ParameterDefinition}s and
     *   {@link UninstantiatedDescribable}s.
     * @param optionsOrUninstantiated Newly-defined Declarative options, potentially a mix of {@link DeclarativeOption}s and
     *   {@link UninstantiatedDescribable}s.
     * @param script The CpsScript instance.
     */
    @Restricted(NoExternalUse.class)
    public static void updateJobProperties(@CheckForNull List<Object> propsOrUninstantiated,
                                           @CheckForNull List<Object> trigsOrUninstantiated,
                                           @CheckForNull List<Object> paramsOrUninstantiated,
                                           @CheckForNull Map<String, DeclarativeOption> optionsOrUninstantiated,
                                           @NonNull CpsScript script) throws Exception {
        List<JobProperty> rawJobProperties = instantiateList(JobProperty.class, propsOrUninstantiated != null ? propsOrUninstantiated : Collections.emptyList());
        List<Trigger> rawTriggers = instantiateList(Trigger.class, trigsOrUninstantiated != null ? trigsOrUninstantiated : Collections.emptyList());
        List<ParameterDefinition> rawParameters = instantiateList(ParameterDefinition.class, paramsOrUninstantiated != null ? paramsOrUninstantiated : Collections.emptyList());
        List<DeclarativeOption> rawOptions = optionsOrUninstantiated != null ? new ArrayList<>(optionsOrUninstantiated.values()) : Collections.emptyList();

        WorkflowRun r = (WorkflowRun) script.$build();
        WorkflowJob j = r.getParent();

        synchronized (j) {
            List<JobProperty> existingJobProperties = existingJobPropertiesForJob(j);
            List<Trigger> existingTriggers = existingTriggersForJob(j);
            List<ParameterDefinition> existingParameters = existingParametersForJob(j);

            Set<String> previousProperties = new HashSet<>();
            Set<String> previousTriggers = new HashSet<>();
            Set<String> previousParameters = new HashSet<>();
            Set<String> previousOptions = new HashSet<>();

            // First, use the action from the job if it's present.
            DeclarativeJobPropertyTrackerAction previousAction = j.getAction(DeclarativeJobPropertyTrackerAction.class);

            // Fall back to previous build for compatibility reasons.
            if (previousAction == null) {
                WorkflowRun previousBuild = r.getPreviousCompletedBuild();
                if (previousBuild != null) {
                    previousAction = previousBuild.getAction(DeclarativeJobPropertyTrackerAction.class);
                }
            }
            if (previousAction != null) {
                previousProperties.addAll(previousAction.getJobProperties());
                previousTriggers.addAll(previousAction.getTriggers());
                previousParameters.addAll(previousAction.getParameters());
                previousOptions.addAll(previousAction.getOptions());
            }

            List<JobProperty> currentJobProperties = getPropertiesToApply(rawJobProperties, existingJobProperties, previousProperties);
            // Find all existing job properties that aren't of classes we've explicitly defined, *and* aren't
            // in the set of classes of job properties defined by the Jenkinsfile in the previous build. Add those too.
            // Oh, and ignore the PipelineTriggersJobProperty and ParameterDefinitionsProperty - we handle those separately.
            // And stash the property classes that should be removed aside as well.
            List<JobProperty> propsToRemove = getPropertiesToRemove(currentJobProperties, existingJobProperties);
            List<JobProperty> propsToUpdate = getPropertiesToUpdate(currentJobProperties, existingJobProperties);

            List<Trigger> currentTriggers = getTriggersToApply(rawTriggers, existingTriggers, previousTriggers);
            boolean isTriggersChanged = !isTriggersListEquals(currentTriggers, existingTriggers);

            List<ParameterDefinition> parametersToApply = getParametersToApply(rawParameters, existingParameters, previousParameters);
            boolean isParametersChanged = !isParametersListEquals(parametersToApply, existingParameters);

            BulkChange bc = new BulkChange(j);
            try {
                boolean isJobChanged = false;
                // Check if QuietPeriod option is specified
                QuietPeriod quietPeriod = rawOptions.stream()
                        .filter(option -> option instanceof QuietPeriod)
                        .map(option -> (QuietPeriod) option)
                        .findFirst()
                        .orElse(null);
                Integer newQuietPeriod = null;
                if (quietPeriod != null) {
                    newQuietPeriod = quietPeriod.getQuietPeriod();
                } else {
                    String quietPeriodName = Jenkins.get().getDescriptorByType(QuietPeriod.DescriptorImpl.class).getName();
                    // If the quiet period was set by the previous build, wipe it out.
                    if (quietPeriodName != null && previousOptions.contains(quietPeriodName)) {
                        newQuietPeriod = Jenkins.get().getQuietPeriod();
                    }
                }
                if (newQuietPeriod != null && j.getQuietPeriod() != newQuietPeriod) {
                    j.setQuietPeriod(newQuietPeriod);
                    isJobChanged = true;
                }

                DisableRestartFromStage declaredDisableRestartFromStageOption = rawOptions.stream()
                        .filter(option -> option instanceof DisableRestartFromStage)
                        .map(option -> (DisableRestartFromStage) option)
                        .findFirst()
                        .orElse(null);
                DisableRestartFromStageAction currentRestartFromStageAction = j.getAction(DisableRestartFromStageAction.class);
                if (currentRestartFromStageAction == null && declaredDisableRestartFromStageOption != null) {
                    j.addAction(new DisableRestartFromStageAction());
                } else if (currentRestartFromStageAction != null && declaredDisableRestartFromStageOption == null) {
                    j.removeAction(currentRestartFromStageAction);
                }

                // If there are any triggers update them if needed
                // It would be cool to only add or remove individual triggers,
                // however the "addTrigger()" method calls removeProperty and adds a new one.
                // Example: Adding 3 single triggers causes the first trigger to be removed and added two extra times.
                // and the second trigger to be removed and added one extra time
                if (isTriggersChanged) {
                    j.removeProperty(PipelineTriggersJobProperty.class);
                    if (!currentTriggers.isEmpty()) {
                        j.addProperty(new PipelineTriggersJobProperty(currentTriggers));
                    }
                    isJobChanged = true;
                }

                // If any parameter changed replace all, due to parameter stateless
                if (isParametersChanged) {
                    j.removeProperty(ParametersDefinitionProperty.class);
                    if (!parametersToApply.isEmpty()) {
                        j.addProperty(new ParametersDefinitionProperty(parametersToApply));
                    }
                    isJobChanged = true;
                }

                // Remove the job properties we defined in previous Jenkinsfiles but don't any more.
                for (JobProperty prop : propsToRemove) {
                    j.removeProperty(prop);
                    isJobChanged = true;
                }

                // Now replace properties we know need to be added or updated.
                for (JobProperty p : propsToUpdate) {
                    // Remove the existing instance(s) of the property class before we add the new one. We're looping and
                    // removing multiple to deal with the results of JENKINS-44809.
                    while (j.removeProperty(p.getClass()) != null) {
                        // removed one, try again in case there is more
                    }
                    j.addProperty(p);
                    isJobChanged = true;
                }

                // Add the action tracking what we added (or empty otherwise)
                DeclarativeJobPropertyTrackerAction newPropertyTrackerAction = new DeclarativeJobPropertyTrackerAction(rawJobProperties, rawTriggers, rawParameters, rawOptions);
                if (previousAction == null ||
                        !(previousAction.getJobProperties().equals(newPropertyTrackerAction.getJobProperties()) &&
                                previousAction.getParameters().equals(newPropertyTrackerAction.getParameters()) &&
                                previousAction.getOptions().equals(newPropertyTrackerAction.getOptions()) &&
                                previousAction.getTriggers().equals(newPropertyTrackerAction.getTriggers()))) {
                    j.replaceAction(newPropertyTrackerAction);
                    isJobChanged = true;
                }

                if (isJobChanged) {
                    bc.commit();
                }
            } finally {
                bc.abort();
            }
        }
    }

    /**
     * Compare lists of {@link ParameterDefinition}.
     *
     * @param first  First list of parameter definitions.
     * @param second Second list of parameter definitions.
     *
     * @return {@code true}, if both lists of {@link ParameterDefinition} are contain same elements;
     * {@code false} otherwise
     */
    private static boolean isParametersListEquals(@NonNull List<ParameterDefinition> first,
                                                  @NonNull List<ParameterDefinition> second) {
        if (first.size() != second.size()) {
            return false;
        }
        Map<String, ParameterDefinition> firstNameToParameterMap = first.stream()
                .collect(Collectors.toMap(ParameterDefinition::getName, param -> param));
        Map<String, ParameterDefinition> secondNameToParameterMap = second.stream()
                .collect(Collectors.toMap(ParameterDefinition::getName, param -> param));
        // Check that all parameter's names are equals.
        if (!firstNameToParameterMap.keySet().equals(secondNameToParameterMap.keySet())) {
            return false;
        }
        // Check until first non-equal parameter.
        return firstNameToParameterMap.values().stream()
                .noneMatch(firstParam -> !isParametersEquals(firstParam, secondNameToParameterMap.get(firstParam.getName())));
    }

    /**
     * Compare {@link ParameterDefinition} objects.
     *
     * @param first  First parameter definition.
     * @param second Second parameter definition.
     *
     * @return {@code true}, if both {@link ParameterDefinition} objects are equals; {@code false} otherwise.
     */
    private static boolean isParametersEquals(@NonNull ParameterDefinition first, @NonNull ParameterDefinition second) {
        if (!first.getDescriptor().getId().equals(second.getDescriptor().getId()) || !Objects.equals(first.getDescription(), second.getDescription())) {
            return false;
        }
        return Objects.equals(first, second) || isObjectsEqualsXStream(first, second);
    }

    /**
     * Compare objects' string representations after conversion to XML
     * Require to use after call equals()
     *
     * Used for objects:
     * - all fields are unknown before comparison
     * - without overwritten methods equals() and hashCode()
     *
     * @param first  First object for comparing
     * @param second Second object for comparing
     *
     * @return {@code true} if string representation of objects in XML format are equals; {@code false} otherwise
     */
    private static boolean isObjectsEqualsXStream(@NonNull Object first, @NonNull Object second) {
        String firstMarshaled = Items.XSTREAM2.toXML(first);
        String secondMarshaled = Items.XSTREAM2.toXML(second);
        return firstMarshaled.equals(secondMarshaled);
    }

    /**
     * Given the new triggers defined in the Jenkinsfile, the existing triggers already on the job, and the set of
     * trigger classes that may have been recorded as defined in the Jenkinsfile in the previous build, return a list of
     * triggers that will actually be applied, including both the newly defined in Jenkinsfile triggers and any triggers
     * defined outside of the Jenkinsfile.
     *
     * @param newTriggers New triggers from the Jenkinsfile.
     * @param existingTriggers Any triggers already defined on the job.
     * @param prevDefined Any trigger classes recorded in a {@link DeclarativeJobPropertyTrackerAction} on the previous run.
     *
     * @return A list of triggers to apply. May be empty.
     */
    @NonNull
    private static List<Trigger> getTriggersToApply(@CheckForNull List<Trigger> newTriggers,
                                                    @NonNull List<Trigger> existingTriggers,
                                                    @NonNull Set<String> prevDefined) {
        // Store triggers with unique descriptor id (Class)
        Set<Trigger> toApply = new TreeSet<>(Comparator.comparing(t -> t.getDescriptor().getId()));
        toApply.addAll(existingTriggers);
        // Remove all triggers defined in previous Jenkinsfile and may be deprecated
        toApply.removeIf(t -> prevDefined.contains(t.getDescriptor().getId()));
        // Replace all triggers defined in current Jenkinsfile
        if (newTriggers != null) {
            toApply.addAll(newTriggers);
        }
        return new ArrayList<>(toApply);
    }

    /**
     * Compare lists of {@link Trigger}s.
     *
     * @param first  First list of triggers.
     * @param second Second list of triggers.
     *
     * @return {@code true}, if both lists of {@link Trigger}s contain same elements;
     * {@code false} otherwise
     */
    @NonNull
    private static boolean isTriggersListEquals(@NonNull List<Trigger> first,
                                                @NonNull List<Trigger> second) {
        Map<String, Trigger> firstMap = first.stream()
                .collect(Collectors.toMap(t -> t.getDescriptor().getId(), t -> t));
        Map<String, Trigger> secondMap = second.stream()
                .collect(Collectors.toMap(t -> t.getDescriptor().getId(), t -> t));

        if (first.size() != second.size()) {
            return false;
        }

        return secondMap.values().stream()
                .noneMatch(t -> firstMap.get(t.getDescriptor().getId()) == null ||
                        !(Objects.equals(t, firstMap.get(t.getDescriptor().getId())) ||
                                isObjectsEqualsXStream(t, firstMap.get(t.getDescriptor().getId()))));
    }

    /**
     * Given the new parameters defined in the Jenkinsfile, the existing parameters already on the job, and the set of
     * parameter names that may have been recorded as defined in the Jenkinsfile in the previous build, return a list of
     * parameters that will actually be applied, including both the newly defined in Jenkinsfile parameters and any
     * parameters defined outside of the Jenkinsfile.
     *
     * @param newParameters New parameters from the Jenkinsfile.
     * @param existingParameters Any parameters already defined on the job.
     * @param prevDefined Any parameter names recorded in a {@link DeclarativeJobPropertyTrackerAction} on the previous run.
     *
     * @return A list of parameters to apply. May be empty.
     */
    @NonNull
    private static List<ParameterDefinition> getParametersToApply(@CheckForNull List<ParameterDefinition> newParameters,
                                                                  @NonNull List<ParameterDefinition> existingParameters,
                                                                  @NonNull Set<String> prevDefined) {
        Set<String> seenNames = new HashSet<>();
        List<ParameterDefinition> toApply = new ArrayList<>();
        if (newParameters != null) {
            toApply.addAll(newParameters);
            seenNames.addAll(newParameters.stream().map(ParameterDefinition::getName).collect(Collectors.toList()));
        }
        // Find all existing parameters that aren't of names we've explicitly defined, *and* aren't
        // in the set of names of parameters defined by the Jenkinsfile in the previous build. Add those too.
        toApply.addAll(existingParameters.stream()
                .filter(param -> !seenNames.contains(param.getName()) && !prevDefined.contains(param.getName()))
                .collect(Collectors.toList()));

        return toApply;
    }

    /**
     * Given the new parameters defined in the Jenkinsfile, the existing parameters already on the job, and the set of
     * parameter names that may have been recorded as defined in the Jenkinsfile in the previous build, return a list of
     * parameters that will actually be applied, including both the newly defined in Jenkinsfile parameters and any
     * parameters defined outside of the Jenkinsfile.
     *
     * @param newProperties      New properties from the Jenkinsfile.
     * @param existingProperties Any properties already defined on the job.
     * @param prevDefined        Any property names recorded in a {@link DeclarativeJobPropertyTrackerAction} on the
     * previous run.
     *
     * @return A list of parameters to apply. May be empty.
     */
    @NonNull
    private static List<JobProperty> getPropertiesToApply(@CheckForNull List<JobProperty> newProperties,
                                                          @NonNull List<JobProperty> existingProperties,
                                                          @NonNull Set<String> prevDefined) {
        // Store properties with unique descriptor id (Class)
        Set<JobProperty> toApply = new TreeSet<>(Comparator.comparing(p -> p.getDescriptor().getId()));
        toApply.addAll(existingProperties);
        // Remove all properties defined in previous Jenkinsfile and may be no longer exist
        toApply.removeIf(p -> prevDefined.contains(p.getDescriptor().getId()));
        // Replace all properties defined in current Jenkinsfile
        if (newProperties != null) {
            toApply.addAll(newProperties);
        }
        return new ArrayList<>(toApply);
    }

    /**
     * Helper method for getting Triggers, which should be updated or added to a job.
     *
     * @param currentProperties  Actual triggers for the job.
     * @param existingProperties Any triggers already defined on the job.
     *
     * @return A list of properties to add/update. May be empty.
     */
    @NonNull
    private static List<JobProperty> getPropertiesToUpdate(@CheckForNull List<JobProperty> currentProperties,
                                                           @NonNull List<JobProperty> existingProperties) {
        Map<String, JobProperty> descriptorsToExistingProperties =
                existingProperties.stream().collect(Collectors.toMap(p -> p.getDescriptor().getId(), p -> p, (a, b) -> b));
        // Create map <DescriptorId, Count> for extract duplicated parameters
        Map<String, Integer> countMap = new HashMap<>();
        for (JobProperty property : existingProperties) {
            countMap.compute(property.getDescriptor().getId(), (key, count) -> count == null ? 1 : count + 1);
        }

        return currentProperties.stream()
                .filter(p -> descriptorsToExistingProperties.get(p.getDescriptor().getId()) == null ||
                        countMap.get(p.getDescriptor().getId()) > 1 ||
                        !(Objects.equals(p, descriptorsToExistingProperties.get(p.getDescriptor().getId())) ||
                                isObjectsEqualsXStream(p, descriptorsToExistingProperties.get(p.getDescriptor().getId()))))
                .collect(Collectors.toList());
    }

    /**
     * Helper method for getting Triggers, which should be removed from a job.
     *
     * @param currentProperties  Actual triggers for the job.
     * @param existingProperties Any triggers already defined on the job.
     *
     * @return A list of properties to remove. May be empty.
     */
    @NonNull
    private static List<JobProperty> getPropertiesToRemove(@CheckForNull List<JobProperty> currentProperties,
                                                           @NonNull List<JobProperty> existingProperties) {
        Set<String> currentPropertiesDescriptors = currentProperties != null ? currentProperties.stream()
                .map(p -> p.getDescriptor().getId())
                .collect(Collectors.toSet()) : Set.of();
        return existingProperties.stream()
                .filter(p -> !currentPropertiesDescriptors.contains(p.getDescriptor().getId()))
                .collect(Collectors.toList());
    }

    /**
     * Helper method for getting the appropriate {@link JobProperty}s from a job.
     *
     * @param j a job
     * @return A list of all {@link JobProperty}s on the given job, other than ones specifically excluded because we're
     * handling them elsewhere. May be empty.
     */
    @NonNull
    private static List<JobProperty> existingJobPropertiesForJob(@NonNull WorkflowJob j) {
        List<JobProperty> existing = new ArrayList<>();
        existing.addAll(j.getAllProperties().stream()
                .filter(p -> !(p instanceof PipelineTriggersJobProperty) && !(p instanceof ParametersDefinitionProperty))
                .collect(Collectors.toList()));

        return existing;
    }

    /**
     * Helper method for getting all {@link Trigger}s on a job.
     *
     * @param j a job
     * @return A list of all {@link Trigger}s defined in the job's {@link PipelineTriggersJobProperty}. May be empty.
     */
    @NonNull
    private static List<Trigger> existingTriggersForJob(@NonNull WorkflowJob j) {
        List<Trigger> existing = new ArrayList<>();
        if (j.getProperty(PipelineTriggersJobProperty.class) != null) {
            existing.addAll(j.getProperty(PipelineTriggersJobProperty.class).getTriggers());
        }
        return existing;
    }

    /**
     * Helper method for getting all {@link ParameterDefinition}s on a job.
     *
     * @param j a job
     * @return A list of all {@link ParameterDefinition}s defined in the job's {@link ParametersDefinitionProperty}. May
     * be empty.
     */
    @NonNull
    private static List<ParameterDefinition> existingParametersForJob(@NonNull WorkflowJob j) {
        List<ParameterDefinition> existing = new ArrayList<>();
        if (j.getProperty(ParametersDefinitionProperty.class) != null) {
            existing.addAll(j.getProperty(ParametersDefinitionProperty.class).getParameterDefinitions());
        }
        return existing;
    }

    /**
     * Obtains the source text of the given {@link org.codehaus.groovy.ast.ASTNode}.
     */
    public static String getSourceTextForASTNode(@NonNull ASTNode n, @NonNull SourceUnit sourceUnit) throws IOException {
        StringBuilder result = new StringBuilder();
        int beginLine = n.getLineNumber();
        int endLine = n.getLastLineNumber();
        int beginLineColumn = n.getColumnNumber();
        int endLineLastColumn = n.getLastColumnNumber();

        // The node seems to be lying about the last line, so go through each statement to try to make sure
        if (n instanceof BlockStatement) {
            for (Statement s : ((BlockStatement) n).getStatements()) {
                if (s.getLineNumber() < beginLine) {
                    beginLine = s.getLineNumber();
                    beginLineColumn = s.getColumnNumber();
                }
                if (s.getLastLineNumber() > endLine) {
                    endLine = s.getLastLineNumber();
                    endLineLastColumn = s.getLastColumnNumber();
                }
            }
        }
        for (int x = beginLine; x <= endLine; x++) {
            String line = sourceUnit.getSource().getLine(x, null);
            if (line == null)
                throw new AssertionError("Unable to get source line" + x);

            if (x == endLine) {
                line = line.substring(0, endLineLastColumn - 1);
            }
            if (x == beginLine) {
                line = line.substring(beginLineColumn - 1);
            }
            result.append(line).append('\n');
        }

        return result.toString().trim();
    }

    @NonNull
    public static List<Class<? extends Describable>> parentsForMethodCall(@NonNull ModelASTMethodCall meth) {
        if (meth instanceof ModelASTTrigger) {
            return Collections.singletonList(Trigger.class);
        } else if (meth instanceof ModelASTBuildParameter) {
            return Collections.singletonList(ParameterDefinition.class);
        } else if (meth instanceof ModelASTOption) {
            return Arrays.asList(JobProperty.class, DeclarativeOption.class, Step.class);
        } else {
            return Collections.emptyList();
        }
    }

    /**
     * Get the stage we're restarting at, if this build is a restarted one in the first place.
     * @param script The script from ModelInterpreter
     * @return The name of the stage we're restarting at, if defined, and null otherwise.
     */
    public static String getRestartedStage(@NonNull CpsScript script) throws IOException {
        WorkflowRun r = (WorkflowRun) script.$build();

        RestartDeclarativePipelineCause cause = r.getCause(RestartDeclarativePipelineCause.class);

        return cause != null ? cause.getOriginStage() : null;
    }

    /**
     * Convenience method for parsing a {@link ModelASTStage} from a JSON string
     * @param stageJSON The JSON string representing the stage
     * @return The parsed result of the JSON, or null
     * @throws Exception If anything goes wrong in parsing the JSON.
     */
    public static ModelASTStage parseStageFromJSON(String stageJSON) throws Exception {
        JsonNode json = JsonLoader.fromString(stageJSON);
        JsonTree jsonTree = new SimpleJsonTree(json);
        JSONParser parser = new JSONParser(null);
        return parser.parseStage(jsonTree);
    }
}
