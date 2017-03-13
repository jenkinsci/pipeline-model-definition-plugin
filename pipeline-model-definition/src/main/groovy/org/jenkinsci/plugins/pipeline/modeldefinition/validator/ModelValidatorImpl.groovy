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
import hudson.tools.ToolDescriptor
import hudson.tools.ToolInstallation
import hudson.util.EditDistance
import jenkins.model.Jenkins
import org.codehaus.groovy.runtime.ScriptBytecodeAdapter
import org.jenkinsci.plugins.pipeline.modeldefinition.DescriptorLookupCache
import org.jenkinsci.plugins.pipeline.modeldefinition.Messages
import org.jenkinsci.plugins.pipeline.modeldefinition.Utils
import org.jenkinsci.plugins.pipeline.modeldefinition.agent.DeclarativeAgentDescriptor
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.*
import org.jenkinsci.plugins.pipeline.modeldefinition.model.BuildCondition
import org.jenkinsci.plugins.pipeline.modeldefinition.model.Options
import org.jenkinsci.plugins.pipeline.modeldefinition.model.Parameters
import org.jenkinsci.plugins.pipeline.modeldefinition.model.Tools
import org.jenkinsci.plugins.pipeline.modeldefinition.model.Triggers
import org.jenkinsci.plugins.pipeline.modeldefinition.when.DeclarativeStageConditional
import org.jenkinsci.plugins.pipeline.modeldefinition.when.DeclarativeStageConditionalDescriptor
import org.jenkinsci.plugins.structs.SymbolLookup
import org.jenkinsci.plugins.structs.describable.DescribableModel
import org.jenkinsci.plugins.structs.describable.DescribableParameter

import javax.annotation.Nonnull

/**
 * Class for validating various AST elements. Contains the error collector as well as caches for steps, models, etc.
 *
 * @author Andrew Bayer
 */
@ToString
@EqualsAndHashCode
@SuppressFBWarnings(value="SE_NO_SERIALVERSIONID")
class ModelValidatorImpl implements ModelValidator {

    private final ErrorCollector errorCollector
    private transient DescriptorLookupCache lookup

    public ModelValidatorImpl(ErrorCollector e) {
        this.errorCollector = e
        this.lookup = DescriptorLookupCache.getPublicCache()
    }

    public DescriptorLookupCache getLookup() {
        return lookup
    }

    public boolean validateElement(@Nonnull ModelASTPostBuild postBuild) {
        // post specific validation
        true
    }

    public boolean validateElement(@Nonnull ModelASTPostStage post) {
        // post stage specific validation
        true
    }

    public boolean validateElement(@Nonnull ModelASTBuildConditionsContainer post) {
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

    public boolean validateElement(@Nonnull ModelASTBuildCondition buildCondition) {
        boolean valid = true

        // Only do the symbol lookup if we have a Jenkins instance and condition/branch aren't null. That only happens
        // when there's a failure at parse-time.
        if (Jenkins.getInstance() != null && buildCondition.condition != null && buildCondition.branch != null) {
            if (SymbolLookup.get().find(BuildCondition.class, buildCondition.condition) == null) {
                errorCollector.error(buildCondition,
                    Messages.ModelValidatorImpl_InvalidBuildCondition(buildCondition.condition, BuildCondition.getOrderedConditionNames()))
                valid = false
            }
        }

        return valid
    }

    public boolean validateElement(@Nonnull ModelASTEnvironment env) {
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

        return valid
    }

    public boolean validateElement(@Nonnull ModelASTTools t) {
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
                // Don't bother checking whether the tool exists in this Jenkins master if we know it isn't an allowed tool type.

                // Can't do tools validation without a Jenkins instance, so move on if that's not available.
                if (Jenkins.getInstance() != null) {
                    // Not bothering with a null check here since we could only get this far if the ToolDescriptor's available in the first place.
                    ToolDescriptor desc = ToolInstallation.all().find { it.getId().equals(Tools.typeForKey(k.key)) }
                    def installer = desc.getInstallations().find { it.name.equals((String) v.value) }
                    if (installer == null) {
                        String possible = EditDistance.findNearest((String) v.value, desc.getInstallations().collect { it.name })
                        errorCollector.error(v, Messages.ModelValidatorImpl_NoToolVersion(k.key, v.value, possible))
                        valid = false
                    }
                }
            }
        }

        return valid
    }

    public boolean validateElement(ModelASTWhen when) {
        boolean valid = true
        if (when.condition == null) {
            errorCollector.error(when, Messages.ModelValidatorImpl_EmptyWhen())
            valid = false
        }

        return valid
    }

    public boolean validateElement(ModelASTLibraries libraries) {
        boolean valid = true

        if (libraries.libs.isEmpty()) {
            errorCollector.error(libraries, Messages.ModelValidatorImpl_EmptySection("libraries"))
            valid = false
        } else {
            libraries.libs.each { l ->
                // TODO: Decide what validation, if any, we want to do for library identifiers.
            }
        }

        return valid
    }

    public boolean validateElement(ModelASTWhenCondition condition) {
        boolean valid = true
        def allNames = DeclarativeStageConditionalDescriptor.allNames()

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
                    valid = validateDescribable(condition, condition.name, condition.args, model, desc, false)
                }
            }
        }

        return valid
    }

    private boolean validateDescribable(ModelASTElement element, String name,
                                        ModelASTArgumentList args,
                                        DescribableModel<? extends Describable> model,
                                        Descriptor desc, boolean takesClosure = false) {
        boolean valid = true

        if (args instanceof ModelASTNamedArgumentList) {
            ModelASTNamedArgumentList argList = (ModelASTNamedArgumentList) args

            argList.arguments.each { k, v ->

                def p = model.getParameter(k.key);
                if (p == null) {
                    String possible = EditDistance.findNearest(k.key, model.getParameters().collect {
                        it.name
                    })
                    errorCollector.error(k, Messages.ModelValidatorImpl_InvalidStepParameter(k.key, possible))
                    valid = false
                    return;
                }

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
            model.parameters.each { p ->
                if (p.isRequired() && !argList.containsKeyName(p.name)) {
                    errorCollector.error(element, Messages.ModelValidatorImpl_MissingRequiredStepParameter(p.name))
                    valid = false
                }
            }
        } else if (args instanceof ModelASTPositionalArgumentList) {
            ModelASTPositionalArgumentList argList = (ModelASTPositionalArgumentList) args

            List<DescribableParameter> requiredParams = model.parameters.findAll { it.isRequired() }

            if (requiredParams.size() != argList.arguments.size()) {
                errorCollector.error(element, Messages.ModelValidatorImpl_WrongNumberOfStepParameters(name, requiredParams.size(), argList.arguments.size()))
                valid = false
            } else {
                requiredParams.eachWithIndex { DescribableParameter entry, int i ->
                    def argVal = argList.arguments.get(i)
                    if (!validateParameterType(argVal, entry.erasedType)) {
                        valid = false
                    }
                }
            }
        } else {
            assert args instanceof ModelASTSingleArgument;
            ModelASTSingleArgument arg = (ModelASTSingleArgument) args;

            def p = model.soleRequiredParameter;
            if (p == null && !takesClosure) {
                errorCollector.error(element, Messages.ModelValidatorImpl_NotSingleRequiredParameter())
                valid = false
            } else {
                Class erasedType = p?.erasedType
                def v = arg.value;

                if (!validateParameterType(v, erasedType)) {
                    valid = false
                }
            }

        }

        return valid
    }

    private boolean validateStep(ModelASTStep step, DescribableModel<? extends Describable> model, Descriptor desc) {

        if (step instanceof AbstractModelASTCodeBlock) {
            // No validation needed for code blocks like expression and script
            return true
        } else {
            return validateDescribable(step, step.name, step.args, model, desc, lookup.stepTakesClosure(desc))
        }
    }

    public boolean validateElement(@Nonnull ModelASTStep step) {
        boolean valid = true

        if (ModelASTStep.blockedSteps.keySet().contains(step.name)) {
            errorCollector.error(step,
                Messages.ModelValidatorImpl_BlockedStep(step.name, ModelASTStep.blockedSteps.get(step.name)))
            valid = false
        } else {
            // We can't do step validation without a Jenkins instance, so move on.
            if (Jenkins.getInstance() != null) {
                Descriptor desc = lookup.lookupStepFirstThenFunction(step.name)
                DescribableModel<? extends Describable> model = lookup.modelForStepFirstThenFunction(step.name)

                if (model != null) {
                    valid = validateStep(step, model, desc)
                }
            }
        }

        return valid
    }

    public boolean validateElement(@Nonnull ModelASTMethodCall meth) {
        boolean valid = true
        if (ModelASTMethodCall.blockedSteps.keySet().contains(meth.name)) {
            errorCollector.error(meth,
                Messages.ModelValidatorImpl_BlockedStep(meth.name, ModelASTMethodCall.blockedSteps.get(meth.name)))
            valid = false
        }
        if (Jenkins.getInstance() != null) {
            Descriptor desc = lookup.lookupFunctionFirstThenStep(meth.name)
            DescribableModel<? extends Describable> model
            if (desc != null) {
                model = lookup.modelForFunctionFirstThenStep(meth.name)
            }

            if (model != null) {
                if (meth.args.any { it instanceof ModelASTKeyValueOrMethodCallPair }) {
                    meth.args.each { a ->
                        if (!(a instanceof ModelASTKeyValueOrMethodCallPair)) {
                            errorCollector.error(meth, Messages.ModelValidatorImpl_MixedNamedAndUnnamedParameters())
                            return
                        }
                        ModelASTKeyValueOrMethodCallPair kvm = (ModelASTKeyValueOrMethodCallPair) a

                        def p = model.getParameter(kvm.key.key);
                        if (p == null) {
                            String possible = EditDistance.findNearest(kvm.key.key, model.getParameters().collect {
                                it.name
                            })
                            errorCollector.error(kvm.key, Messages.ModelValidatorImpl_InvalidStepParameter(kvm.key.key, possible))
                            valid = false
                            return;
                        }

                        if (kvm.value instanceof ModelASTMethodCall) {
                            valid = validateElement((ModelASTMethodCall) kvm.value)
                        } else {
                            if (!validateParameterType((ModelASTValue) kvm.value, p.erasedType, kvm.key)) {
                                valid = false
                            }
                        }
                    }
                } else {
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
        return valid
    }

    public boolean validateElement(@Nonnull ModelASTOptions opts) {
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
        }

        return valid
    }

    public boolean validateElement(@Nonnull ModelASTTrigger trig) {
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
        return valid
    }

    public boolean validateElement(@Nonnull ModelASTTriggers triggers) {
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

        return valid
    }

    public boolean validateElement(@Nonnull ModelASTBuildParameter param) {
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
        return valid
    }

    public boolean validateElement(@Nonnull ModelASTBuildParameters params) {
        if (params.parameters.isEmpty()) {
            errorCollector.error(params, Messages.ModelValidatorImpl_EmptySection("parameters"))
            return false
        }

        return true
    }

    public boolean validateElement(@Nonnull ModelASTOption opt) {
        boolean valid = true

        if (opt.name == null) {
            // Validation failed at compilation time so move on.
        }
        // We can't do property validation without a Jenkins instance, so move on.
        else if (Options.typeForKey(opt.name) == null) {
            errorCollector.error(opt,
                Messages.ModelValidatorImpl_InvalidSectionType("option", opt.name, Options.getAllowedOptionTypes().keySet()))
            valid = false
        } else if (opt.args.any { it instanceof ModelASTKeyValueOrMethodCallPair }
            && !opt.args.every { it instanceof ModelASTKeyValueOrMethodCallPair }) {
            errorCollector.error(opt, Messages.ModelValidatorImpl_MixedNamedAndUnnamedParameters())
            valid = false
        }
        return valid
    }

    private boolean validateParameterType(ModelASTValue v, Class erasedType, ModelASTKey k = null) {
        if (v.isLiteral()) {
            try {
                // Converting amongst boolean, string and int at runtime doesn't work, but does pass castToType. So.
                if ((erasedType.equals(String.class) && (v.value instanceof Integer || v.value instanceof Boolean)) ||
                    (erasedType.equals(int.class) && (v.value instanceof String || v.value instanceof Boolean))) {
                    throw new RuntimeException("Ignore")
                }
                ScriptBytecodeAdapter.castToType(v.value, erasedType);
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

    public boolean validateElement(@Nonnull ModelASTBranch branch) {
        boolean valid = true

        if (branch.steps.isEmpty()) {
            errorCollector.error(branch, Messages.ModelValidatorImpl_NoSteps())
            valid = false
        }

        return valid
    }

    public boolean validateElement(@Nonnull ModelASTPipelineDef pipelineDef) {
        boolean valid = true

        if (pipelineDef.stages == null) {
            errorCollector.error(pipelineDef, Messages.ModelValidatorImpl_RequiredSection("stages"))
            valid = false
        }

        if (pipelineDef.agent == null) {
            errorCollector.error(pipelineDef, Messages.ModelValidatorImpl_RequiredSection("agent"))
        }
        return valid
    }

    public boolean validateElement(@Nonnull ModelASTStage stage) {
        boolean valid = true
        if (stage.name == null) {
            errorCollector.error(stage, Messages.ModelValidatorImpl_NoStageName())
            valid = false
        }
        if (stage.branches.isEmpty()) {
            errorCollector.error(stage, Messages.ModelValidatorImpl_NothingForStage(stage.name))
            valid = false
        }
        def branchNames = stage.branches.collect { it.name }
        branchNames.findAll { branchNames.count(it) > 1 }.unique().each { bn ->
            errorCollector.error(stage, Messages.ModelValidatorImpl_DuplicateParallelName(bn))
            valid = false
        }

        return valid
    }

    public boolean validateElement(@Nonnull ModelASTStages stages) {
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

        return valid
    }

    public boolean validateElement(@Nonnull ModelASTAgent agent) {
        boolean valid = true

        Map<String, DescribableModel> possibleModels = DeclarativeAgentDescriptor.describableModels

        List<String> orderedNames = DeclarativeAgentDescriptor.all().collect { it.name }
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
        return valid
    }
}
