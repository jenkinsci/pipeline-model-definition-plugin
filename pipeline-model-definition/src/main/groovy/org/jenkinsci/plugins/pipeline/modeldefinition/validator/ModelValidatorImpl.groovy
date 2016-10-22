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
import org.jenkinsci.plugins.pipeline.modeldefinition.agent.DeclarativeAgentDescriptor
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTBranch
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTBuildConditionsContainer
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTBuildParameter
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTBuildParameters
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTEnvironment
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTJobProperties
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTJobProperty
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTKey
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTKeyValueOrMethodCallPair
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTMethodCall
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTNamedArgumentList
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTNotifications
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTPipelineDef
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTPositionalArgumentList
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTPostBuild
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTPostStage
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTSingleArgument
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTStage
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTStages
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTStep
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTTrigger
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTTriggers
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTValue
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTBuildCondition
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTAgent
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTTools
import org.jenkinsci.plugins.pipeline.modeldefinition.model.BuildCondition
import org.jenkinsci.plugins.pipeline.modeldefinition.model.Agent
import org.jenkinsci.plugins.pipeline.modeldefinition.model.JobProperties
import org.jenkinsci.plugins.pipeline.modeldefinition.model.Parameters
import org.jenkinsci.plugins.pipeline.modeldefinition.model.Tools
import org.jenkinsci.plugins.pipeline.modeldefinition.model.Triggers
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
        // postBuild specific validation
        true
    }

    public boolean validateElement(@Nonnull ModelASTPostStage post) {
        // post stage specific validation
        true
    }

    public boolean validateElement(ModelASTNotifications notifications) {
        // notifications specific validation
        true
    }

    public boolean validateElement(@Nonnull ModelASTBuildConditionsContainer post) {
        boolean valid = true

        if (post.conditions.isEmpty()) {
            errorCollector.error(post, "${post.getName()} can not be empty")
            valid = false
        }

        def conditionNames = post.conditions.collect { c ->
            c.condition
        }

        conditionNames.findAll { conditionNames.count(it) > 1 }.unique().each { sn ->
            errorCollector.error(post, "Duplicate build condition name: '${sn}'")
            valid = false
        }

        return valid
    }

    public boolean validateElement(@Nonnull ModelASTBuildCondition buildCondition) {
        boolean valid = true

        // Only do the symbol lookup if we have a Jenkins instance
        if (Jenkins.getInstance() != null) {
            if (SymbolLookup.get().find(BuildCondition.class, buildCondition.condition) == null) {
                errorCollector.error(buildCondition, "Invalid condition '${buildCondition.condition}' - valid conditions are ${BuildCondition.getConditionMethods().keySet()}")
                valid = false
            }
        }

        return valid
    }

    public boolean validateElement(@Nonnull ModelASTEnvironment env) {
        boolean valid = true

        if (env.variables.isEmpty()) {
            errorCollector.error(env, "No variables specified for environment")
            valid = false
        }

        return valid
    }

    public boolean validateElement(@Nonnull ModelASTTools t) {
        boolean valid = true

        if (t.tools.isEmpty()) {
            errorCollector.error(t, "No tools specified.")
            valid = false
        }

        t.tools.each { k, v ->
            if (Tools.typeForKey(k.key) == null) {
                errorCollector.error(k, "Invalid tool type '${k.key}'. Valid tool types: ${Tools.getAllowedToolTypes().keySet()}")
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
                        errorCollector.error(v, "Tool type '${k.key}' does not have an install of '${v.value}' configured - did you mean '${possible}'?")
                        valid = false
                    }
                }
            }
        }

        return valid
    }

    public boolean validateElement(@Nonnull ModelASTStep step) {
        boolean valid = true

        if (ModelASTStep.blockedSteps.keySet().contains(step.name)) {
            errorCollector.error(step,
                "Invalid step '${step.name}' used - not allowed in this context - ${ModelASTStep.blockedSteps.get(step.name)}")
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
                                errorCollector.error(k, "Invalid parameter '${k.key}', did you mean '${possible}'?")
                                valid = false
                                return;
                            }

                            if (!validateParameterType(v, p.erasedType, k)) {
                                valid = false
                            }
                        }
                        model.parameters.each { p ->
                            if (p.isRequired() && !argList.containsKeyName(p.name)) {
                                errorCollector.error(step, "Missing required parameter: '${p.name}'")
                                valid = false
                            }
                        }
                    } else if (step.args instanceof ModelASTPositionalArgumentList) {
                        ModelASTPositionalArgumentList argList = (ModelASTPositionalArgumentList) step.args

                        List<DescribableParameter> requiredParams = model.parameters.findAll { it.isRequired() }

                        if (requiredParams.size() != argList.arguments.size()) {
                            errorCollector.error(step, "Step '${step.name}' should have ${requiredParams.size()} arguments but has ${argList.arguments.size()} arguments instead.")
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
                            errorCollector.error(step, "Step does not take a single required parameter - use named parameters instead")
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
                "Invalid step '${meth.name}' used - not allowed in this context - ${ModelASTMethodCall.blockedSteps.get(meth.name)}")
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
                            errorCollector.error(meth, "Can't mix named and unnamed parameter definition arguments")
                            return
                        }
                        ModelASTKeyValueOrMethodCallPair kvm = (ModelASTKeyValueOrMethodCallPair) a

                        def p = model.getParameter(kvm.key.key);
                        if (p == null) {
                            String possible = EditDistance.findNearest(kvm.key.key, model.getParameters().collect {
                                it.name
                            })
                            errorCollector.error(kvm.key, "Invalid parameter '${kvm.key.key}', did you mean '${possible}'?")
                            valid = false
                            return;
                        }

                        if (kvm.value instanceof ModelASTMethodCall) {
                            valid = validateElement((ModelASTMethodCall) kvm.value)
                        } else {
                            if (!validateParameterType((ModelASTValue) kvm.value, p.erasedType, kvm.key)) {
                                errorCollector.error(kvm.key, "Invalid type for parameter '${kvm.key.key}'")
                                valid = false
                            }
                        }
                    }
                } else {
                    List<DescribableParameter> requiredParams = model.parameters.findAll { it.isRequired() }

                    if (requiredParams.size() != meth.args.size()) {
                        errorCollector.error(meth, "'${meth.name}' should have ${requiredParams.size()} arguments but has ${meth.args.size()} arguments instead.")
                        valid = false
                    } else {
                        requiredParams.eachWithIndex { DescribableParameter entry, int i ->
                            def argVal = meth.args.get(i)
                            if (argVal instanceof ModelASTMethodCall) {
                                valid = validateElement((ModelASTMethodCall) argVal)
                            } else {
                                if (!validateParameterType((ModelASTValue) argVal, entry.erasedType)) {
                                    errorCollector.error((ModelASTValue)argVal, "Invalid type for parameter '${entry.name}'")
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

    public boolean validateElement(@Nonnull ModelASTJobProperties props) {
        if (props.properties.isEmpty()) {
            errorCollector.error(props, "Cannot have empty jobProperties section")
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
                "Invalid trigger type '${trig.name}'. Valid trigger types: ${Triggers.getAllowedTriggerTypes().keySet()}")
            valid = false
        } else if (trig.args.any { it instanceof ModelASTKeyValueOrMethodCallPair }
            && !trig.args.every { it instanceof ModelASTKeyValueOrMethodCallPair }) {
            errorCollector.error(trig, "Can't mix named and unnamed trigger arguments")
            valid = false
        } else {
            valid = validateElement((ModelASTMethodCall)trig)
        }
        return valid
    }

    public boolean validateElement(@Nonnull ModelASTTriggers triggers) {
        if (triggers.triggers.isEmpty()) {
            errorCollector.error(triggers, "Cannot have empty triggers section")
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
                "Invalid parameter definition type '${param.name}'. Valid parameter definition types: "
                    + Parameters.allowedParameterTypes.keySet())
            valid = false
        } else if (param.args.any { it instanceof ModelASTKeyValueOrMethodCallPair }
            && !param.args.every { it instanceof ModelASTKeyValueOrMethodCallPair }) {
            errorCollector.error(param, "Can't mix named and unnamed parameter definition arguments")
            valid = false
        } else {
            valid = validateElement((ModelASTMethodCall)param)
        }
        return valid
    }

    public boolean validateElement(@Nonnull ModelASTBuildParameters params) {
        if (params.parameters.isEmpty()) {
            errorCollector.error(params, "Cannot have empty parameters section")
            return false
        }

        return true
    }

    public boolean validateElement(@Nonnull ModelASTJobProperty prop) {
        boolean valid = true

        if (prop.name == null) {
            // Validation failed at compilation time so move on.
        }
        // We can't do property validation without a Jenkins instance, so move on.
        else if (JobProperties.typeForKey(prop.name) == null) {
            errorCollector.error(prop,
                "Invalid job property type '${prop.name}'. Valid job property types: ${JobProperties.getAllowedPropertyTypes().keySet()}")
            valid = false
        } else if (prop.args.any { it instanceof ModelASTKeyValueOrMethodCallPair }
            && !prop.args.every { it instanceof ModelASTKeyValueOrMethodCallPair }) {
            errorCollector.error(prop, "Can't mix named and unnamed job property arguments")
            valid = false
        } else {
            valid = validateElement((ModelASTMethodCall)prop)
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
                    errorCollector.error(v, "Expecting ${erasedType} for parameter '${k.key}' but got '${v.value}' instead")
                } else {
                    errorCollector.error(v, "Expecting ${erasedType} but got '${v.value}' instead")
                }
                return false
            }
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
            errorCollector.error(branch, "No steps specified for branch")
            valid = false
        }

        return valid
    }

    public boolean validateElement(@Nonnull ModelASTPipelineDef pipelineDef) {
        boolean valid = true

        if (pipelineDef.stages == null) {
            errorCollector.error(pipelineDef, "Missing required section 'stages'")
            valid = false
        }

        if (pipelineDef.agent == null) {
            errorCollector.error(pipelineDef, "Missing required section 'agent'")
        }
        return valid
    }

    public boolean validateElement(@Nonnull ModelASTStage stage) {
        boolean valid = true
        if (stage.name == null) {
            errorCollector.error(stage, "Stage does not have a name")
            valid = false
        }
        if (stage.branches.isEmpty()) {
            errorCollector.error(stage, "Nothing to execute within stage '${stage.name}'")
            valid = false
        }

        return valid
    }

    public boolean validateElement(@Nonnull ModelASTStages stages) {
        boolean valid = true

        if (stages.stages.isEmpty()) {
            errorCollector.error(stages, "No stages specified")
            valid = false
        }

        def stageNames = stages.stages.collect { s ->
            s.name
        }

        stageNames.findAll { stageNames.count(it) > 1 }.unique().each { sn ->
            errorCollector.error(stages, "Duplicate stage name: '${sn}'")
            valid = false
        }

        return valid
    }

    public boolean validateElement(@Nonnull ModelASTAgent agent) {
        boolean valid = true

        if (agent.args instanceof ModelASTSingleArgument) {
            ModelASTSingleArgument singleArg = (ModelASTSingleArgument) agent.args
            Map<String,DescribableModel> singleArgModels = DeclarativeAgentDescriptor.singleArgModels()
            if (!singleArgModels.containsKey(singleArg.value.getValue())) {
                errorCollector.error(agent.args, "Invalid argument for agent - '${singleArg.value.getValue()}' - must be map of config options or bare ${singleArgModels.keySet().join(', ')}.")
                valid = false
            }
        } else if (agent.args instanceof ModelASTNamedArgumentList) {
            ModelASTNamedArgumentList namedArgs = (ModelASTNamedArgumentList)agent.args
            List<String> argKeys = namedArgs.arguments.collect { k, v ->
                k.key
            }

            Map<String,DescribableModel> possibleModels = DeclarativeAgentDescriptor.describableModels
            String typeName = possibleModels.find { k, v -> k in argKeys }.key

            if (typeName == null) {
                errorCollector.error(agent, "No agent type specified. Must contain one of ${DeclarativeAgentDescriptor.orderedNames}")
                valid = false
            } else {
                DescribableModel model = possibleModels.get(typeName)
                model.parameters.findAll { it.required }.each { p ->
                    if (!argKeys.contains(p.name)) {
                        errorCollector.error(agent, "Missing required parameter for agent type '${typeName}': ${p.name}")
                        valid = false
                    }
                }
                namedArgs.arguments.each { k, v ->
                    List<String> validParamNames = model.parameters.collect { it.name }
                    if (!validParamNames.contains(k.key)) {
                        errorCollector.error(k, "Invalid config option '${k.key}' for agent type '${typeName}. Valid config options are ${validParamNames}")
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
