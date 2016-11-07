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
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.test.steps.SemaphoreStep;
import org.junit.Ignore;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

import java.util.Arrays;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.stringContainsInOrder;

/**
 * @author Andrew Bayer
 */
public class BuildConditionResponderTest extends AbstractModelDefTest {

    @Test
    public void simpleNotification() throws Exception {
        expect("simpleNotification")
                .logContains("[Pipeline] { (foo)",
                        "hello",
                        "[Pipeline] { (Notifications)",
                        "I HAVE FINISHED",
                        "MOST DEFINITELY FINISHED")
                .logNotContains("[Pipeline] { (Post Build Actions)", "I FAILED")
                .go();
    }

    @Test
    public void simplePostBuild() throws Exception {
        expect("simplePostBuild")
                .logContains("[Pipeline] { (foo)",
                        "hello",
                        "[Pipeline] { (Post Build Actions)",
                        "I HAVE FINISHED",
                        "MOST DEFINITELY FINISHED")
                .logNotContains("[Pipeline] { (Notifications)", "I FAILED")
                .go();
    }

    @Test
    public void postBuildAndNotifications() throws Exception {
        expect(Result.FAILURE, "postBuildAndNotifications")
                .logContains("[Pipeline] { (foo)",
                        "hello",
                        "[Pipeline] { (Notifications)",
                        "[Pipeline] { (Post Build Actions)",
                        "I AM FAILING",
                        "I HAVE FAILED")
                .logNotContains("I HAVE SUCCEEDED")
                .go();
    }

    @Test
    public void notificationOnChanged() throws Exception {
        WorkflowRun b = getAndStartNonRepoBuild("notificationOnChangeFailed");
        j.assertBuildStatus(Result.FAILURE, j.waitForCompletion(b));
        j.assertLogContains("[Pipeline] { (foo)", b);
        j.assertLogContains("I FAILED", b);

        WorkflowJob job = b.getParent();
        job.setDefinition(new CpsFlowDefinition(pipelineSourceFromResources("notificationOnChangeChanged")));
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
    public void unstableNotification() throws Exception {
        expect(Result.UNSTABLE, "unstableNotification")
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

    @Ignore("This no longer works with the JENKINS-38049 fix - needs to be re-evaluated")
    @Test
    public void abortedNotification() throws Exception {
        prepRepoWithJenkinsfile("abortedNotification");

        WorkflowRun b = getAndStartBuild();
        SemaphoreStep.waitForStart("wait/1", b);
        b.getExecutor().interrupt();
        j.assertBuildStatus(Result.ABORTED, j.waitForCompletion(b));

        j.assertLogContains("[Pipeline] { (foo)", b);
        j.assertLogContains("hello", b);
        j.assertLogContains("I ABORTED", b);
        j.assertLogNotContains("I FAILED", b);
    }

    @Test
    public void shInNotification() throws Exception {
        expect(Result.FAILURE, "shInNotification")
                .logContains("Attempted to execute a notification step that requires a node context. Notifications do not run inside a 'node { ... }' block.",
                        "Perhaps you forgot to surround the code with a step that provides this, such as: node")
                .go();
    }
}
