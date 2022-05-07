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
package org.jenkinsci.plugins.pipeline.modeldefinition.validator

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import hudson.model.Describable
import hudson.model.Descriptor
import hudson.model.PasswordParameterDefinition
import hudson.tools.ToolDescriptor
import hudson.tools.ToolInstallation
import hudson.util.EditDistance
import jenkins.model.Jenkins
import org.codehaus.groovy.control.CompilationUnit
import org.codehaus.groovy.control.MultipleCompilationErrorsException
import org.codehaus.groovy.control.Phases
import org.codehaus.groovy.runtime.ScriptBytecodeAdapter
import org.jenkinsci.plugins.pipeline.modeldefinition.DescriptorLookupCache
import org.jenkinsci.plugins.pipeline.modeldefinition.Messages
import org.jenkinsci.plugins.pipeline.modeldefinition.Utils
import org.jenkinsci.plugins.pipeline.modeldefinition.agent.DeclarativeAgentDescriptor
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.*
import org.jenkinsci.plugins.pipeline.modeldefinition.model.*
import org.jenkinsci.plugins.pipeline.modeldefinition.when.DeclarativeStageConditional
import org.jenkinsci.plugins.pipeline.modeldefinition.when.DeclarativeStageConditionalDescriptor
import org.jenkinsci.plugins.structs.SymbolLookup
import org.jenkinsci.plugins.structs.describable.DescribableModel
import org.jenkinsci.plugins.structs.describable.DescribableParameter
import org.jenkinsci.plugins.workflow.flow.FlowExecution

import edu.umd.cs.findbugs.annotations.NonNull

/**
 * Class for validating various AST elements. Contains the error collector as well as caches for steps, models, etc.
 *
 * @author Andrew Bayer
 */
@ToString
@EqualsAndHashCode
@SuppressFBWarnings(value = "SE_NO_SERIALVERSIONID")
class ModelValidatorImpl implements ModelValidator {

    private final ErrorCollector errorCollector
    private final List<Class<? extends DeclarativeValidatorContributor>> enabledOptionalValidators = new ArrayList<>()
    private transient DescriptorLookupCache lookup
    private transient FlowExecution execution
    private transient List<DeclarativeValidatorContributor> validatorContributors

    ModelValidatorImpl(@NonNull ErrorCollector e) {
        this(e, [], null)
    }

    ModelValidatorImpl(@NonNull ErrorCollector e, FlowExecution execution) {
        this(e, [], execution)
    }

    ModelValidatorImpl(@NonNull ErrorCollector e,
                       @NonNull List<Class<? extends DeclarativeValidatorContributor>> enabledOptionalValidators,
                       FlowExecution execution = null) {
        this.errorCollector = e
        this.enabledOptionalValidators.addAll(enabledOptionalValidators)
        this.execution = execution
        this.lookup = DescriptorLookupCache.getPublicCache()
    }

    private List<DeclarativeValidatorContributor> getContributors() {
        if (validatorContributors == null) {
            validatorContributors = DeclarativeValidatorContributor.all().findAll { c ->
                !c.isOptional() || c.class in enabledOptionalValidators
            }
        }

        return validatorContributors
    }

    DescriptorLookupCache getLookup() {
        return lookup
    }

    private FlowExecution getExecution() {
        return execution
    }

    boolean validateElement(@NonNull ModelASTPostBuild postBuild) {
        // post specific validation
        return validateFromContributors(postBuild, true)
    }

    boolean validateElement(@NonNull ModelASTPostStage post) {
        // post stage specific validation
        return validateFromContributors(post, true)
    }

    boolean validateElement(@NonNull ModelASTBuildConditionsContainer post) {
        boolean valid = true

        if (post.conditions.isEmpty()) {
            errorCollector.error(post, Messages.ModelValidatorImpl_EmptySection(post.getName()))
            valid = false
        }

        def conditionNames = post.conditions.collect { c ->
            c.condition
        }

        conditionNames.findAll { conditionNames.count(it) > 1 }.unique().each { sn ->
            errorCollector.error(post, Messages.ModelValidatorImpl_DuplicateBuildCondition(sn))
            valid = false
        }

        return valid
    }

    boolean validateElement(@NonNull ModelASTBuildCondition buildCondition) {
        boolean valid = true

        // Only do the symbol lookup if we have a Jenkins instance and condition/branch aren't null. That only happens
        // when there's a failure at parse-time.
        if (Jenkins.getInstanceOrNull() != null && buildCondition.condition != null && buildCondition.branch != null) {
            if (SymbolLookup.get().find(BuildCondition.class, buildCondition.condition) == null) {
                errorCollector.error(buildCondition,
                    Messages.ModelValidatorImpl_InvalidBuildCondition(buildCondition.condition, BuildCondition.getOrderedConditionNames()))
                valid = false
            }
        }

        return validateFromContributors(buildCondition, valid)
    }

    boolean validateElement(@NonNull ModelASTEnvironment env) {
        boolean valid = true

        if (env.variables.isEmpty()) {
            errorCollector.error(env, Messages.ModelValidatorImpl_NoEnvVars())
            valid = false
        }
        env.variables.each { k, v ->
            if (!Utils.validEnvIdentifier(k.key)) {
                errorCollector.error(k, Messages.ModelValidatorImpl_InvalidIdentifierInEnv(k.key))
                valid = false
            }
        }

        return validateFromContributors(env, valid)
    }

    boolean validateElement(@NonNull ModelASTInternalFunctionCall call) {
        // TODO: Make this real validation when JENKINS-41759 lands
        return validateFromContributors(call, true)
    }

    boolean validateElement(@NonNull ModelASTTools t) {
        boolean valid = true

        if (t.tools.isEmpty()) {
            errorCollector.error(t, Messages.ModelValidatorImpl_NoTools())
            valid = false
        }

        t.tools.each { k, v ->
            if (Tools.typeForKey(k.key) == null) {
                errorCollector.error(k, Messages.ModelValidatorImpl_InvalidSectionType("tool", k.key, Tools.getAllowedToolTypes().keySet()))
                valid = false
            } else {
                // Don't bother checking whether the tool exists in this Jenkins controller if we know it isn't an allowed tool type.

                // Can't do tools validation without a Jenkins instance, so move on if that's not available, or if the tool value is a
                // non-literal - we allow users to shoot themselves there.
                if (Jenkins.getInstanceOrNull() != null && v.isLiteral()) {
                    // Not bothering with a null check here since we could only get this far if the ToolDescriptor's available in the first place.
                    ToolDescriptor desc = ToolInstallation.all().find { it.getId() == Tools.typeForKey(k.key) }
                    def installer = desc.getInstallations().find { it.name == (String) v.value }
                    if (installer == null) {
                        String possible = EditDistance.findNearest((String) v.value, desc.getInstallations().collect { it.name })
                        errorCollector.error(v, Messages.ModelValidatorImpl_NoToolVersion(k.key, v.value, possible))
                        valid = false
                    }
                }
            }
        }

        return validateFromContributors(t, valid)
    }

    boolean validateElement(ModelASTWhen when) {
        boolean valid = true
        if (when.conditions.isEmpty()) {
            errorCollector.error(when, Messages.ModelValidatorImpl_EmptyWhen())
            valid = false
        }

        return validateFromContributors(when, valid)
    }

    boolean validateElement(ModelASTLibraries libraries) {
        boolean valid = true

        if (libraries.libs.isEmpty()) {
            errorCollector.error(libraries, Messages.ModelValidatorImpl_EmptySection("libraries"))
            valid = false
        } else {
            libraries.libs.each { l ->
                // TODO: Decide what validation, if any, we want to do for library identifiers.
            }
        }

        return validateFromContributors(libraries, valid)
    }

    boolean validateElement(ModelASTWhenCondition condition) {
        boolean valid = true
        def allNames = DeclarativeStageConditionalDescriptor.allNames()

        // Short-circuit in cases where the condition didn't parse right in the first place.
        if (condition?.name == null) {
            return false
        }
        
        if (!(condition.name in allNames)) {
            errorCollector.error(condition, Messages.ModelValidatorImpl_UnknownWhenConditional(condition.name, allNames.join(", ")))
            valid = false
        } else {
            DescribableModel<? extends DeclarativeStageConditional> model =
                DeclarativeStageConditionalDescriptor.describableModels.get(condition.name)

            DeclarativeStageConditionalDescriptor desc = DeclarativeStageConditionalDescriptor.byName(condition.name)

            if (desc.getAllowedChildrenCount() != 0) {
                if (condition.args != null) {
                    errorCollector.error(condition, Messages.ModelValidatorImpl_NestedWhenNoArgs(condition.name))
                    valid = false
                } else if (desc.getAllowedChildrenCount() < 0) {
                    if (condition.children.isEmpty()) {
                        errorCollector.error(condition, Messages.ModelValidatorImpl_NestedWhenWithoutChildren(condition.name))
                        valid = false
                    }
                } else if (condition.children.size() != desc.getAllowedChildrenCount()) {
                    errorCollector.error(condition, Messages.ModelValidatorImpl_NestedWhenWrongChildrenCount(condition.name, desc.getAllowedChildrenCount()))
                    valid = false
                } else {
                    condition.children.each { c ->
                        if (!c.validate(this)) {
                            valid = false
                        }
                    }
                }
            } else {
                if (!condition.children.isEmpty()) {
                    errorCollector.error(condition, Messages.ModelValidatorImpl_NoNestedWhenAllowed(condition.name))
                    valid = false
                } else {
                    valid = validateDescribable(condition, condition.name, condition.args, model, false)
                }
            }
        }

        return validateFromContributors(condition, valid)
    }

    private boolean isValidStepParameter(DescribableModel<? extends Describable> model,
                                         String key,
                                         ModelASTElement keyElement) {
        def p = model?.getParameter(key)
        if (p == null) {
            String possible = EditDistance.findNearest(key, model.getParameters().collect {
                it.name
            })
            errorCollector.error(keyElement, Messages.ModelValidatorImpl_InvalidStepParameter(key, possible))
            return false
        }
        return true
    }

    @SuppressFBWarnings(value = "UPM_UNCALLED_PRIVATE_METHOD")
    private boolean validateDescribable(ModelASTElement element, String name,
                                        ModelASTArgumentList args,
                                        DescribableModel<? extends Describable> model,
                                        boolean takesClosure = false) {
        boolean valid = true

        if (args instanceof ModelASTNamedArgumentList) {
            ModelASTNamedArgumentList argList = (ModelASTNamedArgumentList) args

            boolean soleDescribableMap = false

            argList.arguments.each { k, v ->
                // Check if there is a sole required parameter and it's describable
                if (model.getParameter(k.key) == null &&
                    model?.soleRequiredParameter != null &&
                    Describable.class.isAssignableFrom(model.soleRequiredParameter.erasedType)) {
                    // Check if the argument list validates as that describable. If it does, note that so
                    // we can proceed.
                    soleDescribableMap = true
                    valid = validateDescribable(element, model.soleRequiredParameter.name, argList,
                        new DescribableModel<>(model.soleRequiredParameter.erasedType))
                    // Note - this return is to break out of the .each loop only
                    return
                }

                if (!isValidStepParameter(model, k.key, k)) {
                    valid = false
                    // Note - this return is to break out of the .each loop only
                    return
                }

                def p = model.getParameter(k.key)

                ModelASTKey validateKey = k

                // Check if this is the only required parameter and if so, validate it without the key.
                if (argList.getArguments().size() == 1) {
                    // If we can lookup the model for this step or function...
                    if (model != null &&
                        model.soleRequiredParameter != null &&
                        model.soleRequiredParameter == p &&
                        !takesClosure) {
                        validateKey = null
                    }
                }

                if (!validateParameterType(v, p.erasedType, validateKey)) {
                    valid = false
                }
            }
            // Only check for required parameters if we're valid up to this point and we haven't already processed
            // a sole describable map
            if (valid && !soleDescribableMap) {
                model.parameters.each { p ->
                    if (p.isRequired() && !argList.containsKeyName(p.name)) {
                        errorCollector.error(element, Messages.ModelValidatorImpl_MissingRequiredStepParameter(p.name))
                        valid = false
                    }
                }
            }
        } else if (args instanceof ModelASTPositionalArgumentList) {
            errorCollector.error(element, Messages.ModelValidatorImpl_TooManyUnnamedParameters(name))
            valid = false
        } else {
            assert args instanceof ModelASTSingleArgument
            ModelASTSingleArgument arg = (ModelASTSingleArgument) args

            def p = model.soleRequiredParameter
            if (p == null && !takesClosure) {
                errorCollector.error(element, Messages.ModelValidatorImpl_NotSingleRequiredParameter())
                valid = false
            } else {
                Class erasedType = p?.erasedType
                def v = arg.value

                if (!validateParameterType(v, erasedType)) {
                    valid = false
                }
            }

        }

        return valid
    }

    private boolean validateStep(ModelASTStep step, DescribableModel<? extends Describable> model, Descriptor desc) {

        if (step instanceof AbstractModelASTCodeBlock) {
            // Verify that the code block can be parsed - we'll still get garbage for errors around class imports, etc,
            // but you can't do that from the editor anyway.
            String codeBlock = step.codeBlockAsString()
            CompilationUnit cu = new CompilationUnit()
            cu.addSource(step.name, codeBlock)
            try {
                cu.compile(Phases.PARSING)
            } catch (MultipleCompilationErrorsException e) {
                int errCnt = e.getErrorCollector().getErrorCount()
                List<String> compErrors = []
                for (int i = 0; i < errCnt; i++) {
                    compErrors.add(e.getErrorCollector().getSyntaxError(i).getOriginalMessage())
                }
                errorCollector.error(step, Messages.ModelValidatorImpl_CompilationErrorInCodeBlock(step.name, compErrors.join(", ")))
                return false
            }
            return true
        } else {
            return validateDescribable(step, step.name, step.args, model, lookup.stepTakesClosure(desc))
        }
    }

    boolean validateElement(@NonNull ModelASTStep step) {
        boolean valid = true

        // We can't do step validation without a Jenkins instance, so move on.
        // Also, special casing of parallel due to it not having a DataBoundConstructor.
        if (Jenkins.getInstanceOrNull() != null && step.name != "parallel") {
            Descriptor desc = lookup.lookupStepFirstThenFunction(step.name)
            DescribableModel<? extends Describable> model = lookup.modelForStepFirstThenFunction(step.name)

            if (model != null || step instanceof AbstractModelASTCodeBlock) {
                valid = validateStep(step, model, desc)
            }
        }

        return validateFromContributors(step, valid)
    }

    boolean validateElement(@NonNull ModelASTMethodCall meth) {
        boolean valid = true

        if (Jenkins.getInstanceOrNull() != null) {
            DescribableModel<? extends Describable> model

            List<Class<? extends Describable>> parentDescribables = Utils.parentsForMethodCall(meth)

            if (!parentDescribables.isEmpty()) {
                model = parentDescribables.collect { p ->
                    Descriptor fromParent = lookup.lookupFunctionFirstThenStep(meth.name, p)
                    if (fromParent != null) {
                        def m = lookup.modelForFunctionFirstThenStep(meth.name, p)
                        return m
                    } else {
                        return null
                    }
                }.find { it != null }
            } else {
                Descriptor desc = lookup.lookupFunctionFirstThenStep(meth.name)

                if (desc != null) {
                    model = lookup.modelForFunctionFirstThenStep(meth.name)
                }
            }

            if (model != null) {
                if (meth.args.any { it instanceof ModelASTKeyValueOrMethodCallPair }) {
                    meth.args.each { a ->
                        if (!(a instanceof ModelASTKeyValueOrMethodCallPair)) {
                            errorCollector.error(meth, Messages.ModelValidatorImpl_MixedNamedAndUnnamedParameters())
                            return
                        }
                        ModelASTKeyValueOrMethodCallPair kvm = (ModelASTKeyValueOrMethodCallPair) a

                        // Special case, see JENKINS-63499.
                        if (model.type == PasswordParameterDefinition.class && "defaultValue".equals(kvm.key.key)) {
                            if (!validateParameterType((ModelASTValue) kvm.value, String.class, kvm.key)) {
                                valid = false
                            }
                            return
                        }

                        if (!isValidStepParameter(model, kvm.key.key, kvm.key)) {
                            valid = false
                            return
                        }

                        def p = model.getParameter(kvm.key.key)

                        if (kvm.value instanceof ModelASTMethodCall) {
                            valid = validateElement((ModelASTMethodCall) kvm.value)
                        } else {
                            if (!validateParameterType((ModelASTValue) kvm.value, p.erasedType, kvm.key)) {
                                valid = false
                            }
                        }
                    }
                } else if (meth.args.size() > 1) {
                    errorCollector.error(meth, Messages.ModelValidatorImpl_TooManyUnnamedParameters(meth.name))
                    valid = false
                } else {
                    // TODO: Rewrite this to just handle the single argument case.
                    List<DescribableParameter> requiredParams = model.parameters.findAll { it.isRequired() }

                    if (requiredParams.size() != meth.args.size()) {
                        // NOTE: This is a specialized hack for allowing single-required-Boolean-parameter constructors
                        // to be called like "foo()", Groovy-style, passing null as the parameter value. Added for
                        // JENKINS-41391, may need to be revisited in the future.
                        if (!(requiredParams.size() == 1 &&
                            meth.args.isEmpty() &&
                            requiredParams.get(0).erasedType == Boolean.class)) {
                            errorCollector.error(meth, Messages.ModelValidatorImpl_WrongNumberOfStepParameters(meth.name, requiredParams.size(), meth.args.size()))
                            valid = false
                        }
                    } else {
                        requiredParams.eachWithIndex { DescribableParameter entry, int i ->
                            def argVal = meth.args.get(i)
                            if (argVal instanceof ModelASTMethodCall) {
                                valid = validateElement((ModelASTMethodCall) argVal)
                            } else {
                                if (!validateParameterType((ModelASTValue) argVal, entry.erasedType)) {
                                    valid = false
                                }
                            }
                        }
                    }
                }
            }
        }
        if (meth.class == ModelASTMethodCall.class) {
            return validateFromContributors(meth, valid)
        } else {
            return valid
        }
    }

    boolean validateElement(@NonNull ModelASTOptions opts) {
        boolean valid = true
        if (opts.options.isEmpty()) {
            errorCollector.error(opts, Messages.ModelValidatorImpl_EmptySection("options"))
            valid = false
        } else {
            def optionNames = opts.options.collect { it.name }
            optionNames.findAll { optionNames.count(it) > 1 }.unique().each { bn ->
                errorCollector.error(opts, Messages.ModelValidatorImpl_DuplicateOptionName(bn))
                valid = false
            }
            // Validate that the option is allowed for its context.
            opts.options.findAll { it.name != null }.each { opt ->
                if (opts.inStage && StageOptions.typeForKey(opt.name) == null) {
                    errorCollector.error(opt,
                        Messages.ModelValidatorImpl_InvalidSectionType("option", opt.name,
                            StageOptions.getAllowedOptionTypes().keySet()))
                    valid = false
                } else if (Options.typeForKey(opt.name) == null) {
                    errorCollector.error(opt,
                        Messages.ModelValidatorImpl_InvalidSectionType("option", opt.name,
                            Options.getAllowedOptionTypes().keySet()))
                    valid = false
                }
            }
        }

        return validateFromContributors(opts, valid)
    }

    boolean validateElement(@NonNull ModelASTTrigger trig) {
        boolean valid = true
        if (trig.name == null) {
            // This means that we failed at compilation time so can move on.
        }
        // We can't do trigger validation without a Jenkins instance, so move on.
        else if (Triggers.typeForKey(trig.name) == null) {
            errorCollector.error(trig,
                Messages.ModelValidatorImpl_InvalidSectionType("trigger", trig.name, Triggers.getAllowedTriggerTypes().keySet()))
            valid = false
        } else if (trig.args.any { it instanceof ModelASTKeyValueOrMethodCallPair }
            && !trig.args.every { it instanceof ModelASTKeyValueOrMethodCallPair }) {
            errorCollector.error(trig, Messages.ModelValidatorImpl_MixedNamedAndUnnamedParameters())
            valid = false
        }
        return validateFromContributors(trig, valid)
    }

    boolean validateElement(@NonNull ModelASTTriggers triggers) {
        boolean valid = true
        if (triggers.triggers.isEmpty()) {
            errorCollector.error(triggers, Messages.ModelValidatorImpl_EmptySection("triggers"))
            valid = false
        } else {
            def triggerNames = triggers.triggers.collect { it.name }
            triggerNames.findAll { triggerNames.count(it) > 1 }.unique().each { bn ->
                errorCollector.error(triggers, Messages.ModelValidatorImpl_DuplicateTriggerName(bn))
                valid = false
            }
        }

        return validateFromContributors(triggers, valid)
    }

    boolean validateElement(@NonNull ModelASTBuildParameter param) {
        boolean valid = true

        if (param.name == null) {
            // Validation failed at compilation time - avoid redundant errors here.
        }
        // We can't do parameter validation without a Jenkins instance, so move on.
        else if (Parameters.typeForKey(param.name) == null) {
            errorCollector.error(param,
                Messages.ModelValidatorImpl_InvalidSectionType("parameter", param.name, Parameters.getAllowedParameterTypes().keySet()))
            valid = false
        } else if (param.args.any { it instanceof ModelASTKeyValueOrMethodCallPair }
            && !param.args.every { it instanceof ModelASTKeyValueOrMethodCallPair }) {
            errorCollector.error(param, Messages.ModelValidatorImpl_MixedNamedAndUnnamedParameters())
            valid = false
        }
        return validateFromContributors(param, valid)
    }

    boolean validateElement(@NonNull ModelASTBuildParameters params) {
        boolean valid = true
        if (params.parameters.isEmpty()) {
            errorCollector.error(params, Messages.ModelValidatorImpl_EmptySection("parameters"))
            valid = false
        }

        return validateFromContributors(params, valid)
    }

    boolean validateElement(@NonNull ModelASTStageInput input) {
        boolean valid = true
        if (input.message == null) {
            errorCollector.error(input, Messages.ModelValidatorImpl_MissingInputMessage())
            valid = false
        }

        return validateFromContributors(input, valid)
    }

    boolean validateElement(@NonNull ModelASTOption opt) {
        boolean valid = true

        if (opt.name == null) {
            // Validation failed at compilation time so move on.
        } else if (opt.args.any { it instanceof ModelASTKeyValueOrMethodCallPair }
            && !opt.args.every { it instanceof ModelASTKeyValueOrMethodCallPair }) {
            errorCollector.error(opt, Messages.ModelValidatorImpl_MixedNamedAndUnnamedParameters())
            valid = false
        }
        return validateFromContributors(opt, valid)
    }

    @SuppressFBWarnings(value = ["REC_CATCH_EXCEPTION", "UPM_UNCALLED_PRIVATE_METHOD"])
    private boolean validateParameterType(ModelASTValue v, Class erasedType, ModelASTKey k = null) {
        if (v.isLiteral()) {
            try {
                // Converting amongst boolean, string and int at runtime doesn't work, but does pass castToType. So.
                if ((erasedType == String.class && (v.value instanceof Integer || v.value instanceof Boolean)) ||
                    (erasedType == int.class && (v.value instanceof String || v.value instanceof Boolean))) {
                    throw new RuntimeException("Ignore")
                }
                ScriptBytecodeAdapter.castToType(v.value, erasedType)
            } catch (Exception e) {
                if (k != null) {
                    errorCollector.error(v, Messages.ModelValidatorImpl_InvalidParameterType(erasedType, k.key, v.value.toString(),
                        v.value.getClass()))
                } else {
                    errorCollector.error(v, Messages.ModelValidatorImpl_InvalidUnnamedParameterType(erasedType, v.value.toString(),
                    v.value.getClass()))
                }
                return false
            }
        }
        return true
    }

    boolean validateElement(@NonNull ModelASTBranch branch) {
        boolean valid = true

        if (branch.steps.isEmpty()) {
            errorCollector.error(branch, Messages.ModelValidatorImpl_NoSteps())
            valid = false
        }

        return validateFromContributors(branch, valid)
    }

    boolean validateElement(@NonNull ModelASTPipelineDef pipelineDef) {
        boolean valid = true

        if (pipelineDef.stages == null) {
            errorCollector.error(pipelineDef, Messages.ModelValidatorImpl_RequiredSection("stages"))
            valid = false
        }

        if (pipelineDef.agent == null) {
            errorCollector.error(pipelineDef, Messages.ModelValidatorImpl_RequiredSection("agent"))
        }
        return validateFromContributors(pipelineDef, valid)
    }

    boolean validateElement(ModelASTStageBase stage) {
        return true
    }

    boolean validateElement(@NonNull ModelASTStage stage, boolean isWithinParallel) {
        boolean valid = true
        def stepsStagesParallelCount = 0
        if (!stage.branches.isEmpty()) {
            stepsStagesParallelCount += 1
        }
        if (stage.parallel != null) {
            stepsStagesParallelCount += 1
        }
        if (stage.matrix != null) {
            stepsStagesParallelCount += 1
        }
        if (stage.stages != null) {
            stepsStagesParallelCount += 1
        }

        if (isWithinParallel && (stage.branches.size() > 1 || stage.parallel != null || stage.matrix != null)) {
            ModelASTElement errorElement
            if (stage.matrix != null) {
                errorElement = stage.matrix
            } else if (stage.parallel != null) {
                errorElement = stage.parallel
            } else {
                errorElement = stage.branches.first()
            }
            errorCollector.error(errorElement, Messages.ModelValidatorImpl_NoNestedWithinNestedStages())
            valid = false
        } else if (stepsStagesParallelCount > 1) {
            errorCollector.error(stage, Messages.ModelValidatorImpl_TwoOfStepsStagesParallel(stage.name))
            valid = false
        } else if (stepsStagesParallelCount == 0) {
            errorCollector.error(stage, Messages.ModelValidatorImpl_NothingForStage(stage.name))
            valid = false
        } else if (stage.parallel != null || stage.matrix != null) {
            if (stage.agent != null) {
                errorCollector.error(stage.agent, Messages.ModelValidatorImpl_AgentInNestedStages(stage.name))
                valid = false
            }
            if (stage.tools != null) {
                errorCollector.error(stage.tools, Messages.ModelValidatorImpl_ToolsInNestedStages(stage.name))
                valid = false
            }
        } else {
            def branchNames = stage.branches.collect { it.name }
            branchNames.findAll { branchNames.count(it) > 1 }.unique().each { bn ->
                errorCollector.error(stage, Messages.ModelValidatorImpl_DuplicateParallelName(bn))
                valid = false
            }
        }

        return validateFromContributors(stage, valid, isWithinParallel)
    }

    boolean validateElement(@NonNull ModelASTStages stages) {
        boolean valid = true

        if (stages.stages.isEmpty()) {
            errorCollector.error(stages, Messages.ModelValidatorImpl_NoStages())
            valid = false
        }

        def stageNames = stages.stages.collect { s ->
            s.name
        }

        stageNames.findAll { stageNames.count(it) > 1 }.unique().each { sn ->
            errorCollector.error(stages, Messages.ModelValidatorImpl_DuplicateStageName(sn))
            valid = false
        }

        return validateFromContributors(stages, valid)
    }

    boolean validateElement(@NonNull ModelASTParallel parallel) {
        return validateElement((ModelASTStages)parallel)
    }

    boolean validateElement(@NonNull ModelASTMatrix matrix) {
        boolean valid = true

        if (matrix.axes == null) {
            errorCollector.error(matrix, Messages.ModelValidatorImpl_RequiredSection("axes"))
            valid = false
        }

        if (matrix.stages == null) {
            errorCollector.error(matrix, Messages.ModelValidatorImpl_RequiredSection("stages"))
            valid = false
        }

        return validateFromContributors(matrix, valid)
    }

    boolean validateElement(ModelASTAxisContainer axes) {
        boolean valid = true

        if (axes.axes.isEmpty()) {
            errorCollector.error(axes, Messages.ModelValidatorImpl_NoAxes())
            valid = false
        }

        def names = axes.axes.collect { s ->
            s.name
        }

        names.findAll { it != null && it.key != null && it.key != '' && names.count(it) > 1 }.unique().each { name ->
            errorCollector.error(name, Messages.ModelValidatorImpl_DuplicateAxisName(name.getKey()))
            valid = false
        }

        return validateFromContributors(axes, valid)
    }

    boolean validateElement(ModelASTAxis axis) {
        boolean valid = true

        if (axis.name == null) {
            errorCollector.error(axis, Messages.ModelValidatorImpl_RequiredSection("name"))
            valid = false
        } else if (!Utils.validEnvIdentifier(axis.name.key)) {
            errorCollector.error(axis.name, Messages.ModelValidatorImpl_InvalidIdentifierInEnv(axis.name.key))
            valid = false
        }

        if (axis.values.isEmpty()) {
            errorCollector.error(axis, Messages.ModelValidatorImpl_RequiredSection("values"))
        }

        axis.values.findAll { axis.values.count(it) > 1 }.unique().each { value ->
            errorCollector.error(value, Messages.ModelValidatorImpl_DuplicateAxisValue(value.value))
            valid = false
        }

        axis.values.each { value ->
            if (!value.literal) {
                errorCollector.error(value, Messages.ModelParser_ExpectedStringLiteralButGot(value.value))
                valid = false
            }
        }

        return validateFromContributors(axis, valid)
    }

    boolean validateElement(ModelASTExcludes excludes) {
        boolean valid = true

        if (excludes.excludes.isEmpty()) {
            errorCollector.error(excludes, Messages.ModelValidatorImpl_NoExcludes())
            valid = false
        }

        return validateFromContributors(excludes, valid)
    }

    boolean validateElement(ModelASTExclude exclude) {
        boolean valid = true

        if (exclude.excludeAxes.isEmpty()) {
            errorCollector.error(exclude, Messages.ModelValidatorImpl_NoAxes())
            valid = false
        }

        def names = exclude.excludeAxes.collect { s ->
            s.name
        }

        names.findAll { it != null && it.key != null && it.key != '' && names.count(it) > 1 }.unique().each { name ->
            errorCollector.error(name, Messages.ModelValidatorImpl_DuplicateAxisName(name.getKey()))
            valid = false
        }

        return validateFromContributors(exclude, valid)
    }

    boolean validateElement(ModelASTExcludeAxis axis) {
        boolean valid = true

        // validation is the  by ModelASTExcludeAxis

        return validateFromContributors(axis, valid)
    }

    boolean validateElement(@NonNull ModelASTAgent agent) {
        boolean valid = true

        Map<String, DescribableModel> possibleModels = DeclarativeAgentDescriptor.describableModels

        List<String> orderedNames = DeclarativeAgentDescriptor.allSorted().collect { it.name }
        String typeName = agent.agentType?.key

        if (typeName == null) {
            errorCollector.error(agent, Messages.ModelValidatorImpl_NoAgentType(orderedNames))
            valid = false
        } else if (!(typeName in DeclarativeAgentDescriptor.zeroArgModels().keySet())) {
            DescribableModel model = possibleModels.get(typeName)
            if (model == null) {
                errorCollector.error(agent.agentType, Messages.ModelValidatorImpl_InvalidAgentType(typeName, orderedNames))
                valid = false
            } else {
                List<DescribableParameter> requiredParams = model.parameters.findAll { it.isRequired() }

                if (agent.variables instanceof ModelASTClosureMap) {
                    ModelASTClosureMap map = (ModelASTClosureMap) agent.variables
                    requiredParams.each { p ->
                        if (!map.containsKey(p.name)) {
                            errorCollector.error(agent.agentType, Messages.ModelValidatorImpl_MissingAgentParameter(typeName, p.name))
                            valid = false
                        }
                    }
                    map.variables.each { k, v ->
                        // Make sure we don't actually include "context" in the valid param names, since, well, it's
                        // not really one.
                        List<String> validParamNames = model.parameters.collect { it.name }
                        if (!validParamNames.contains(k.key)) {
                            errorCollector.error(k, Messages.ModelValidatorImpl_InvalidAgentParameter(k.key, typeName, validParamNames))
                            valid = false
                        }
                    }
                } else if (requiredParams.size() > 1) {
                    errorCollector.error(agent.agentType,
                        Messages.ModelValidatorImpl_MultipleAgentParameters(typeName,
                            requiredParams.collect { it.name }))
                    valid = false
                }
            }
        }
        return validateFromContributors(agent, valid)
    }

    boolean validateElement(@NonNull ModelASTValue value) {
        return validateFromContributors(value, true)
    }

    @SuppressFBWarnings(value = "UPM_UNCALLED_PRIVATE_METHOD")
    private boolean validateFromContributors(ModelASTElement element, boolean isValid, boolean isNested = false) {
        boolean contributorsValid = getContributors().collect { contributor ->
            List<String> errors
            if (!(element instanceof ModelASTStage)) {
                errors = contributor.validateElementAll(element, getExecution())
            } else {
                errors = contributor.validateElementAll((ModelASTStage)element, isNested, getExecution())
            }
            if (!errors.isEmpty()) {
                errors.each { err ->
                    errorCollector.error(element, err)
                }
                return false
            } else {
                return true
            }
        }.every { it }
        if (isValid) {
            return contributorsValid
        } else {
            return false
        }
    }
}
