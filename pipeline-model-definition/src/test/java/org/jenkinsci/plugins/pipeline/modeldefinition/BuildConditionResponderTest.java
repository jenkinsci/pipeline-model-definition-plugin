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
import org.junit.Test;
import org.jvnet.hudson.test.Issue;

/**
 * @author Andrew Bayer
 */
public class BuildConditionResponderTest extends AbstractModelDefTest {

    @Test
    public void simplePostBuild() throws Exception {
        expect("simplePostBuild")
                .logContains("[Pipeline] { (foo)",
                        "hello",
                        "[Pipeline] { (Post Build Actions)",
                        "I HAVE FINISHED",
                        "MOST DEFINITELY FINISHED")
                .logNotContains("I FAILED")
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
}
