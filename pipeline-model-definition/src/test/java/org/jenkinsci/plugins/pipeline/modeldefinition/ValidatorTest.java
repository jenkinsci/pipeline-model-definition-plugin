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

import hudson.model.Result;
import hudson.slaves.DumbSlave;
import org.jenkinsci.plugins.pipeline.modeldefinition.model.BuildCondition;
import org.jenkinsci.plugins.pipeline.modeldefinition.model.JobProperties;
import org.jenkinsci.plugins.pipeline.modeldefinition.model.Parameters;
import org.jenkinsci.plugins.pipeline.modeldefinition.model.Tools;
import org.jenkinsci.plugins.pipeline.modeldefinition.model.Triggers;
import org.jenkinsci.plugins.pipeline.modeldefinition.model.Wrappers;
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
                .logContains("pipeline block must be at the top-level, not within another block")
                .go();
    }

    @Test
    public void rejectStageInSteps() throws Exception {
        expectError("rejectStageInSteps")
                .logContains("Invalid step 'stage' used - not allowed in this context - The stage step cannot be used in Declarative Pipelines")
                .go();
    }

    @Test
    public void rejectParallelMixedInSteps() throws Exception {
        expectError("rejectParallelMixedInSteps")
                .logContains("Invalid step 'parallel' used - not allowed in this context - The parallel step can only be used as the only top-level step in a stage's step block")
                .go();
    }

    @Test
    public void emptyStages() throws Exception {
        expectError("emptyStages")
                .logContains("No stages specified")
                .go();
    }

    @Test
    public void emptyJobProperties() throws Exception {
        expectError("emptyJobProperties")
                .logContains("Cannot have empty properties section")
                .go();
    }

    @Test
    public void emptyParameters() throws Exception {
        expectError("emptyParameters")
                .logContains("Cannot have empty parameters section")
                .go();
    }

    @Test
    public void emptyTriggers() throws Exception {
        expectError("emptyTriggers")
                .logContains("Cannot have empty triggers section")
                .go();
    }

    @Test
    public void blockInJobProperties() throws Exception {
        expectError("blockInJobProperties")
                .logContains("Job property definitions cannot have blocks")
                .go();
    }

    @Test
    public void blockInParameters() throws Exception {
        expectError("blockInParameters")
                .logContains("Build parameter definitions cannot have blocks")
                .go();
    }

    @Test
    public void blockInTriggers() throws Exception {
        expectError("blockInTriggers")
                .logContains("Trigger definitions cannot have blocks")
                .go();
    }

    @Test
    public void mixedMethodArgs() throws Exception {
        expectError("mixedMethodArgs")
                .logContains("Can't mix named and unnamed parameter definition arguments")
                .go();
    }

    @Test
    public void closureAsMethodCallArg() throws Exception {
        expectError("closureAsMethodCallArg")
                .logContains("Method call arguments cannot use closures")
                .go();
    }

    @Test
    public void tooFewMethodCallArgs() throws Exception {
        expectError("tooFewMethodCallArgs")
                .logContains("'cron' should have 1 arguments but has 0 arguments instead")
                .go();
    }

    @Test
    public void wrongParameterNameMethodCall() throws Exception {
        expectError("wrongParameterNameMethodCall")
                .logContains("Invalid parameter 'namd', did you mean 'name'?")
                .go();
    }

    @Test
    public void invalidParameterTypeMethodCall() throws Exception {
        expectError("invalidParameterTypeMethodCall")
                .logContains("Expecting class java.lang.String for parameter 'name' but got '1234' instead")
                .go();
    }

    @Test
    public void rejectPropertiesStepInMethodCall() throws Exception {
        expectError("rejectPropertiesStepInMethodCall")
                .logContains("Invalid step 'properties' used - not allowed in this context - The properties step cannot be used in Declarative Pipelines")
                .go();
    }

    @Test
    public void rejectMapsForTriggerDefinition() throws Exception {
        expectError("rejectMapsForTriggerDefinition")
                .logContains("Triggers cannot be defined as maps")
                .go();
    }

    @Test
    public void emptyParallel() throws Exception {
        expectError("emptyParallel")
                .logContains("Nothing to execute within stage 'foo'")
                .go();
    }

    @Test
    public void parallelPipelineWithInvalidFailFast() throws Exception {
        expectError("parallelPipelineWithInvalidFailFast")
                .logContains("Expected a boolean with failFast")
                .go();
    }

    @Test
    public void parallelPipelineWithInvalidExtraKey() throws Exception {
        expectError("parallelPipelineWithInvalidExtraKey")
                .logContains("Expected closure or failFast")
                .go();
    }

    @Test
    public void missingAgent() throws Exception {
        expectError("missingAgent")
                .logContains("Missing required section 'agent'")
                .go();
    }

    @Test
    public void missingStages() throws Exception {
        expectError("missingStages")
                .logContains("Missing required section 'stages'")
                .go();
    }

    @Test
    public void missingRequiredStepParameters() throws Exception {
        expectError("missingRequiredStepParameters")
                .logContains("Missing required parameter: 'time'")
                .go();
    }

    @Test
    public void invalidStepParameterType() throws Exception {
        expectError("invalidStepParameterType")
                .logContains("Expecting int for parameter 'time' but got 'someTime' instead")
                .go();
    }

    @Test
    public void invalidTriggerType() throws Exception {
        expectError("invalidTriggerType")
                .logContains("Invalid trigger type 'banana'. Valid trigger types: " + Triggers.getAllowedTriggerTypes().keySet())
                .go();
    }

    @Test
    public void invalidParameterType() throws Exception {
        expectError("invalidParameterType")
                .logContains("Invalid parameter definition type 'bananaParam'. Valid parameter definition types: "
                        + Parameters.getAllowedParameterTypes().keySet())
                .go();
    }

    @Test
    public void invalidPropertiesType() throws Exception {
        expectError("invalidPropertiesType")
                .logContains("Invalid job property type 'banana'. Valid job property types: "
                        + JobProperties.getAllowedPropertyTypes().keySet())
                .go();
    }

    @Test
    public void unknownStepParameter() throws Exception {
        expectError("unknownStepParameter")
                .logContains("Invalid parameter 'banana', did you mean 'unit'?")
                .go();
    }

    @Test
    public void perStageConfigEmptySteps() throws Exception {
        expectError("perStageConfigEmptySteps")
                .logContains("No steps specified for branch")
                .go();
    }

    @Test
    public void perStageConfigMissingSteps() throws Exception {
        expectError("perStageConfigMissingSteps")
                .logContains("Nothing to execute within stage")
                .go();
    }

    @Test
    public void perStageConfigUnknownSection() throws Exception {
        expectError("perStageConfigUnknownSection")
                .logContains("Unknown stage section 'banana'")
                .go();
    }

    @Test
    public void invalidMetaStepSyntax() throws Exception {
        expectError("invalidMetaStepSyntax")
                .logContains("Invalid parameter 'someRandomField', did you mean 'caseSensitive'?")
                .go();
    }

    @Test
    public void duplicateStageNames() throws Exception {
        expectError("duplicateStageNames")
                .logContains("Duplicate stage name: 'foo'", "Nothing to execute within stage 'bar'")
                .go();
    }

    @Test
    public void duplicateEnvironment() throws Exception {
        expectError("duplicateEnvironment")
                .logContains("Duplicate environment variable name: 'FOO'")
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
                .logContains("No variables specified for environment")
                .go();
    }

    @Test
    public void emptyAgent() throws Exception {
        expectError("emptyAgent")
                .logContains("Not a valid section definition: 'agent'. Some extra configuration is required.")
                .go();
    }

    @Test
    public void perStageConfigEmptyAgent() throws Exception {
        expectError("perStageConfigEmptyAgent")
                .logContains("Not a valid stage section definition: 'agent'. Some extra configuration is required.")
                .go();
    }

    @Test
    public void invalidBuildCondition() throws Exception {
        expectError("invalidBuildCondition")
                .logContains("MultipleCompilationErrorsException: startup failed:",
                        "Invalid condition 'banana' - valid conditions are " + BuildCondition.getOrderedConditionNames())
                .go();
    }

    @Test
    public void emptyPostBuild() throws Exception {
        expectError("emptyPostBuild")
                .logContains("post can not be empty")
                .go();
    }

    @Test
    public void duplicatePostBuildConditions() throws Exception {
        expectError("duplicatePostBuildConditions")
                .logContains("Duplicate build condition name: 'always'")
                .go();
    }

    @Test
    public void unlistedToolType() throws Exception {
        expectError("unlistedToolType")
                .logContains("MultipleCompilationErrorsException: startup failed:",
                        "Invalid tool type 'banana'. Valid tool types: " + Tools.getAllowedToolTypes().keySet())
                .go();
    }

    @Test
    public void notInstalledToolVersion() throws Exception {
        expectError("notInstalledToolVersion")
                .logContains("Tool type 'maven' does not have an install of 'apache-maven-3.0.2' configured - did you mean 'apache-maven-3.0.1'?")
                .go();
    }

    @Test
    public void globalLibraryNonStepBody() throws Exception {
        initGlobalLibrary();

        // Test the case of a function with a body that doesn't consist of steps - this will fail.
        expectError("globalLibraryNonStepBody")
                .logContains("MultipleCompilationErrorsException: startup failed:",
                        "Expected a step @ line")
                .go();
    }

    @Test
    public void globalLibraryObjectMethodCall() throws Exception {
        initGlobalLibrary();

        // Test the case of calling a method on an object, i.e., foo.bar(1). This will fail.
        expectError("globalLibraryObjectMethodCall")
                .logContains("MultipleCompilationErrorsException: startup failed:",
                        "Expected a symbol @ line")
                .go();
    }

    @Test
    public void unknownAgentType() throws Exception {
        expectError("unknownAgentType")
                .logContains("No agent type specified. Must contain one of [otherField, docker, dockerfile, label, any, none]")
                .go();
    }

    @Test
    public void unknownBareAgentType() throws Exception {
        expectError("unknownBareAgentType")
                .logContains("Invalid argument for agent - 'foo' - must be map of config options or bare [any, none]")
                .go();
    }

    @Test
    public void agentMissingRequiredParam() throws Exception {
        expectError("agentMissingRequiredParam")
                .logContains("Missing required parameter for agent type 'otherField': label")
                .go();
    }

    @Test
    public void agentUnknownParamForType() throws Exception {
        expectError("agentUnknownParamForType")
                .logContains("Invalid config option 'fruit' for agent type 'otherField'. Valid config options are [label, otherField]")
                .go();
    }

    @Test
    public void packageShouldNotSkipParsing() throws Exception {
        expectError("packageShouldNotSkipParsing")
                .logContains("Missing required section 'agent'")
                .go();
    }

    @Test
    public void importAndFunctionShouldNotSkipParsing() throws Exception {
        expectError("importAndFunctionShouldNotSkipParsing")
                .logContains("Missing required section 'agent'")
                .go();
    }

    @Test
    public void invalidWrapperType() throws Exception {
        expectError("invalidWrapperType")
                .logContains("Invalid wrapper type 'echo'. Valid wrapper types: " + Wrappers.getEligibleSteps())
                .go();
    }

    @Test
    public void notificationsSectionRemoved() throws Exception {
        expectError("notificationsSectionRemoved")
                .logContains("The 'notifications' section has been removed as of version 0.6. Use 'post' for all post-build actions.")
                .go();
    }

    @Issue("JENKINS-39799")
    @Test
    public void badPostContent() throws Exception {
        expectError("badPostContent")
                .logContains("MultipleCompilationErrorsException: startup failed:",
                        "The 'post' section can only contain build condition names with code blocks. "
                                + "Valid condition names are " + BuildCondition.getOrderedConditionNames(),
                        "post can not be empty")
                .logNotContains("Caused by: java.lang.NullPointerException")
                .go();
    }
}
