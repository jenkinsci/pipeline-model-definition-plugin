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
import org.jenkinsci.plugins.pipeline.modeldefinition.Messages
import org.jenkinsci.plugins.pipeline.modeldefinition.agent.DeclarativeAgentDescriptor
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.*
import org.jenkinsci.plugins.pipeline.modeldefinition.model.BuildCondition
import org.jenkinsci.plugins.pipeline.modeldefinition.model.Options
import org.jenkinsci.plugins.pipeline.modeldefinition.model.Parameters
import org.jenkinsci.plugins.pipeline.modeldefinition.model.Tools
import org.jenkinsci.plugins.pipeline.modeldefinition.model.Triggers
import org.jenkinsci.plugins.pipeline.modeldefinition.model.Wrappers
import org.jenkinsci.plugins.pipeline.modeldefinition.when.DeclarativeStageConditionalDescriptor
import org.jenkinsci.plugins.structs.SymbolLookup
import org.jenkinsci.plugins.structs.describable.DescribableModel
import org.jenkinsci.plugins.structs.describable.DescribableParameter
import org.jenkinsci.plugins.workflow.steps.Step
import org.jenkinsci.plugins.workflow.steps.StepDescriptor

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
    private transient Map<String,StepDescriptor> stepMap
    private transient Map<String,DescribableModel<? extends Step>> modelMap
    private transient Map<String,Descriptor> describableMap
    private transient Map<String,DescribableModel<? extends Describable>> describableModelMap

    public ModelValidatorImpl(ErrorCollector e) {
        this.errorCollector = e
        this.stepMap = StepDescriptor.all().collectEntries { StepDescriptor d ->
            [(d.functionName): d]
        }
        this.modelMap = [:]
        this.describableMap = [:]
        this.describableModelMap = [:]
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
        if (when.conditions.isEmpty()) {
            errorCollector.error(when, Messages.ModelValidator_ModelASTWhen_empty())
            return false
        } else {
            def allNames = DeclarativeStageConditionalDescriptor.allNames()
            boolean isUnknownName = false
            when.conditions.each {step ->
                if (!(step.name in allNames)) {
                    errorCollector.error(when, Messages.ModelValidatorImpl_UnknownWhenConditional(step.name, allNames.join(", ")))
                    isUnknownName = true
                } else {
                    step.args
                }
            }
            return !isUnknownName
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
                Descriptor desc = lookupStepDescriptor(step.name)
                DescribableModel<? extends Describable> model

                if (desc != null) {
                    model = modelForStep(step.name)
                } else {
                    desc = lookupFunction(step.name)
                    if (desc != null) {
                        model = modelForDescribable(step.name)
                    }
                }

                if (model != null) {
                    if (step.args instanceof ModelASTNamedArgumentList) {
                        ModelASTNamedArgumentList argList = (ModelASTNamedArgumentList) step.args

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

                            if (!validateParameterType(v, p.erasedType, k)) {
                                valid = false
                            }
                        }
                        model.parameters.each { p ->
                            if (p.isRequired() && !argList.containsKeyName(p.name)) {
                                errorCollector.error(step, Messages.ModelValidatorImpl_MissingRequiredStepParameter(p.name))
                                valid = false
                            }
                        }
                    } else if (step.args instanceof ModelASTPositionalArgumentList) {
                        ModelASTPositionalArgumentList argList = (ModelASTPositionalArgumentList) step.args

                        List<DescribableParameter> requiredParams = model.parameters.findAll { it.isRequired() }

                        if (requiredParams.size() != argList.arguments.size()) {
                            errorCollector.error(step, Messages.ModelValidatorImpl_WrongNumberOfStepParameters(step.name, requiredParams.size(), argList.arguments.size()))
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
                        assert step.args instanceof ModelASTSingleArgument;
                        ModelASTSingleArgument arg = (ModelASTSingleArgument) step.args;

                        def p = model.soleRequiredParameter;
                        if (p == null && !stepTakesClosure(desc)) {
                            errorCollector.error(step, Messages.ModelValidatorImpl_NotSingleRequiredParameter())
                            valid = false
                        } else {
                            Class erasedType = p?.erasedType
                            if (stepTakesClosure(desc)) {
                                erasedType = String.class
                            }
                            def v = arg.value;

                            if (!validateParameterType(v, erasedType)) {
                                valid = false
                            }
                        }

                    }
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
            Descriptor desc = lookupFunction(meth.name)
            DescribableModel<? extends Describable> model
            if (desc != null) {
                model = modelForDescribable(meth.name)
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
                                errorCollector.error(kvm.key, Messages.ModelValidatorImpl_WrongBuildParameterType(kvm.key.key))
                                valid = false
                            }
                        }
                    }
                } else {
                    List<DescribableParameter> requiredParams = model.parameters.findAll { it.isRequired() }

                    if (requiredParams.size() != meth.args.size()) {
                        errorCollector.error(meth, Messages.ModelValidatorImpl_WrongNumberOfStepParameters(meth.name, requiredParams.size(), meth.args.size()))
                        valid = false
                    } else {
                        requiredParams.eachWithIndex { DescribableParameter entry, int i ->
                            def argVal = meth.args.get(i)
                            if (argVal instanceof ModelASTMethodCall) {
                                valid = validateElement((ModelASTMethodCall) argVal)
                            } else {
                                if (!validateParameterType((ModelASTValue) argVal, entry.erasedType)) {
                                    errorCollector.error((ModelASTValue)argVal, Messages.ModelValidatorImpl_WrongParameterType(entry.name))
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
        if (opts.options.isEmpty()) {
            errorCollector.error(opts, Messages.ModelValidatorImpl_EmptySection("options"))
            return false
        }

        return true
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
        } else {
            valid = validateElement((ModelASTMethodCall)trig)
        }
        return valid
    }

    public boolean validateElement(@Nonnull ModelASTTriggers triggers) {
        if (triggers.triggers.isEmpty()) {
            errorCollector.error(triggers, Messages.ModelValidatorImpl_EmptySection("triggers"))
            return false
        }

        return true
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
        } else {
            valid = validateElement((ModelASTMethodCall)param)
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
        } else {
            valid = validateElement((ModelASTMethodCall)opt)
        }
        return valid
    }

    private boolean validateParameterType(ModelASTValue v, Class erasedType, ModelASTKey k = null) {
        if (v.isLiteral()) {
            try {
                // Converting from boolean or int to string at runtime doesn't work, but does pass castToType. So.
                if (erasedType.equals(String.class)
                    && (v.value instanceof Integer || v.value instanceof Boolean)) {
                    throw new RuntimeException("Ignore")
                }
                ScriptBytecodeAdapter.castToType(v.value, erasedType);
            } catch (Exception e) {
                if (k != null) {
                    errorCollector.error(v, Messages.ModelValidatorImpl_InvalidParameterType(erasedType, k.key, v.value.toString()))
                } else {
                    errorCollector.error(v, Messages.ModelValidatorImpl_InvalidUnnamedParameterType(erasedType, v.value.toString()))
                }
                return false
            }
        }
        return true
    }

    public boolean validateElement(@Nonnull ModelASTWrapper wrapper) {
        boolean valid = true

        if (wrapper.name == null) {
            // This means that we failed at compilation time so can move on.
        }
        // We can't do trigger validation without a Jenkins instance, so move on.
        else if (!(wrapper.name in Wrappers.getEligibleSteps())) {
            errorCollector.error(wrapper,
                Messages.ModelValidatorImpl_InvalidSectionType("wrapper", wrapper.name, Wrappers.getEligibleSteps()))
            valid = false
        } else if (wrapper.args.any { it instanceof ModelASTKeyValueOrMethodCallPair }
            && !wrapper.args.every { it instanceof ModelASTKeyValueOrMethodCallPair }) {
            errorCollector.error(wrapper, Messages.ModelValidatorImpl_MixedNamedAndUnnamedParameters())
            valid = false
        } else {
            valid = validateElement((ModelASTMethodCall)wrapper)
        }
        return valid
    }

    public boolean validateElement(@Nonnull ModelASTWrappers wrappers) {
        if (wrappers.wrappers.isEmpty()) {
            errorCollector.error(wrappers, Messages.ModelValidatorImpl_EmptySection("wrappers"))
            return false
        }

        return true
    }

    private boolean stepTakesClosure(Descriptor d) {
        if (d instanceof StepDescriptor) {
            return ((StepDescriptor)d).takesImplicitBlockArgument()
        } else {
            return false
        }
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

        if (agent.args instanceof ModelASTSingleArgument) {
            ModelASTSingleArgument singleArg = (ModelASTSingleArgument) agent.args
            Map<String,DescribableModel> zeroArgModels = DeclarativeAgentDescriptor.zeroArgModels()
            if (!zeroArgModels.containsKey(singleArg.value.getValue())) {
                errorCollector.error(agent.args, Messages.ModelValidatorImpl_InvalidAgent(singleArg.value.toGroovy(), zeroArgModels.keySet().sort()))
                valid = false
            }
        } else if (agent.args instanceof ModelASTNamedArgumentList) {
            ModelASTNamedArgumentList namedArgs = (ModelASTNamedArgumentList)agent.args
            List<String> argKeys = namedArgs.arguments.collect { k, v ->
                k.key
            }

            Map<String,DescribableModel> possibleModels = DeclarativeAgentDescriptor.describableModels

            List<String> orderedNames = DeclarativeAgentDescriptor.all().collect { it.name }
            String typeName = orderedNames.find { it in argKeys }

            if (typeName == null) {
                errorCollector.error(agent, Messages.ModelValidatorImpl_NoAgentType(orderedNames))
                valid = false
            } else {
                DescribableModel model = possibleModels.get(typeName)
                model.parameters.findAll { it.required }.each { p ->
                    if (!argKeys.contains(p.name)) {
                        errorCollector.error(agent, Messages.ModelValidatorImpl_MissingAgentParameter(typeName, p.name))
                        valid = false
                    }
                }
                namedArgs.arguments.each { k, v ->
                    List<String> validParamNames = model.parameters.collect { it.name }
                    if (!validParamNames.contains(k.key)) {
                        errorCollector.error(k, Messages.ModelValidatorImpl_InvalidAgentParameter(k.key, typeName, validParamNames))
                        valid = false
                    }
                }
            }
        }

        return valid
    }

    private DescribableModel<? extends Step> modelForStep(String n) {
        if (!modelMap.containsKey(n)) {
            Class<? extends Step> c = lookupStepDescriptor(n)?.clazz
            modelMap.put(n, c != null ? new DescribableModel<? extends Step>(c) : null)
        }

        return modelMap.get(n)
    }

    private DescribableModel<? extends Describable> modelForDescribable(String n) {
        if (!describableModelMap.containsKey(n)) {
            Class<? extends Describable> c = lookupFunction(n)?.clazz
            describableModelMap.put(n, c != null ? new DescribableModel<? extends Describable>(c) : null)
        }

        return describableModelMap.get(n)
    }

    private StepDescriptor lookupStepDescriptor(String n) {
        return stepMap.get(n)
    }

    private Descriptor lookupFunction(String n) {
        if (!describableMap.containsKey(n)) {
            try {
                Descriptor d = SymbolLookup.get().findDescriptor(Describable.class, n)
                describableMap.put(n, d)
            } catch (NullPointerException e) {
                describableMap.put(n, null)
            }
        }

        return describableMap.get(n)
    }

}
