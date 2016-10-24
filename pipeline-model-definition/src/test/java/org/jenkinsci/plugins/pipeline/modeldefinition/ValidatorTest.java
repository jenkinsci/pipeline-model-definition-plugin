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
import hudson.slaves.EnvironmentVariablesNodeProperty;
import org.jenkinsci.plugins.pipeline.modeldefinition.model.BuildCondition;
import org.jenkinsci.plugins.pipeline.modeldefinition.model.Tools;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.BeforeClass;
import org.junit.Ignore;
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
        s.getNodeProperties().add(new EnvironmentVariablesNodeProperty(new EnvironmentVariablesNodeProperty.Entry("ONSLAVE", "true")));

    }

    @Issue("JENKINS-39011")
    @Test
    public void pipelineStepWithinOtherBlockFailure() throws Exception {
        prepRepoWithJenkinsfile("errors", "pipelineStepWithinOtherBlocksFailure");

        assertFailWithError("pipeline block must be at the top-level, not within another block");
    }

    @Test
    public void rejectStageInSteps() throws Exception {
        prepRepoWithJenkinsfile("errors", "rejectStageInSteps");

        assertFailWithError("Invalid step 'stage' used - not allowed in this context - The stage step cannot be used in Declarative Pipelines");
    }

    @Test
    public void rejectParallelMixedInSteps() throws Exception {
        prepRepoWithJenkinsfile("errors", "rejectParallelMixedInSteps");

        assertFailWithError("Invalid step 'parallel' used - not allowed in this context - The parallel step can only be used as the only top-level step in a stage's step block");
    }

    @Ignore("I still want to block parallel, but I'm not sure it's worth it, ignoring for now.")
    @Test
    public void rejectParallelInNotifications() throws Exception {
        prepRepoWithJenkinsfile("errors", "rejectParallelInNotifications");

        WorkflowRun b = getAndStartBuild();
        j.assertBuildStatus(Result.FAILURE, j.waitForCompletion(b));
        j.assertLogContains("Illegal Pipeline steps used in inline Pipeline - parallel", b);
    }

    @Test
    public void emptyStages() throws Exception {
        prepRepoWithJenkinsfile("errors", "emptyStages");

        assertFailWithError("No stages specified");
    }

    @Test
    public void emptyJobProperties() throws Exception {
        prepRepoWithJenkinsfile("errors", "emptyJobProperties");

        assertFailWithError("Cannot have empty jobProperties section");
    }

    @Test
    public void emptyParameters() throws Exception {
        prepRepoWithJenkinsfile("errors", "emptyParameters");

        assertFailWithError("Cannot have empty parameters section");
    }

    @Test
    public void emptyTriggers() throws Exception {
        prepRepoWithJenkinsfile("errors", "emptyTriggers");

        assertFailWithError("Cannot have empty triggers section");
    }

    @Test
    public void blockInJobProperties() throws Exception {
        prepRepoWithJenkinsfile("errors", "blockInJobProperties");

        assertFailWithError("Job property definitions cannot have blocks");
    }

    @Test
    public void blockInParameters() throws Exception {
        prepRepoWithJenkinsfile("errors", "blockInParameters");

        assertFailWithError("Build parameter definitions cannot have blocks");
    }

    @Test
    public void blockInTriggers() throws Exception {
        prepRepoWithJenkinsfile("errors", "blockInTriggers");

        assertFailWithError("Trigger definitions cannot have blocks");
    }

    @Test
    public void mixedMethodArgs() throws Exception {
        prepRepoWithJenkinsfile("errors", "mixedMethodArgs");

        assertFailWithError("Can't mix named and unnamed parameter definition arguments");
    }

    @Test
    public void closureAsMethodCallArg() throws Exception {
        prepRepoWithJenkinsfile("errors", "closureAsMethodCallArg");

        assertFailWithError("Method call arguments cannot use closures");
    }

    @Test
    public void tooFewMethodCallArgs() throws Exception {
        prepRepoWithJenkinsfile("errors", "tooFewMethodCallArgs");

        assertFailWithError("'cron' should have 1 arguments but has 0 arguments instead");
    }

    @Test
    public void wrongParameterNameMethodCall() throws Exception {
        prepRepoWithJenkinsfile("errors", "wrongParameterNameMethodCall");

        assertFailWithError("Invalid parameter 'namd', did you mean 'name'?");
    }

    @Test
    public void invalidParameterTypeMethodCall() throws Exception {
        prepRepoWithJenkinsfile("errors", "invalidParameterTypeMethodCall");

        assertFailWithError("Expecting class java.lang.String for parameter 'name' but got '1234' instead");
    }

    @Test
    public void rejectPropertiesStepInMethodCall() throws Exception {
        prepRepoWithJenkinsfile("errors", "rejectPropertiesStepInMethodCall");

        assertFailWithError("Invalid step 'properties' used - not allowed in this context - The properties step cannot be used in Declarative Pipelines");
    }

    @Test
    public void rejectMapsForTriggerDefinition() throws Exception {
        prepRepoWithJenkinsfile("errors", "rejectMapsForTriggerDefinition");

        assertFailWithError("Triggers cannot be defined as maps");
    }

    @Test
    public void emptyParallel() throws Exception {
        prepRepoWithJenkinsfile("errors", "emptyParallel");

        assertFailWithError("Nothing to execute within stage 'foo'");
    }

    @Test
    public void missingAgent() throws Exception {
        prepRepoWithJenkinsfile("errors", "missingAgent");

        assertFailWithError("Missing required section 'agent'");
    }

    @Test
    public void missingStages() throws Exception {
        prepRepoWithJenkinsfile("errors", "missingStages");

        assertFailWithError("Missing required section 'stages'");
    }

    @Test
    public void missingRequiredStepParameters() throws Exception {
        prepRepoWithJenkinsfile("errors", "missingRequiredStepParameters");

        assertFailWithError("Missing required parameter: 'time'");
    }

    @Test
    public void invalidStepParameterType() throws Exception {
        prepRepoWithJenkinsfile("errors", "invalidStepParameterType");

        assertFailWithError("Expecting int for parameter 'time' but got 'someTime' instead");
    }

    @Test
    public void unknownStepParameter() throws Exception {
        prepRepoWithJenkinsfile("errors", "unknownStepParameter");

        assertFailWithError("Invalid parameter 'banana', did you mean 'unit'?");
    }

    @Test
    public void perStageConfigEmptySteps() throws Exception {
        prepRepoWithJenkinsfile("errors", "perStageConfigEmptySteps");

        assertFailWithError("No steps specified for branch");
    }

    @Test
    public void perStageConfigMissingSteps() throws Exception {
        prepRepoWithJenkinsfile("errors", "perStageConfigMissingSteps");

        assertFailWithError("Nothing to execute within stage");
    }

    @Test
    public void perStageConfigUnknownSection() throws Exception {
        prepRepoWithJenkinsfile("errors", "perStageConfigUnknownSection");

        assertFailWithError("Unknown stage section 'banana'");
    }

    @Test
    public void invalidMetaStepSyntax() throws Exception {
        prepRepoWithJenkinsfile("errors", "invalidMetaStepSyntax");

        assertFailWithError("Invalid parameter 'someRandomField', did you mean 'caseSensitive'?");
    }

    @Test
    public void duplicateStageNames() throws Exception {
        prepRepoWithJenkinsfile("errors", "duplicateStageNames");

        assertFailWithError("Duplicate stage name: 'foo'", "Nothing to execute within stage 'bar'");
    }

    @Test
    public void duplicateEnvironment() throws Exception {
        prepRepoWithJenkinsfile("errors", "duplicateEnvironment");

        assertFailWithError("Duplicate environment variable name: 'FOO'");
    }

    @Test
    public void duplicateStepParameter() throws Exception {
        prepRepoWithJenkinsfile("errors", "duplicateStepParameter");

        assertFailWithError("Duplicate named parameter 'time' found");
    }

    @Test
    public void emptyEnvironment() throws Exception {
        prepRepoWithJenkinsfile("errors", "emptyEnvironment");

        assertFailWithError("No variables specified for environment");
    }

    @Test
    public void emptyAgent() throws Exception {
        prepRepoWithJenkinsfile("errors", "emptyAgent");

        assertFailWithError("Not a valid section definition: 'agent'. Some extra configuration is required.");
    }

    @Test
    public void perStageConfigEmptyAgent() throws Exception {
        prepRepoWithJenkinsfile("errors", "perStageConfigEmptyAgent");

        assertFailWithError("Not a valid stage section definition: 'agent'. Some extra configuration is required.");
    }

    @Test
    public void invalidBuildCondition() throws Exception {
        prepRepoWithJenkinsfile("errors", "invalidBuildCondition");

        WorkflowRun b = getAndStartBuild();
        j.assertBuildStatus(Result.FAILURE, j.waitForCompletion(b));
        j.assertLogContains("MultipleCompilationErrorsException: startup failed:", b);
        // Note that we need to generate the error string here within the story or it comes out empty due to lack of a Jenkins instance.
        j.assertLogContains("Invalid condition 'banana' - valid conditions are " + BuildCondition.getConditionMethods().keySet(), b);
    }

    @Test
    public void emptyNotification() throws Exception {
        prepRepoWithJenkinsfile("errors", "emptyNotifications");

        assertFailWithError("notifications can not be empty");
    }

    @Test
    public void emptyPostBuild() throws Exception {
        prepRepoWithJenkinsfile("errors", "emptyPostBuild");

        assertFailWithError("postBuild can not be empty");
    }

    @Test
    public void duplicateNotificationConditions() throws Exception {
        prepRepoWithJenkinsfile("errors", "duplicateNotificationConditions");

        assertFailWithError("Duplicate build condition name: 'always'");
    }

    @Test
    public void duplicatePostBuildConditions() throws Exception {
        prepRepoWithJenkinsfile("errors", "duplicatePostBuildConditions");

        assertFailWithError("Duplicate build condition name: 'always'");
    }

    @Test
    public void unlistedToolType() throws Exception {
        prepRepoWithJenkinsfile("errors", "unlistedToolType");

        WorkflowRun b = getAndStartBuild();
        j.assertBuildStatus(Result.FAILURE, j.waitForCompletion(b));
        j.assertLogContains("MultipleCompilationErrorsException: startup failed:", b);
        // Note that we need to generate the error string here within the story or it comes out empty due to lack of a Jenkins instance.
        j.assertLogContains("Invalid tool type 'banana'. Valid tool types: " + Tools.getAllowedToolTypes().keySet(), b);
    }

    @Test
    public void notInstalledToolVersion() throws Exception {
        prepRepoWithJenkinsfile("errors", "notInstalledToolVersion");

        assertFailWithError("Tool type 'maven' does not have an install of 'apache-maven-3.0.2' configured - did you mean 'apache-maven-3.0.1'?");
    }

    @Test
    public void globalLibraryNonStepBody() throws Exception {
        // Test the case of a function with a body that doesn't consist of steps - this will fail.
        prepRepoWithJenkinsfile("errors", "globalLibraryNonStepBody");

        initGlobalLibrary();

        WorkflowRun b = getAndStartBuild();

        j.assertBuildStatus(Result.FAILURE, j.waitForCompletion(b));
        j.assertLogContains("MultipleCompilationErrorsException: startup failed:", b);
        j.assertLogContains("Expected a step @ line", b);
    }

    @Test
    public void globalLibraryObjectMethodCall() throws Exception {
        // Test the case of calling a method on an object, i.e., foo.bar(1). This will fail.
        prepRepoWithJenkinsfile("errors", "globalLibraryObjectMethodCall");

        initGlobalLibrary();

        WorkflowRun b = getAndStartBuild();

        j.assertBuildStatus(Result.FAILURE, j.waitForCompletion(b));
        j.assertLogContains("MultipleCompilationErrorsException: startup failed:", b);
        j.assertLogContains("Expected a symbol @ line", b);

    }

    @Test
    public void unknownAgentType() throws Exception {
        prepRepoWithJenkinsfile("errors", "unknownAgentType");

        assertFailWithError("No agent type specified. Must contain one of [otherField, docker, label, any, none]");
    }

    @Test
    public void unknownBareAgentType() throws Exception {
        prepRepoWithJenkinsfile("errors", "unknownBareAgentType");

        assertFailWithError("Invalid argument for agent - 'foo' - must be map of config options or bare [any, none]");
    }

    @Test
    public void agentMissingRequiredParam() throws Exception {
        prepRepoWithJenkinsfile("errors", "agentMissingRequiredParam");

        assertFailWithError("Missing required parameter for agent type 'otherField': label");
    }

    @Test
    public void agentUnknownParamForType() throws Exception {
        prepRepoWithJenkinsfile("errors", "agentUnknownParamForType");

        assertFailWithError("Invalid config option 'fruit' for agent type 'otherField'. Valid config options are [label, otherField]");
    }

    @Test
    public void packageShouldNotSkipParsing() throws Exception {
        prepRepoWithJenkinsfile("errors", "packageShouldNotSkipParsing");

        assertFailWithError("Missing required section 'agent'");
    }

    @Test
    public void importAndFunctionShouldNotSkipParsing() throws Exception {
        prepRepoWithJenkinsfile("errors", "importAndFunctionShouldNotSkipParsing");

        assertFailWithError("Missing required section 'agent'");
    }

    private void assertFailWithError(final String... errors) throws Exception {
        WorkflowRun b = getAndStartBuild();
        j.assertBuildStatus(Result.FAILURE, j.waitForCompletion(b));
        j.assertLogContains("MultipleCompilationErrorsException: startup failed:", b);
        for (String error : errors) {
            j.assertLogContains(error, b);
        }
    }
}
