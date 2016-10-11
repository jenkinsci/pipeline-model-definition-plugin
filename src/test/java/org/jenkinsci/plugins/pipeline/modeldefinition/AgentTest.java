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
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;

/**
 * @author Andrew Bayer
 */
public class AgentTest extends AbstractModelDefTest {

    private static DumbSlave s;

    @BeforeClass
    public static void setUpAgent() throws Exception {
        s = j.createOnlineSlave();
        s.setLabelString("some-label docker");
        s.getNodeProperties().add(new EnvironmentVariablesNodeProperty(new EnvironmentVariablesNodeProperty.Entry("ONSLAVE", "true")));

    }

    @Test
    public void agentLabel() throws Exception {
        prepRepoWithJenkinsfile("agentLabel");

        WorkflowRun b = getAndStartBuild();
        j.assertBuildStatusSuccess(j.waitForCompletion(b));
        j.assertLogContains("[Pipeline] { (foo)", b);
        j.assertLogContains("ONSLAVE=true", b);
    }

    @Issue("JENKINS-37932")
    @Test
    public void agentAny() throws Exception {
        prepRepoWithJenkinsfile("agentAny");

        WorkflowRun b = getAndStartBuild();
        j.assertBuildStatusSuccess(j.waitForCompletion(b));
        j.assertLogContains("[Pipeline] { (foo)", b);
        j.assertLogContains("THIS WORKS", b);
    }

    @Test
    public void noCheckoutScmInWrongContext() throws Exception {
        WorkflowRun b = getAndStartNonRepoBuild("noCheckoutScmInWrongContext");
        j.assertBuildStatusSuccess(j.waitForCompletion(b));
        j.assertLogContains("[Pipeline] { (foo)", b);
        j.assertLogContains("ONSLAVE=true", b);
    }

    @Test
    public void agentDocker() throws Exception {
        final WorkflowRun b = agentDocker("agentDocker");
        j.assertLogContains("-v /tmp:/tmp -p 80:80", b);
    }

    @Test
    public void agentDockerWithNullDockerArgs() throws Exception {
        agentDocker("agentDockerWithNullDockerArgs");
    }

    @Test
    public void agentDockerWithEmptyDockerArgs() throws Exception {
        agentDocker("agentDockerWithEmptyDockerArgs");
    }

    @Test
    public void agentNone() throws Exception {
        prepRepoWithJenkinsfile("agentNone");

        WorkflowRun b = getAndStartBuild();
        j.assertBuildStatus(Result.FAILURE, j.waitForCompletion(b));

        j.assertLogContains("Attempted to execute a step that requires a node context while 'agent none' was specified. " +
                "Be sure to specify your own 'node { ... }' blocks when using 'agent none'.", b);

        // This message is printed straight to the build log so we can't prevent it from showing up.
        j.assertLogContains("Perhaps you forgot to surround the code with a step that provides this, such as: node", b);
    }

    @Test
    public void agentNoneWithNode() throws Exception {
        prepRepoWithJenkinsfile("agentNoneWithNode");

        WorkflowRun b = getAndStartBuild();
        j.assertBuildStatusSuccess(j.waitForCompletion(b));
        j.assertLogContains("[Pipeline] { (foo)", b);
        j.assertLogContains("ONSLAVE=true", b);
    }

    @Test
    public void perStageConfigAgent() throws Exception {
        prepRepoWithJenkinsfile("perStageConfigAgent");

        WorkflowRun b = getAndStartBuild();
        j.assertBuildStatusSuccess(j.waitForCompletion(b));
        j.assertLogContains("[Pipeline] { (foo)", b);
        j.assertLogContains("ONSLAVE=true", b);
    }

    private WorkflowRun agentDocker(final String jenkinsfile) throws Exception {
        assumeDocker();
        // Bind mounting /var on OS X doesn't work at the moment
        onAllowedOS(PossibleOS.LINUX);
        prepRepoWithJenkinsfile(jenkinsfile);

        WorkflowRun b = getAndStartBuild();
        j.assertBuildStatusSuccess(j.waitForCompletion(b));
        j.assertLogContains("[Pipeline] { (foo)", b);
        j.assertLogContains("The answer is 42", b);
        return b;
    }
}
