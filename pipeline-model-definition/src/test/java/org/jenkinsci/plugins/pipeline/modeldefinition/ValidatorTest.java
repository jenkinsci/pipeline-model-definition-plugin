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

import hudson.slaves.DumbSlave;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTStep;
import org.jenkinsci.plugins.pipeline.modeldefinition.model.BuildCondition;
import org.jenkinsci.plugins.pipeline.modeldefinition.model.Options;
import org.jenkinsci.plugins.pipeline.modeldefinition.model.Parameters;
import org.jenkinsci.plugins.pipeline.modeldefinition.model.Tools;
import org.jenkinsci.plugins.pipeline.modeldefinition.model.Triggers;
import org.jenkinsci.plugins.pipeline.modeldefinition.when.DeclarativeStageConditionalDescriptor;
import org.junit.BeforeClass;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;

/**
 * @author Andrew Bayer
 */
public class ValidatorTest extends AbstractModelDefTest {

    private static DumbSlave s;

    @BeforeClass
    public static void setUpAgent() throws Exception {
        s = j.createOnlineSlave();
        s.setLabelString("some-label docker");
    }

    @Issue("JENKINS-39011")
    @Test
    public void pipelineStepWithinOtherBlockFailure() throws Exception {
        expectError("pipelineStepWithinOtherBlocksFailure")
                 .logContains(Messages.ModelParser_PipelineBlockNotAtTop(ModelStepLoader.STEP_NAME))
                .go();
    }

    @Test
    public void rejectStageInSteps() throws Exception {
        expectError("rejectStageInSteps")
                .logContains(Messages.ModelValidatorImpl_BlockedStep("stage", ModelASTStep.getBlockedSteps().get("stage")))
                .go();
    }

    @Test
    public void rejectParallelMixedInSteps() throws Exception {
        expectError("rejectParallelMixedInSteps")
                .logContains(Messages.ModelValidatorImpl_BlockedStep("parallel", ModelASTStep.getBlockedSteps().get("parallel")))
                .go();
    }

    @Test
    public void emptyStages() throws Exception {
        expectError("emptyStages")
                .logContains(Messages.ModelValidatorImpl_NoStages())
                .go();
    }

    @Test
    public void emptyJobProperties() throws Exception {
        expectError("emptyJobProperties")
                .logContains(Messages.ModelValidatorImpl_EmptySection("options"))
                .go();
    }

    @Test
    public void emptyParameters() throws Exception {
        expectError("emptyParameters")
                .logContains(Messages.ModelValidatorImpl_EmptySection("parameters"))
                .go();
    }

    @Test
    public void emptyTriggers() throws Exception {
        expectError("emptyTriggers")
                .logContains(Messages.ModelValidatorImpl_EmptySection("triggers"))
                .go();
    }

    @Test
    public void whenInvalidParameterType() throws Exception {
        expectError("whenInvalidParameterType")
                .logContains(Messages.ModelValidatorImpl_InvalidUnnamedParameterType("class java.lang.String", 4, Integer.class))
                .go();
    }

    @Test
    public void emptyWhen() throws Exception {
        expectError("emptyWhen")
                .logContains(Messages.ModelValidatorImpl_EmptyWhen())
                .go();
    }

    @Test
    public void unknownWhenConditional() throws Exception {
        expectError("unknownWhenConditional")
                .logContains(Messages.ModelValidatorImpl_UnknownWhenConditional("banana",
                        StringUtils.join(DeclarativeStageConditionalDescriptor.allNames(), ", ")))
                .go();
    }

    @Test
    public void whenMissingRequiredParameter() throws Exception {
        expectError("whenMissingRequiredParameter")
                .logContains(Messages.ModelValidatorImpl_MissingRequiredStepParameter("value"))
                .go();
    }

    @Test
    public void whenUnknownParameter() throws Exception {
        expectError("whenUnknownParameter")
                .logContains(Messages.ModelValidatorImpl_InvalidStepParameter("banana", "name"))
                .go();
    }

    @Issue("JENKINS-41185")
    @Test
    public void whenNestedChildrenInvalid() throws Exception {
        expectError("whenNestedChildrenInvalid")
                .logContains(Messages.ModelValidatorImpl_NestedWhenWithoutChildren("allOf"),
                        Messages.ModelValidatorImpl_NestedWhenWrongChildrenCount("not", 1))
                .go();
    }

    @Test
    public void blockInJobProperties() throws Exception {
        expectError("blockInJobProperties")
                .logContains(Messages.ModelParser_CannotHaveBlocks(Messages.Parser_Options()))
                .go();
    }

    @Test
    public void blockInParameters() throws Exception {
        expectError("blockInParameters")
                .logContains(Messages.ModelParser_CannotHaveBlocks(Messages.Parser_BuildParameters()))
                .go();
    }

    @Test
    public void blockInTriggers() throws Exception {
        expectError("blockInTriggers")
                .logContains(Messages.ModelParser_CannotHaveBlocks(Messages.Parser_Triggers()))
                .go();
    }

    @Test
    public void mixedMethodArgs() throws Exception {
        expectError("mixedMethodArgs")
                .logContains(Messages.ModelValidatorImpl_MixedNamedAndUnnamedParameters())
                .go();
    }

    @Test
    public void closureAsMethodCallArg() throws Exception {
        expectError("closureAsMethodCallArg")
                .logContains(Messages.ModelParser_MethodCallWithClosure())
                .go();
    }

    @Test
    public void tooFewMethodCallArgs() throws Exception {
        expectError("tooFewMethodCallArgs")
                .logContains(Messages.ModelValidatorImpl_WrongNumberOfStepParameters("cron", 1, 0))
                .go();
    }

    @Test
    public void wrongParameterNameMethodCall() throws Exception {
        expectError("wrongParameterNameMethodCall")
                .logContains(Messages.ModelValidatorImpl_InvalidStepParameter("namd", "name"))
                .go();
    }

    @Test
    public void invalidParameterTypeMethodCall() throws Exception {
        expectError("invalidParameterTypeMethodCall")
                .logContains(Messages.ModelValidatorImpl_InvalidParameterType("class java.lang.String", "name", "1234", Integer.class))
                .go();
    }

    @Test
    public void rejectPropertiesStepInMethodCall() throws Exception {
        expectError("rejectPropertiesStepInMethodCall")
                .logContains(Messages.ModelValidatorImpl_BlockedStep("properties", ModelASTStep.getBlockedSteps().get("properties")))
                .go();
    }

    @Test
    public void rejectMapsForTriggerDefinition() throws Exception {
        expectError("rejectMapsForTriggerDefinition")
                .logContains(Messages.ModelParser_MapNotAllowed(Messages.Parser_Triggers()))
                .go();
    }

    @Test
    public void emptyParallel() throws Exception {
        expectError("emptyParallel")
                .logContains(Messages.ModelValidatorImpl_NothingForStage("foo"))
                .go();
    }

    @Test
    public void parallelPipelineWithInvalidFailFast() throws Exception {
        expectError("parallelPipelineWithInvalidFailFast")
                .logContains(Messages.ModelParser_ExpectedFailFast())
                .go();
    }

    @Test
    public void parallelPipelineWithInvalidExtraKey() throws Exception {
        expectError("parallelPipelineWithInvalidExtraKey")
                .logContains(Messages.ModelParser_ExpectedClosureOrFailFast())
                .go();
    }

    @Test
    public void missingAgent() throws Exception {
        expectError("missingAgent")
                .logContains(Messages.ModelValidatorImpl_RequiredSection("agent"))
                .go();
    }

    @Test
    public void missingStages() throws Exception {
        expectError("missingStages")
                .logContains(Messages.ModelValidatorImpl_RequiredSection("stages"))
                .go();
    }

    @Test
    public void missingRequiredStepParameters() throws Exception {
        expectError("missingRequiredStepParameters")
                .logContains(Messages.ModelValidatorImpl_MissingRequiredStepParameter("time"))
                .go();
    }

    @Test
    public void invalidStepParameterType() throws Exception {
        expectError("invalidStepParameterType")
                .logContains(Messages.ModelValidatorImpl_InvalidParameterType("int", "time", "someTime", String.class))
                .go();
    }

    @Test
    public void invalidTriggerType() throws Exception {
        expectError("invalidTriggerType")
                .logContains(Messages.ModelValidatorImpl_InvalidSectionType("trigger", "banana", Triggers.getAllowedTriggerTypes().keySet()))
                .go();
    }

    @Test
    public void invalidParameterType() throws Exception {
        expectError("invalidParameterType")
                .logContains(Messages.ModelValidatorImpl_InvalidSectionType("parameter", "bananaParam",
                        Parameters.getAllowedParameterTypes().keySet()))
                .go();
    }

    @Test
    public void invalidPropertiesType() throws Exception {
        expectError("invalidPropertiesType")
                .logContains(Messages.ModelValidatorImpl_InvalidSectionType("option", "banana",
                        Options.getAllowedOptionTypes().keySet()))
                .go();
    }

    @Test
    public void unknownStepParameter() throws Exception {
        expectError("unknownStepParameter")
                .logContains(Messages.ModelValidatorImpl_InvalidStepParameter("banana", "unit"))
                .go();
    }

    @Test
    public void perStageConfigEmptySteps() throws Exception {
        expectError("perStageConfigEmptySteps")
                .logContains(Messages.ModelValidatorImpl_NoSteps())
                .go();
    }

    @Test
    public void perStageConfigMissingSteps() throws Exception {
        expectError("perStageConfigMissingSteps")
                .logContains(Messages.ModelValidatorImpl_NothingForStage("foo"))
                .go();
    }

    @Test
    public void perStageConfigUnknownSection() throws Exception {
        expectError("perStageConfigUnknownSection")
                .logContains(Messages.ModelParser_UnknownStageSection("banana"))
                .go();
    }

    @Test
    public void invalidMetaStepSyntax() throws Exception {
        expectError("invalidMetaStepSyntax")
                .logContains(Messages.ModelValidatorImpl_InvalidStepParameter("someRandomField", "caseSensitive"))
                .go();
    }

    @Test
    public void duplicateStageNames() throws Exception {
        expectError("duplicateStageNames")
                .logContains(Messages.ModelValidatorImpl_DuplicateStageName("foo"),
                        Messages.ModelValidatorImpl_NothingForStage("bar"))
                .go();
    }

    @Test
    public void duplicateEnvironment() throws Exception {
        expectError("duplicateEnvironment")
                .logContains(Messages.ModelParser_DuplicateEnvVar("FOO"))
                .go();
    }

    @Test
    public void duplicateStepParameter() throws Exception {
        expectError("duplicateStepParameter")
                .logContains("Duplicate named parameter 'time' found")
                .go();
    }

    @Test
    public void emptyEnvironment() throws Exception {
        expectError("emptyEnvironment")
                .logContains(Messages.ModelValidatorImpl_NoEnvVars())
                .go();
    }

    @Test
    public void emptyAgent() throws Exception {
        expectError("emptyAgent")
                .logContains(Messages.ModelParser_InvalidSectionDefinition("agent"))
                .go();
    }

    @Test
    public void perStageConfigEmptyAgent() throws Exception {
        expectError("perStageConfigEmptyAgent")
                .logContains(Messages.ModelParser_InvalidStageSectionDefinition("agent"))
                .go();
    }

    @Test
    public void invalidBuildCondition() throws Exception {
        expectError("invalidBuildCondition")
                .logContains("MultipleCompilationErrorsException: startup failed:",
                        Messages.ModelValidatorImpl_InvalidBuildCondition("banana", BuildCondition.getOrderedConditionNames()))
                .go();
    }

    @Test
    public void emptyPostBuild() throws Exception {
        expectError("emptyPostBuild")
                .logContains(Messages.ModelValidatorImpl_EmptySection("post"))
                .go();
    }

    @Test
    public void duplicatePostBuildConditions() throws Exception {
        expectError("duplicatePostBuildConditions")
                .logContains(Messages.ModelValidatorImpl_DuplicateBuildCondition("always"))
                .go();
    }

    @Test
    public void unlistedToolType() throws Exception {
        expectError("unlistedToolType")
                .logContains("MultipleCompilationErrorsException: startup failed:",
                        Messages.ModelValidatorImpl_InvalidSectionType("tool", "banana", Tools.getAllowedToolTypes().keySet()))
                .go();
    }

    @Test
    public void notInstalledToolVersion() throws Exception {
        expectError("notInstalledToolVersion")
                .logContains(Messages.ModelValidatorImpl_NoToolVersion("maven", "apache-maven-3.0.2", "apache-maven-3.0.1"))
                .go();
    }

    @Test
    public void globalLibraryNonStepBody() throws Exception {
        initGlobalLibrary();

        // Test the case of a function with a body that doesn't consist of steps - this will fail.
        expectError("globalLibraryNonStepBody")
                .logContains("MultipleCompilationErrorsException: startup failed:",
                        Messages.ModelParser_ExpectedStep())
                .go();
    }

    @Test
    public void globalLibraryObjectMethodCall() throws Exception {
        initGlobalLibrary();

        // Test the case of calling a method on an object, i.e., foo.bar(1). This will fail.
        expectError("globalLibraryObjectMethodCall")
                .logContains("MultipleCompilationErrorsException: startup failed:",
                        Messages.ModelParser_ObjectMethodCall())
                .go();
    }

    @Test
    public void unknownAgentType() throws Exception {
        expectError("unknownAgentType")
                .logContains(Messages.ModelValidatorImpl_InvalidAgentType("foo", "[otherField, docker, dockerfile, label, any, none]"))
                .go();
    }

    @Test
    public void missingAgentType() throws Exception {
        expectError("missingAgentType")
                .logContains(Messages.ModelValidatorImpl_NoAgentType("[otherField, docker, dockerfile, label, any, none]"))
                .go();
    }

    @Test
    public void unknownBareAgentType() throws Exception {
        expectError("unknownBareAgentType")
                .logContains(Messages.ModelParser_InvalidAgent())
                .go();
    }

    @Test
    public void agentMissingRequiredParam() throws Exception {
        expectError("agentMissingRequiredParam")
                .logContains(Messages.ModelValidatorImpl_MultipleAgentParameters("otherField", "[label, otherField]"))
                .go();
    }

    @Test
    public void multipleAgentTypes() throws Exception {
        expectError("multipleAgentTypes")
                .logContains(Messages.ModelParser_OneAgentMax())
                .go();
    }

    @Test
    public void agentUnknownParamForType() throws Exception {
        expectError("agentUnknownParamForType")
                .logContains(Messages.ModelValidatorImpl_InvalidAgentParameter("fruit", "otherField", "[label, otherField, nested]"))
                .go();
    }

    @Test
    public void packageShouldNotSkipParsing() throws Exception {
        expectError("packageShouldNotSkipParsing")
                .logContains(Messages.ModelValidatorImpl_RequiredSection("agent"))
                .go();
    }

    @Test
    public void importAndFunctionShouldNotSkipParsing() throws Exception {
        expectError("importAndFunctionShouldNotSkipParsing")
                .logContains(Messages.ModelValidatorImpl_RequiredSection("agent"))
                .go();
    }

    @Test
    public void invalidWrapperType() throws Exception {
        expectError("invalidWrapperType")
                .logContains(Messages.ModelValidatorImpl_InvalidSectionType("option", "echo", Options.getAllowedOptionTypes().keySet()))
                .go();
    }

    @Test
    public void notificationsSectionRemoved() throws Exception {
        expectError("notificationsSectionRemoved")
                .logContains(Messages.ModelParser_RenamedNotifications())
                .go();
    }

    @Issue("JENKINS-41645")
    @Test
    public void invalidEnvironmentIdentifiers() throws Exception {
        expectError("envIdentifiersCaughtInternally")
                .runFromRepo(false)
                .logContains(Messages.ModelValidatorImpl_InvalidIdentifierInEnv("1BAR"),
                        Messages.ModelValidatorImpl_InvalidIdentifierInEnv("$DOLLAR"))
                .go();

        expectError("envIdentifierString")
                .runFromRepo(false)
                .logContains("[heyLook] is a constant expression, but it should be a variable expression")
                .go();

        expectError("envIdentifierHyphens")
                .runFromRepo(false)
                .logContains("(hey - look) is a binary expression, but it should be a variable expression")
                .go();

        expectError("envIdentifierDigitsUnderscore")
                .runFromRepo(false)
                .logContains("expecting token in range: '0'..'9', found 'A'")
                .go();
    }

    @Issue("JENKINS-39799")
    @Test
    public void badPostContent() throws Exception {
        expectError("badPostContent")
                .logContains("MultipleCompilationErrorsException: startup failed:",
                        Messages.ModelParser_InvalidBuildCondition(BuildCondition.getOrderedConditionNames()),
                        Messages.ModelValidatorImpl_EmptySection("post"))
                .logNotContains("Caused by: java.lang.NullPointerException")
                .go();
    }

    @Test
    public void duplicateOptionAndTriggerNames() throws Exception {
        expectError("duplicateOptionAndTriggerNames")
                .logContains(Messages.ModelValidatorImpl_DuplicateOptionName("skipDefaultCheckout"),
                        Messages.ModelValidatorImpl_DuplicateTriggerName("cron"))
                .go();
    }

    @Issue("JENKINS-38110")
    @Test
    public void emptyLibrariesDirective() throws Exception {
        expectError("emptyLibrariesDirective")
                .logContains(Messages.ModelValidatorImpl_EmptySection("libraries"))
                .go();
    }

    @Issue("JENKINS-38110")
    @Test
    public void invalidLibrariesDirectiveContent() throws Exception {
        expectError("invalidLibrariesDirectiveContent")
                .logContains(Messages.ModelValidatorImpl_EmptySection("libraries"),
                        Messages.ModelParser_ExpectedLibrary("\"oh hi there\""),
                        Messages.ModelParser_ExpectedLibrary("foo('bar')"),
                        Messages.ModelParser_ExpectedLibrary("1 + 2"))
                .go();
    }

    @Issue("JENKINS-42550")
    @Test
    public void undefinedSectionReferencesCorrectly() throws Exception {
        expectError("undefinedSectionReferencesCorrectly")
                .logContains(Messages.Parser_UndefinedSection("node"),
                        "node {")
                .logNotContains("pipeline {")
                .go();
    }

    @Test
    public void parallelStagesAndSteps() throws Exception {
        expectError("parallelStagesAndSteps")
                .logContains(Messages.ModelValidatorImpl_BothStagesAndSteps("foo"))
                .go();
    }

    @Test
    public void parallelStagesAgentTools() throws Exception {
        expectError("parallelStagesAgentTools")
                .logContains(Messages.ModelValidatorImpl_AgentInNestedStages("foo"),
                        Messages.ModelValidatorImpl_ToolsInNestedStages("foo"))
                .go();
    }
}
