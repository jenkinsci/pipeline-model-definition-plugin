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

import com.google.common.base.Predicate
import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.common.cache.LoadingCache;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings
import groovy.json.StringEscapeUtils
import hudson.BulkChange
import hudson.ExtensionList
import hudson.model.Describable
import hudson.model.Descriptor
import hudson.model.JobProperty
import hudson.model.ParameterDefinition
import hudson.model.ParametersDefinitionProperty
import hudson.model.Result
import hudson.triggers.Trigger
import org.apache.commons.codec.digest.DigestUtils
import org.apache.commons.lang.StringUtils
import org.codehaus.groovy.ast.ASTNode
import org.codehaus.groovy.ast.stmt.BlockStatement
import org.codehaus.groovy.ast.stmt.Statement
import org.codehaus.groovy.control.SourceUnit
import org.jenkinsci.plugins.pipeline.StageStatus
import org.jenkinsci.plugins.pipeline.StageTagsMetadata
import org.jenkinsci.plugins.pipeline.SyntheticStage
import org.jenkinsci.plugins.pipeline.modeldefinition.actions.ExecutionModelAction
import org.jenkinsci.plugins.pipeline.modeldefinition.actions.DeclarativeJobPropertyTrackerAction
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTPipelineDef
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTStages
import org.jenkinsci.plugins.pipeline.modeldefinition.model.Environment
import org.jenkinsci.plugins.pipeline.modeldefinition.model.StageConditionals
import org.jenkinsci.plugins.pipeline.modeldefinition.model.Root
import org.jenkinsci.plugins.pipeline.modeldefinition.model.StepsBlock
import org.jenkinsci.plugins.pipeline.modeldefinition.parser.Converter
import org.jenkinsci.plugins.pipeline.modeldefinition.steps.CredentialWrapper
import org.jenkinsci.plugins.pipeline.modeldefinition.when.DeclarativeStageConditionalDescriptor
import org.jenkinsci.plugins.scriptsecurity.sandbox.whitelists.Whitelisted
import org.jenkinsci.plugins.structs.SymbolLookup
import org.jenkinsci.plugins.structs.describable.DescribableModel
import org.jenkinsci.plugins.structs.describable.UninstantiatedDescribable
import org.jenkinsci.plugins.workflow.actions.TagsAction
import org.jenkinsci.plugins.workflow.cps.CpsFlowExecution
import org.jenkinsci.plugins.workflow.cps.CpsScript
import org.jenkinsci.plugins.workflow.cps.CpsThread
import org.jenkinsci.plugins.workflow.flow.FlowExecution
import org.jenkinsci.plugins.workflow.flow.FlowExecutionOwner
import org.jenkinsci.plugins.workflow.graphanalysis.LinearBlockHoppingScanner
import org.jenkinsci.plugins.workflow.cps.nodes.StepEndNode
import org.jenkinsci.plugins.workflow.cps.nodes.StepStartNode
import org.jenkinsci.plugins.workflow.graph.FlowNode
import org.jenkinsci.plugins.workflow.job.WorkflowJob
import org.jenkinsci.plugins.workflow.job.WorkflowRun
import org.jenkinsci.plugins.workflow.job.properties.PipelineTriggersJobProperty
import org.jenkinsci.plugins.workflow.multibranch.BranchJobProperty
import org.jenkinsci.plugins.workflow.steps.FlowInterruptedException
import org.jenkinsci.plugins.workflow.steps.StepDescriptor
import org.jenkinsci.plugins.workflow.support.steps.StageStep

import javax.annotation.CheckForNull
import javax.annotation.Nonnull
import javax.annotation.Nullable
import javax.lang.model.SourceVersion
import java.lang.reflect.ParameterizedType
import java.util.concurrent.TimeUnit

// TODO: Prune like mad once we have step-in-groovy and don't need these static whitelisted wrapper methods.
/**
 * Utility methods for use primarily in CPS-transformed code to avoid excessive global whitelisting.
 *
 * @author Andrew Bayer
 */
@SuppressFBWarnings(value="SE_NO_SERIALVERSIONID")
public class Utils {

    /**
     * Workaround for not having to whitelist isAssignableFrom, metaClass etc to determine whether a field on
     * a class is of a specific type.
     *
     * @param fieldType The type we're checking
     * @param actualClass The class we're inspecting
     * @param fieldName The name of the field - could be a singular when the field name is plural, in which case
     *   we'll get the actual name from actualFieldName(...)
     * @return True if the field exists and is of the given type.
     */
    public static boolean isFieldA(Class fieldType, Class actualClass, String fieldName) {
        def actualFieldName = actualFieldName(actualClass, fieldName)
        def realFieldType = actualClass.metaClass.getMetaProperty(actualFieldName)?.type

        if (realFieldType == null) {
            return false
        } else {
            return realFieldType == fieldType || fieldType.isAssignableFrom(realFieldType)
        }
    }

    /**
     * Gets the actual field name for a possibly-needs-to-be-pluralized name.
     *
     * @param actualClass The class we're inspecting
     * @param fieldName The original field name, which could need to be pluralized.
     * @return The real field name, pluralized if necessary, or null if not found.
     */
    public static String actualFieldName(Class actualClass, String fieldName) {
        if (actualClass.metaClass.getMetaProperty(fieldName) != null) {
            return fieldName
        } else if (actualClass.metaClass.getMetaProperty("${fieldName}s") != null) {
            return "${fieldName}s"
        } else {
            return null
        }
    }

    /**
     * Get the actual field type or contained field type in the case of parameterized types in the inspected class.
     *
     * @param actualClass The class we're inspecting
     * @param fieldName The field name we're looking for, which could get pluralized.
     * @return The class of the field in the inspected class, or the class contained in the list or map.
     */
    public static Class actualFieldType(Class actualClass, String fieldName) {
        def actualFieldName = actualFieldName(actualClass, fieldName)
        if (actualFieldName == null) {
            return null
        } else {
            def field = actualClass.getDeclaredFields().find { !it.isSynthetic() && it.name == actualFieldName }
            // If the field's a ParameterizedType, we need to check it to see if it's containing a Plumber class.
            if (field.getGenericType() instanceof ParameterizedType) {
                if (Map.class.isAssignableFrom(field.getType())) {
                    return (Class) ((ParameterizedType) field.getGenericType()).getActualTypeArguments()[1]
                } else {
                    // First class listed in the actual type arguments - we ignore anything past this because eh.
                    return (Class) ((ParameterizedType) field.getGenericType()).getActualTypeArguments()[0]
                }
            } else {
                return field.getType()
            }
        }

    }

    /**
     * Simple wrapper for isInstance to avoid whitelisting issues.
     *
     * @param c The class to check against
     * @param o The object to check
     * @return True if the object is an instance of the class, false otherwise
     */
    public static boolean instanceOfWrapper(Class c, Object o) {
        return c.isInstance(o)
    }

    /**
     * Simple wrapper for isAssignableFrom to avoid whitelisting issues.
     *
     * @param c The class that should be assignable from
     * @param o The class to check
     * @return True if o can be assigned to c, false otherwise
     */
    public static boolean assignableFromWrapper(Class c, Class o) {
        return c.isAssignableFrom(o)
    }

    public static boolean hasScmContext(CpsScript script) {
        try {
            // Just rely on SCMVar's own context-checking (via CpsScript) rather than brewing our own.
            script.getProperty("scm")
            return true
        } catch (_) {
            // If we get an IllegalStateException, "checkout scm" isn't valid, so return false.
            return false
        }
    }

    static Object getScriptPropOrParam(CpsScript script, String name) {
        try {
            return script.getProperty(name)
        } catch (MissingPropertyException e) {
            return script.params?.get(name)
        }
    }

    static boolean hasJobProperties(CpsScript script) {
        WorkflowRun r = script.$build()

        WorkflowJob j = r.getParent()

        return j.getAllProperties().any { p ->
            // We only consider PipelineTriggersJobProperty and ParametersDefinitionProperty if they're empty.
            if (p instanceof PipelineTriggersJobProperty) {
                if (!p.getTriggers().isEmpty()) {
                    return true
                }
            } else if (p instanceof ParametersDefinitionProperty) {
                if (!p.getParameterDefinitions().isEmpty()) {
                    return true
                }
            } else {
                return !(p instanceof BranchJobProperty)
            }
        }
    }

    static Root attachDeclarativeActions(@Nonnull Root root, CpsScript script) {
        WorkflowRun r = script.$build()
        ModelASTPipelineDef model = Converter.parseFromWorkflowRun(r)

        if (model != null) {
            ModelASTStages stages = model.stages

            stages.removeSourceLocation()
            if (r.getAction(ExecutionModelAction.class) == null) {
                r.addAction(new ExecutionModelAction(stages))
            }

        }

        return root
    }



    /**
     * Takes a string and makes sure it starts/ends with double quotes so that it can be evaluated correctly.
     *
     * @param s The original string
     * @return Either the original string, if it already starts/ends with double quotes, or the original string
     * prepended/appended with double quotes.
     */
    public static String prepareForEvalToString(String s) {
        String toEval = s ?: ""
        if (!toEval.startsWith('"') || !toEval.endsWith('"')) {
            if (toEval.indexOf('\n') == -1) {
                toEval = '"' + escapeForEval(toEval) + '"';
            } else {
                toEval = '"""' + escapeForEval(toEval) + '"""';
            }
        }

        return toEval
    }

    static String escapeForEval(String s) {
        s = StringUtils.replace(s, '\\\\', '\\\\')
        s = StringEscapeUtils.escapeJava(s)

        s.eachMatch(/\$\{.*?\}/) { m ->
            s = StringUtils.replaceOnce(s, m, StringEscapeUtils.unescapeJava(m))
        }

        return s
    }

    static String unescapeFromEval(String s) {
        return StringEscapeUtils.unescapeJava(s)
    }

    static String unescapeDollars(String s) {
        return s
    }

    static Map<String,Closure> getCredsFromResolver(Environment environment, CpsScript script) {
        if (environment != null) {
            environment.credsResolver.setScript(script)
            return environment.credsResolver.closureMap
        } else {
            return [:]
        }
    }

    static List<List<String>> getEnvCredentials(Environment environment, CpsScript script, Environment parent = null) {
        List<List<String>> credsTuples = new ArrayList<>()
        // TODO: Creds
        return credsTuples
    }

    /**
     * This exists for pre-1.1.2 -> later upgrades of running builds only.
     *
     * @param environment The environment to pull credentials from
     * @return A non-null but possibly empty map of strings to {@link CredentialWrapper}s
     */
    @Nonnull
    static Map<String, CredentialWrapper> getLegacyEnvCredentials(@Nonnull Environment environment) {
        Map<String, CredentialWrapper> m = [:]
        environment.each {k, v ->
            if (v instanceof  CredentialWrapper) {
                m["${k}"] = v;
            }
        }
        return m
    }


    // Note that we're not using StringUtils.strip(s, "'\"") here because we want to make sure we only get rid of
    // matched pairs of quotes/double-quotes.
    static String trimQuotes(String s) {
        if ((s.startsWith('"') && s.endsWith('"')) ||
            (s.startsWith("'") && s.endsWith("'"))) {
            return trimQuotes(s[1..-2])
        } else {
            return s
        }
    }


    static Predicate<FlowNode> endNodeForStage(final StepStartNode startNode) {
        return new Predicate<FlowNode>() {
            @Override
            boolean apply(@Nullable FlowNode input) {
                return input != null &&
                    input instanceof StepEndNode &&
                    input.getStartNode().equals(startNode)
            }
        }
    }

    static Predicate<FlowNode> isStageWithOptionalName(final String stageName = null) {
        return new Predicate<FlowNode>() {
            @Override
            boolean apply(@Nullable FlowNode input) {
                return input != null &&
                    input instanceof StepStartNode &&
                    ((StepStartNode) input).descriptor instanceof StageStep.DescriptorImpl &&
                    (stageName == null || input.displayName?.equals(stageName))
            }
        }
    }

    public static String stringToSHA1(String s) {
        return DigestUtils.sha1Hex(s)
    }

    /**
     * Prints a log message to the Jenkins log, bypassing the echo step.
     * @param s The message to log
     */
    public static void logToTaskListener(String s) {
        CpsThread thread = CpsThread.current()
        CpsFlowExecution execution = thread.execution

        execution?.getOwner()?.getListener()?.getLogger()?.println(s)
    }

    /**
     * Returns true if we're currently children under a stage.
     *
     * @return true if we're in a stage and false otherwise
     */
    static boolean withinAStage() {
        CpsThread thread = CpsThread.current()
        CpsFlowExecution execution = thread.execution

        LinearBlockHoppingScanner scanner = new LinearBlockHoppingScanner();

        FlowNode stageNode = execution.currentHeads.find { h ->
            scanner.findFirstMatch(h, isStageWithOptionalName())
        }

        return stageNode != null
    }

    private static FlowNode findStageFlowNode(String stageName) {
        CpsThread thread = CpsThread.current()
        CpsFlowExecution execution = thread.execution

        LinearBlockHoppingScanner scanner = new LinearBlockHoppingScanner();

        return scanner.findFirstMatch(execution.currentHeads, null, isStageWithOptionalName(stageName))
    }

    private static void markStageWithTag(String stageName, String tagName, String tagValue) {
        FlowNode currentNode = findStageFlowNode(stageName)

        if (currentNode != null) {
            TagsAction tagsAction = currentNode.getAction(TagsAction.class)
            if (tagsAction == null) {
                tagsAction = new TagsAction()
                tagsAction.addTag(tagName, tagValue)
                currentNode.addAction(tagsAction)
            } else if (tagsAction.getTagValue(tagName) == null) {
                tagsAction.addTag(tagName, tagValue)
                currentNode.save()
            }
        }
    }

    static <T extends StageTagsMetadata> T getTagMetadata(Class<T> c) {
        return ExtensionList.lookup(StageTagsMetadata.class).get(c)
    }

    static StageStatus getStageStatusMetadata() {
        return getTagMetadata(StageStatus.class)
    }

    static SyntheticStage getSyntheticStageMetadata() {
        return getTagMetadata(SyntheticStage.class)
    }

    static void markStageFailedAndContinued(String stageName) {
        markStageWithTag(stageName, getStageStatusMetadata().tagName, getStageStatusMetadata().failedAndContinued)
    }

    static void markStageSkippedForFailure(String stageName) {
        markStageWithTag(stageName, getStageStatusMetadata().tagName, getStageStatusMetadata().skippedForFailure)
    }

    static void markStageSkippedForUnstable(String stageName) {
        markStageWithTag(stageName, getStageStatusMetadata().tagName, getStageStatusMetadata().skippedForFailure)
    }

    static void markStageSkippedForConditional(String stageName) {
        markStageWithTag(stageName, getStageStatusMetadata().tagName, getStageStatusMetadata().skippedForConditional)
    }

    /**
     * Creates and sets the loading for a cache of {@link Describable}s descending from the given descriptor type.
     *
     * @param type The {@link Descriptor} class whose extensions we want to find.
     * @param includeClassNames Optionally include class names as keys. Defaults to false.
     * @param excludedSymbols Optional list of symbol names to exclude from the cache.
     * @return A {@link LoadingCache} for looking up types from symbols.
     */
    static generateTypeCache(Class<? extends Descriptor> type, boolean includeClassNames = false,
                             List<String> excludedSymbols = [], Closure<Boolean> filter = null) {
        return CacheBuilder.newBuilder()
            .expireAfterWrite(10, TimeUnit.MINUTES)
            .build(new CacheLoader<Object, Map<String, String>>() {
            @Override
            Map<String, String> load(Object key) throws Exception {
                return populateTypeCache(type, includeClassNames, excludedSymbols, filter)
            }
        })
    }

    /**
     * Actually populates the type cache.
     *
     * @param type The {@link Descriptor} class whose extensions we want to find.
     * @param includeClassNames Optionally include class names as keys. Defaults to false.
     * @param excludedSymbols Optional list of symbol names to exclude from the cache.
     * @return A map of symbols or class names to class names.
     */
    private static Map<String,String> populateTypeCache(Class<? extends Descriptor> type,
                                                        boolean includeClassNames = false,
                                                        List<String> excludedSymbols = [],
                                                        Closure<Boolean> filter = null) {
        Map<String,String> knownTypes = [:]

        ExtensionList.lookup(type).each { t ->
            if (filter == null || filter.call(t)) {
                // Have to special-case StepDescriptor since it doesn't actually have symbols!
                if (t instanceof StepDescriptor) {
                    knownTypes.put(t.functionName, t.clazz.getName())
                } else {
                    Set<String> symbolValue = SymbolLookup.getSymbolValue(t)
                    if (!symbolValue.isEmpty() && !symbolValue.any { excludedSymbols.contains(it) }) {
                        knownTypes.put(symbolValue.iterator().next(), t.clazz.getName())
                    }
                }

                if (includeClassNames) {
                    // Add the class name mapping even if we also found the symbol, for backwards compatibility reasons.
                    knownTypes.put(t.clazz.getName(), t.clazz.getName())
                }
            }
        }

        return knownTypes
    }

    @Whitelisted
    static <T> T instantiateDescribable(Class<T> c, Map<String, ?> args) {
        DescribableModel<T> model = new DescribableModel<>(c)
        return model?.instantiate(args)
    }

    /**
     * Determines whether a given {@link UninstantiatedDescribable} is of a given type.
     *
     * @param ud The {@link UninstantiatedDescribable} to check
     * @param base The {@link Class}
     * @return True if the uninstantiated describable is of the type given
     */
    public static boolean isOfType(UninstantiatedDescribable ud, Class<?> base) {
        Descriptor d = SymbolLookup.get().findDescriptor(base, ud.symbol)
        return d != null
    }

    /**
     * @param c The closure to wrap.
     */
    @Whitelisted
    public static StepsBlock createStepsBlock(Closure c) {
        // Jumping through weird hoops to get around the ejection for cases of JENKINS-26481.
        StepsBlock wrapper = new StepsBlock()
        wrapper.setClosure(c)

        return wrapper
    }

    public static boolean validEnvIdentifier(String i) {
        if (!SourceVersion.isIdentifier(i)) {
            return false
        } else if (!i.matches("[a-zA-Z_]+[a-zA-Z0-9_]*")) {
            return false
        }
        return true
    }

    static CpsFlowExecution getExecutionForRun(WorkflowRun run) {
        FlowExecutionOwner owner = ((FlowExecutionOwner.Executable) run).asFlowExecutionOwner()
        if (owner == null) {
            return null
        }
        FlowExecution exec = owner.getOrNull()
        return exec instanceof CpsFlowExecution ? (CpsFlowExecution) exec : null
    }

    /**
     * Shortcut for determining whether we've got a {@link DeclarativeStageConditionalDescriptor} for a given name.
     * @param name
     * @return True if found, false otherwise
     */
    public static boolean whenConditionDescriptorFound(String name) {
        return DeclarativeStageConditionalDescriptor.byName(name) != null
    }

    /**
     * Whether a given name has a {@link DeclarativeStageConditionalDescriptor} that takes children conditions.
     * @param name
     * @return True if there is a descriptor with that name and it takes children conditions.
     */
    public static boolean nestedWhenCondition(String name) {
        return StageConditionals.nestedConditionals.containsKey(name)
    }

    /**
     * Whether a given name has a {@link DeclarativeStageConditionalDescriptor} that takes multiple children conditions
     * @param name
     * @return True if there is a descriptor with that name and it takes multiple children conditions.
     */
    public static boolean takesWhenConditionList(String name) {
        return StageConditionals.multipleNestedConditionals.containsKey(name)
    }

    /**
     * Find and create an {@link UninstantiatedDescribable} from a symbol name and arguments.
     * The arguments are assumed to be packaged as Groovy does to {@code invokeMethod} et.al.
     * And to be either a single argument or named arguments, and not taking a closure.
     *
     * @param symbol the {@code @Symbol} name
     * @param baseClazz the base class the describable is supposed to inherit
     * @param _args the arguments packaged as described above
     * @return an UninstantiatedDescribable ready to be instantiated or {@code null} if the descriptor could not be found
     */
    public static UninstantiatedDescribable getDescribable(String symbol, Class<? extends Describable> baseClazz, Object _args) {
        def descriptor = SymbolLookup.get().findDescriptor(baseClazz, symbol)
        if (descriptor != null) {
            //Lots copied from org.jenkinsci.plugins.workflow.cps.DSL.invokeDescribable

            Map<String, ?> args = unPackageArgs(_args)
            return new UninstantiatedDescribable(symbol, descriptor.clazz.name, args)
        }
        return null
    }

    /**
     * Unpacks the arguments for {@link  #getDescribable(java.lang.String, java.lang.Class, java.lang.Object)}.
     *
     * @param _args the arguments
     * @return the unpacked version suitable to give to an {@link UninstantiatedDescribable}.
     * @see #getDescribable(java.lang.String, java.lang.Class, java.lang.Object)
     */
    static Map<String, ?> unPackageArgs(Object _args) {
        if(_args instanceof Object[]) {
            List a = Arrays.asList((Object[])_args);
            if (a.size()==0) {
                return Collections.emptyMap()
            }

            if (a.size()==1 && a.get(0) instanceof Map && !((Map)a.get(0)).containsKey('$class')) {
                return (Map) a.get(0)
            } else if (a.size() == 1 && !(a.get(0) instanceof Map)) {
                return Collections.singletonMap(UninstantiatedDescribable.ANONYMOUS_KEY, a.get(0))
            }
            throw new IllegalArgumentException("Expected named arguments but got "+a)
        } else {
            return Collections.singletonMap(UninstantiatedDescribable.ANONYMOUS_KEY, _args)
        }
    }

    /**
     * Get the appropriate result given an exception. If it's a {@link FlowInterruptedException}, return the result
     * on the exception, otherwise return {@link Result#FAILURE}.
     *
     * @param e The exception.
     * @return The result.
     */
    static Result getResultFromException(Exception e) {
        if (e instanceof FlowInterruptedException) {
            return ((FlowInterruptedException)e).result
        } else {
            return Result.FAILURE
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
    @Nonnull
    private static <T extends Describable> List<T> instantiateList(Class<T> clazz, List<Object> toInstantiate) {
        List<T> l = []
        toInstantiate.each { t ->
            if (t instanceof UninstantiatedDescribable) {
                l.add((T) t.instantiate(clazz))
            } else {
                l.add((T)t)
            }
        }

        return l
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
     * @param script
     */
    static void updateJobProperties(@CheckForNull List<Object> propsOrUninstantiated,
                                    @CheckForNull List<Object> trigsOrUninstantiated,
                                    @CheckForNull List<Object> paramsOrUninstantiated,
                                    @Nonnull CpsScript script) {
        List<JobProperty> rawJobProperties = instantiateList(JobProperty.class, propsOrUninstantiated)
        List<Trigger> rawTriggers = instantiateList(Trigger.class, trigsOrUninstantiated)
        List<ParameterDefinition> rawParameters = instantiateList(ParameterDefinition.class, paramsOrUninstantiated)

        WorkflowRun r = script.$build()
        WorkflowJob j = r.getParent()

        List<JobProperty> existingJobProperties = existingJobPropertiesForJob(j)
        List<Trigger> existingTriggers = existingTriggersForJob(j)
        List<ParameterDefinition> existingParameters = existingParametersForJob(j)

        Set<String> previousProperties = new HashSet<>()
        Set<String> previousTriggers = new HashSet<>()
        Set<String> previousParameters = new HashSet<>()

        // First, use the action from the job if it's present.
        DeclarativeJobPropertyTrackerAction previousAction = j.getAction(DeclarativeJobPropertyTrackerAction.class)

        // Fall back to previous build for compatibility reasons.
        if (previousAction == null) {
            WorkflowRun previousBuild = r.getPreviousCompletedBuild()
            if (previousBuild != null) {
                previousAction = previousBuild.getAction(DeclarativeJobPropertyTrackerAction.class)
            }
        }
        if (previousAction != null) {
            previousProperties.addAll(previousAction.getJobProperties())
            previousTriggers.addAll(previousAction.getTriggers())
            previousParameters.addAll(previousAction.getParameters())
        }

        List<JobProperty> jobPropertiesToApply = []
        Set<String> seenClasses = new HashSet<>()
        if (rawJobProperties != null) {
            jobPropertiesToApply.addAll(rawJobProperties)
            seenClasses.addAll(rawJobProperties.collect { it.descriptor.id })
        }
        // Find all existing job properties that aren't of classes we've explicitly defined, *and* aren't
        // in the set of classes of job properties defined by the Jenkinsfile in the previous build. Add those too.
        // Oh, and ignore the PipelineTriggersJobProperty and ParameterDefinitionsProperty - we handle those separately.
        // And stash the property classes that should be removed aside as well.
        List<JobProperty> propsToRemove = []
        existingJobProperties.each { p ->
            // We only care about classes that we haven't already seen in the new properties list.
            if (!(p.descriptor.id in seenClasses)) {
                if (!(p.descriptor.id in previousProperties)) {
                    // This means it's a job property defined outside of our scope, so retain it, if it's the first
                    // instance of the class that we've seen so far. Ideally we'd be ignoring it completely, but due to
                    // JENKINS-44809, we've created situations where tons of duplicate job property instances exist,
                    // which need to be nuked, so go through normal cleanup.
                    if (!jobPropertiesToApply.any { p.descriptor == it.descriptor }) {
                        jobPropertiesToApply.add(p)
                    }
                } else {
                    // This means we should be removing it - it was defined via the Jenkinsfile last time but is no
                    // longer defined.
                    propsToRemove.add(p)
                }
            }
        }

        List<Trigger> triggersToApply = getTriggersToApply(rawTriggers, existingTriggers, previousTriggers)
        List<ParameterDefinition> parametersToApply = getParametersToApply(rawParameters, existingParameters, previousParameters)

        BulkChange bc = new BulkChange(j)
        try {
            // Remove the triggers/parameters properties regardless.
            j.removeProperty(PipelineTriggersJobProperty.class)
            j.removeProperty(ParametersDefinitionProperty.class)

            // Remove the job properties we defined in previous Jenkinsfiles but don't any more.
            propsToRemove.each { j.removeProperty(it) }

            // If there are any triggers and if there are any parameters, add those properties.
            if (!triggersToApply.isEmpty()) {
                j.addProperty(new PipelineTriggersJobProperty(triggersToApply))
            }
            if (!parametersToApply.isEmpty()) {
                j.addProperty(new ParametersDefinitionProperty(parametersToApply))
            }

            // Now add all the other job properties we know need to be added.
            jobPropertiesToApply.each { p ->
                // Remove the existing instance(s) of the property class before we add the new one. We're looping and
                // removing multiple to deal with the results of JENKINS-44809.
                while (j.getProperty(p.class) != null) {
                    j.removeProperty(p.class)
                }
                j.addProperty(p)
            }

            bc.commit();
            // Add the action tracking what we added (or empty otherwise)
            j.replaceAction(new DeclarativeJobPropertyTrackerAction(rawJobProperties, rawTriggers, rawParameters))
        } finally {
            bc.abort();
        }
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
    @Nonnull
    private static List<Trigger> getTriggersToApply(@CheckForNull List<Trigger> newTriggers,
                                                    @Nonnull List<Trigger> existingTriggers,
                                                    @Nonnull Set<String> prevDefined) {
        Set<String> seenTriggerClasses = new HashSet<>()
        List<Trigger> toApply = []
        if (newTriggers != null) {
            toApply.addAll(newTriggers)
            seenTriggerClasses.addAll(newTriggers.collect { it.descriptor.id })
        }

        // Find all existing triggers that aren't of classes we've explicitly defined, *and* aren't
        // in the set of classes of triggers defined by the Jenkinsfile in the previous build. Add those too.
        toApply.addAll(existingTriggers.findAll {
            !(it.descriptor.id in seenTriggerClasses) && !(it.descriptor.id in prevDefined)
        })

        return toApply
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
    @Nonnull
    private static List<ParameterDefinition> getParametersToApply(@CheckForNull List<ParameterDefinition> newParameters,
                                                                  @Nonnull List<ParameterDefinition> existingParameters,
                                                                  @Nonnull Set<String> prevDefined) {
        Set<String> seenNames = new HashSet<>()
        List<ParameterDefinition> toApply = []
        if (newParameters != null) {
            toApply.addAll(newParameters)
            seenNames.addAll(newParameters.collect { it.name })
        }
        // Find all existing parameters that aren't of names we've explicitly defined, *and* aren't
        // in the set of names of parameters defined by the Jenkinsfile in the previous build. Add those too.
        toApply.addAll(existingParameters.findAll {
            !(it.name in seenNames) && !(it.name in prevDefined)
        })

        return toApply
    }

    /**
     * Helper method for getting the appropriate {@link JobProperty}s from a job.
     *
     * @param j a job
     * @return A list of all {@link JobProperty}s on the given job, other than ones specifically excluded because we're
     * handling them elsewhere. May be empty.
     */
    @Nonnull
    private static List<JobProperty> existingJobPropertiesForJob(@Nonnull WorkflowJob j) {
        List<JobProperty> existing = []
        existing.addAll(j.getAllProperties().findAll {
            !(it instanceof PipelineTriggersJobProperty) && !(it instanceof ParametersDefinitionProperty)
        })

        return existing
    }

    /**
     * Helper method for getting all {@link Trigger}s on a job.
     *
     * @param j a job
     * @return A list of all {@link Trigger}s defined in the job's {@link PipelineTriggersJobProperty}. May be empty.
     */
    @Nonnull
    private static List<Trigger> existingTriggersForJob(@Nonnull WorkflowJob j) {
        List<Trigger> existing = []
        if (j.getProperty(PipelineTriggersJobProperty.class) != null) {
            existing.addAll(j.getProperty(PipelineTriggersJobProperty.class)?.getTriggers())
        }
        return existing
    }

    /**
     * Helper method for getting all {@link ParameterDefinition}s on a job.
     *
     * @param j a job
     * @return A list of all {@link ParameterDefinition}s defined in the job's {@link ParametersDefinitionProperty}. May
     * be empty.
     */
    @Nonnull
    private static List<ParameterDefinition> existingParametersForJob(@Nonnull WorkflowJob j) {
        List<ParameterDefinition> existing = []
        if (j.getProperty(ParametersDefinitionProperty.class) != null) {
            existing.addAll(j.getProperty(ParametersDefinitionProperty.class)?.getParameterDefinitions())
        }
        return existing
    }


    /**
     * Obtains the source text of the given {@link org.codehaus.groovy.ast.ASTNode}.
     */
    public static String getSourceTextForASTNode(@Nonnull ASTNode n, @Nonnull SourceUnit sourceUnit) {
        def result = new StringBuilder();
        int beginLine = n.getLineNumber()
        int endLine = n.getLastLineNumber()
        int beginLineColumn = n.getColumnNumber()
        int endLineLastColumn = n.getLastColumnNumber()

        //The node seems to be lying about the last line, so go through each statement to try to make sure
        if (n instanceof BlockStatement) {
            for (Statement s : n.statements) {
                if (s.lineNumber < beginLine) {
                    beginLine = s.lineNumber
                    beginLineColumn = s.columnNumber
                }
                if (s.lastLineNumber > endLine) {
                    endLine = s.lastLineNumber
                    endLineLastColumn = s.lastColumnNumber
                }
            }
        }
        for (int x = beginLine; x <= endLine; x++) {
            String line = sourceUnit.source.getLine(x, null);
            if (line == null)
                throw new AssertionError("Unable to get source line"+x);

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
}
