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

import com.gargoylesoftware.htmlunit.html.HtmlPage;
import hudson.model.Result;
import hudson.model.queue.QueueTaskFuture;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.cps.CpsFlowExecution;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.support.steps.input.InputAction;
import org.jenkinsci.plugins.workflow.support.steps.input.InputStepExecution;
import org.jenkinsci.plugins.workflow.test.steps.SemaphoreStep;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;

import static org.junit.Assert.assertEquals;

/**
 * @author Andrew Bayer
 */
public class BuildConditionResponderTest extends AbstractModelDefTest {

    @Test
    public void simplePostBuild() throws Exception {
        expect("simplePostBuild")
                .logContains("[Pipeline] { (foo)",
                        "hello",
                        "[Pipeline] { (" + SyntheticStageNames.postBuild() + ")",
                        "I HAVE FINISHED",
                        "MOST DEFINITELY FINISHED")
                .logNotContains("I FAILED")
                .go();
    }

    @Test
    public void postChecksAllConditions() throws Exception {
        expect(Result.FAILURE, "postChecksAllConditions")
                .logContains("[Pipeline] { (foo)",
                        "hello",
                        "[Pipeline] { (" + SyntheticStageNames.postBuild() + ")",
                        "I AM FAILING NOW",
                        "I FAILED")
                .logNotContains("MOST DEFINITELY FINISHED")
                .go();
    }

    @Test
    public void postOnChanged() throws Exception {
        WorkflowRun b = getAndStartNonRepoBuild("postOnChangeFailed");
        j.assertBuildStatus(Result.FAILURE, j.waitForCompletion(b));
        j.assertLogContains("[Pipeline] { (foo)", b);
        j.assertLogContains("I FAILED", b);

        WorkflowJob job = b.getParent();
        job.setDefinition(new CpsFlowDefinition(pipelineSourceFromResources("postOnChangeChanged")));
        WorkflowRun b2 = job.scheduleBuild2(0).waitForStart();
        j.assertBuildStatusSuccess(j.waitForCompletion(b2));
        j.assertLogContains("[Pipeline] { (foo)", b2);
        j.assertLogContains("hello", b2);
        j.assertLogContains("I CHANGED", b2);

        // Now make sure we don't get any alert this time.
        WorkflowRun b3 = job.scheduleBuild2(0).waitForStart();
        j.assertBuildStatusSuccess(j.waitForCompletion(b3));
        j.assertLogContains("[Pipeline] { (foo)", b3);
        j.assertLogContains("hello", b3);
        j.assertLogNotContains("I CHANGED", b3);
        j.assertLogNotContains("I FAILED", b3);
    }

    @Test
    public void unstablePost() throws Exception {
            expect(Result.UNSTABLE, "unstablePost")
                    .logContains("[Pipeline] { (foo)", "hello", "I AM UNSTABLE")
                    .logNotContains("I FAILED")
                    .go();
    }

    @Issue("JENKINS-38993")
    @Test
    public void buildConditionOrdering() throws Exception {
        expect("buildConditionOrdering")
                .logContains("[Pipeline] { (foo)", "hello")
                .inLogInOrder("I AM ALWAYS", "I CHANGED", "I SUCCEEDED")
                .go();
    }

    @Issue("JENKINS-43339")
    @Test
    public void notBuiltFlowInterruptedException() throws Exception {
        WorkflowJob job = j.jenkins.createProject(WorkflowJob.class, "milestone-flow-interrupted");
        job.setDefinition(new CpsFlowDefinition("" +
                "pipeline {\n" +
                "  agent none\n" +
                "  stages {\n" +
                "    stage('milestones') {\n" +
                "      steps {\n" +
                "        milestone(1)\n" +
                "        semaphore 'wait'\n" +
                "        milestone(2)\n" +
                "      }\n" +
                "      post {\n" +
                "        notBuilt {\n" +
                "          echo 'Job not built due to milestone'\n" +
                "        }\n" +
                "      }\n" +
                "    }\n" +
                "  }\n" +
                "}\n", true));

        WorkflowRun run1 = job.scheduleBuild2(0).waitForStart();
        SemaphoreStep.waitForStart("wait/1", run1);
        WorkflowRun run2 = job.scheduleBuild2(0).waitForStart();
        SemaphoreStep.waitForStart("wait/2", run2);

        SemaphoreStep.success("wait/2", null);

        j.waitForCompletion(run2);

        j.assertBuildStatus(Result.NOT_BUILT, j.waitForCompletion(run1));

        j.assertLogContains("Job not built due to milestone", run1);

        j.assertBuildStatusSuccess(j.waitForCompletion(run2));
        j.assertLogNotContains("Job not built due to milestone", run2);
    }

    @Issue("JENKINS-43339")
    @Test
    public void abortedFlowInterruptedException() throws Exception {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy().grant(Jenkins.ADMINISTER).everywhere().to("ops"));
        WorkflowJob job = j.jenkins.createProject(WorkflowJob.class, "input-flow-interrupted");
        job.setDefinition(new CpsFlowDefinition("" +
                "pipeline {\n" +
                "  agent none\n" +
                "  stages {\n" +
                "    stage('input') {\n" +
                "      steps {\n" +
                "        input(id: 'InputX', message: 'OK?', ok: 'Yes', submitter: 'ops')\n" +
                "      }\n" +
                "      post {\n" +
                "        aborted {\n" +
                "          echo 'Job aborted due to input'\n" +
                "        }\n" +
                "      }\n" +
                "    }\n" +
                "  }\n" +
                "}\n", true));

        QueueTaskFuture<WorkflowRun> queueTaskFuture = job.scheduleBuild2(0);
        WorkflowRun run = queueTaskFuture.getStartCondition().get();
        CpsFlowExecution execution = (CpsFlowExecution) run.getExecutionPromise().get();

        while (run.getAction(InputAction.class) == null) {
            execution.waitForSuspension();
        }

        JenkinsRule.WebClient webClient = j.createWebClient();

        webClient.login("ops");

        InputAction inputAction = run.getAction(InputAction.class);
        InputStepExecution is = inputAction.getExecution("InputX");
        HtmlPage p = webClient.getPage(run, inputAction.getUrlName());

        j.submit(p.getFormByName(is.getId()), "abort");
        assertEquals(0, inputAction.getExecutions().size());
        queueTaskFuture.get();

        j.assertBuildStatus(Result.ABORTED, j.waitForCompletion(run));

        j.assertLogContains("Job aborted due to input", run);
    }
}
