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
import jenkins.util.VirtualFile;
import org.apache.commons.io.IOUtils;
import org.jenkinsci.plugins.workflow.actions.NotExecutedNodeAction;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.graph.FlowGraphWalker;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author Andrew Bayer
 */
public class BasicModelDefTest extends AbstractModelDefTest {

    @Test
    public void simplePipeline() throws Exception {
        prepRepoWithJenkinsfile("simplePipeline");

        WorkflowRun b = getAndStartBuild();
        j.assertBuildStatusSuccess(j.waitForCompletion(b));
        j.assertLogContains("[Pipeline] { (foo)", b);
        j.assertLogNotContains("[Pipeline] { (Post Build Actions)", b);
        j.assertLogNotContains("[Pipeline] { (Notifications)", b);
        j.assertLogContains("hello", b);
    }

    @Test
    public void failingPipeline() throws Exception {
        prepRepoWithJenkinsfile("failingPipeline");

        WorkflowRun b = getAndStartBuild();
        j.assertBuildStatus(Result.FAILURE, j.waitForCompletion(b));
        j.assertLogContains("[Pipeline] { (foo)", b);
        j.assertLogContains("[Pipeline] { (Post Build Actions)", b);
        j.assertLogContains("[Pipeline] { (Notifications)", b);
        j.assertLogContains("hello", b);
        j.assertLogContains("goodbye", b);
        j.assertLogContains("farewell", b);
        assertTrue(b.getExecution().getCauseOfFailure() != null);
    }

    @Test
    public void failingPostBuild() throws Exception {
        prepRepoWithJenkinsfile("failingPostBuild");

        WorkflowRun b = getAndStartBuild();
        j.assertBuildStatus(Result.FAILURE, j.waitForCompletion(b));
        j.assertLogContains("[Pipeline] { (foo)", b);
        j.assertLogContains("[Pipeline] { (Post Build Actions)", b);
        j.assertLogContains("[Pipeline] { (Notifications)", b);
        j.assertLogContains("hello", b);
        j.assertLogContains("goodbye", b);
        j.assertLogContains("farewell", b);
        assertTrue(b.getExecution().getCauseOfFailure() != null);
    }

    @Test
    public void failingNotifications() throws Exception {
        prepRepoWithJenkinsfile("failingNotifications");

        WorkflowRun b = getAndStartBuild();
        j.assertBuildStatus(Result.FAILURE, j.waitForCompletion(b));
        j.assertLogContains("[Pipeline] { (foo)", b);
        j.assertLogContains("[Pipeline] { (Post Build Actions)", b);
        j.assertLogContains("[Pipeline] { (Notifications)", b);
        j.assertLogContains("hello", b);
        j.assertLogContains("goodbye", b);
        j.assertLogContains("farewell", b);
        assertTrue(b.getExecution().getCauseOfFailure() != null);
    }

    @Test
    public void twoStagePipeline() throws Exception {
        prepRepoWithJenkinsfile("twoStagePipeline");

        WorkflowRun b = getAndStartBuild();
        j.assertBuildStatusSuccess(j.waitForCompletion(b));
        j.assertLogContains("[Pipeline] { (foo)", b);
        j.assertLogContains("hello", b);
        j.assertLogContains("[Pipeline] { (bar)", b);
        j.assertLogContains("goodbye", b);
    }

    @Issue("JENKINS-38097")
    @Test
    public void allStagesExist() throws Exception {
        prepRepoWithJenkinsfile("allStagesExist");

        WorkflowRun b = getAndStartBuild();
        j.assertBuildStatus(Result.FAILURE, j.waitForCompletion(b));
        j.assertLogContains("[Pipeline] { (foo)", b);
        j.assertLogContains("hello", b);
        j.assertLogContains("[Pipeline] { (bar)", b);
        FlowExecution execution = b.getExecution();
        FlowGraphWalker walker = new FlowGraphWalker(execution);
        boolean foundSkippedBar = false;
        boolean foundExecutedFoo = false;

        for (FlowNode node : walker) {
            if (node.getDisplayName().equals("bar") && node.getAction(NotExecutedNodeAction.class) != null) {
                foundSkippedBar = true;
            }
            if (node.getDisplayName().equals("foo") && node.getAction(NotExecutedNodeAction.class) == null) {
                foundExecutedFoo = true;
            }
        }

        assertTrue("Executed stage foo either doesn't exist or is marked as skipped", foundExecutedFoo);
        assertTrue("Post-failure stage bar either doesn't exist or isn't marked as skipped", foundSkippedBar);
    }

    @Test
    public void validStepParameters() throws Exception {
        prepRepoWithJenkinsfile("validStepParameters");

        WorkflowRun b = getAndStartBuild();
        j.assertBuildStatusSuccess(j.waitForCompletion(b));
        j.assertLogContains("[Pipeline] { (foo)", b);
        j.assertLogContains("[Pipeline] timeout", b);
        j.assertLogContains("hello", b);
    }

    @Test
    public void metaStepSyntax() throws Exception {
        prepRepoWithJenkinsfile("metaStepSyntax");

        DumbSlave s = j.createOnlineSlave();
        s.setLabelString("some-label");
        s.getNodeProperties().add(new EnvironmentVariablesNodeProperty(new EnvironmentVariablesNodeProperty.Entry("ONSLAVE", "true")));

        WorkflowRun b = getAndStartBuild();
        j.assertBuildStatusSuccess(j.waitForCompletion(b));
        j.assertLogContains("[Pipeline] { (foo)", b);
        j.assertLogContains("ONSLAVE=true", b);

        VirtualFile archivedFile = b.getArtifactManager().root().child("msg.out");
        assertTrue(archivedFile.exists());
        assertEquals("hello world", IOUtils.toString(archivedFile.open()));
    }

    @Test
    public void legacyMetaStepSyntax() throws Exception {
        prepRepoWithJenkinsfile("legacyMetaStepSyntax");

        DumbSlave s = j.createOnlineSlave();
        s.setLabelString("some-label");
        s.getNodeProperties().add(new EnvironmentVariablesNodeProperty(new EnvironmentVariablesNodeProperty.Entry("ONSLAVE", "true")));

        WorkflowRun b = getAndStartBuild();
        j.assertBuildStatusSuccess(j.waitForCompletion(b));
        j.assertLogContains("[Pipeline] { (foo)", b);
        j.assertLogContains("ONSLAVE=true", b);

        VirtualFile archivedFile = b.getArtifactManager().root().child("msg.out");
        assertTrue(archivedFile.exists());
        assertEquals("hello world", IOUtils.toString(archivedFile.open()));
    }

    @Test
    public void parallelPipeline() throws Exception {
        prepRepoWithJenkinsfile("parallelPipeline");

        WorkflowRun b = getAndStartBuild();
        j.assertBuildStatusSuccess(j.waitForCompletion(b));
        j.assertLogContains("[Pipeline] { (foo)", b);
        j.assertLogContains("[first] { (Branch: first)", b);
        j.assertLogContains("[second] { (Branch: second)", b);
    }

    @Test
    public void dockerGlobalVariable() throws Exception {
        assumeDocker();
        // Bind mounting /var on OS X doesn't work at the moment
        onAllowedOS(PossibleOS.LINUX);
        prepRepoWithJenkinsfile("dockerGlobalVariable");

        DumbSlave s = j.createOnlineSlave();
        s.setLabelString("some-label");
        s.getNodeProperties().add(new EnvironmentVariablesNodeProperty(new EnvironmentVariablesNodeProperty.Entry("ONSLAVE", "true")));

        WorkflowRun b = getAndStartBuild();
        j.assertBuildStatusSuccess(j.waitForCompletion(b));
        j.assertLogContains("[Pipeline] { (foo)", b);
        j.assertLogContains("image: ubuntu", b);
    }

    @Test
    public void globalLibrarySuccess() throws Exception {

        // Test the successful, albeit limited, case.
        prepRepoWithJenkinsfile("globalLibrarySuccess");

        initGlobalLibrary();

        WorkflowRun b = getAndStartBuild();
        j.assertBuildStatusSuccess(j.waitForCompletion(b));

        j.assertLogContains("[nothing here]", b);
        j.assertLogContains("map call(1,2)", b);

        j.assertLogContains("closure1(1)", b);

        j.assertLogContains("running inside closure1", b);

        j.assertLogContains("closure2(1, 2)", b);
        j.assertLogContains("running inside closure2", b);

    }
}
