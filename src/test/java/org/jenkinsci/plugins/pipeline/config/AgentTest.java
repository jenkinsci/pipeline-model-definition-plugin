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
package org.jenkinsci.plugins.pipeline.config;

import hudson.model.Result;
import hudson.slaves.DumbSlave;
import hudson.slaves.EnvironmentVariablesNodeProperty;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Test;
import org.junit.runners.model.Statement;

/**
 * @author Andrew Bayer
 */
public class AgentTest extends AbstractConfigTest {

    @Test
    public void agentLabel() throws Exception {
        prepRepoWithJenkinsfile("agentLabel");

        DumbSlave s = j.createOnlineSlave();
        s.setLabelString("some-label");
        s.getNodeProperties().add(new EnvironmentVariablesNodeProperty(new EnvironmentVariablesNodeProperty.Entry("ONSLAVE", "true")));

        WorkflowRun b = getAndStartBuild();
        j.assertBuildStatusSuccess(j.waitForCompletion(b));
        j.assertLogContains("Entering stage foo", b);
        j.assertLogContains("ONSLAVE=true", b);
    }

    @Test
    public void noCheckoutScmInWrongContext() throws Exception {
        DumbSlave s = j.createOnlineSlave();
        s.setLabelString("some-label");
        s.getNodeProperties().add(new EnvironmentVariablesNodeProperty(new EnvironmentVariablesNodeProperty.Entry("ONSLAVE", "true")));

        WorkflowRun b = getAndStartNonRepoBuild("noCheckoutScmInWrongContext");
        j.assertBuildStatusSuccess(j.waitForCompletion(b));
        j.assertLogContains("Entering stage foo", b);
        j.assertLogContains("ONSLAVE=true", b);
    }

    @Test
    public void agentDocker() throws Exception {
        assumeDocker();
        // Bind mounting /var on OS X doesn't work at the moment
        onAllowedOS(PossibleOS.LINUX);
        prepRepoWithJenkinsfile("agentDocker");

        assumeDocker();
        DumbSlave s = j.createOnlineSlave();
        s.setLabelString("docker");

        WorkflowRun b = getAndStartBuild();
        j.assertBuildStatusSuccess(j.waitForCompletion(b));
        j.assertLogContains("Entering stage foo", b);
        j.assertLogContains("The answer is 42", b);
    }

    @Test
    public void agentNone() throws Exception {
        prepRepoWithJenkinsfile("agentNone");

        DumbSlave s = j.createOnlineSlave();
        s.setLabelString("some-label");
        s.getNodeProperties().add(new EnvironmentVariablesNodeProperty(new EnvironmentVariablesNodeProperty.Entry("ONSLAVE", "true")));

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

        DumbSlave s = j.createOnlineSlave();
        s.setLabelString("some-label");
        s.getNodeProperties().add(new EnvironmentVariablesNodeProperty(new EnvironmentVariablesNodeProperty.Entry("ONSLAVE", "true")));

        WorkflowRun b = getAndStartBuild();
        j.assertBuildStatusSuccess(j.waitForCompletion(b));
        j.assertLogContains("Entering stage foo", b);
        j.assertLogContains("ONSLAVE=true", b);
    }
}
