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
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Test;

/**
 * @author Andrew Bayer
 */
public class EnvironmentTest extends AbstractModelDefTest {

    @Test
    public void simpleEnvironment() throws Exception {
        prepRepoWithJenkinsfile("simpleEnvironment");

        DumbSlave s = j.createOnlineSlave();
        s.setLabelString("some-label");

        WorkflowRun b = getAndStartBuild();
        j.assertBuildStatusSuccess(j.waitForCompletion(b));
        j.assertLogContains("[Pipeline] { (foo)", b);
        j.assertLogContains("FOO is BAR", b);
    }

    @Test
    public void nonLiteralEnvironment() throws Exception {
        prepRepoWithJenkinsfile("nonLiteralEnvironment");

        DumbSlave s = j.createOnlineSlave();
        s.setLabelString("some-label");

        WorkflowRun b = getAndStartBuild();
        j.assertBuildStatusSuccess(j.waitForCompletion(b));
        j.assertLogContains("[Pipeline] { (foo)", b);
        j.assertLogContains("FOO is BAR", b);
        j.assertLogContains("BUILD_NUM_ENV is 1", b);
        j.assertLogContains("ANOTHER_ENV is 1", b);
        j.assertLogContains("INHERITED_ENV is 1 is inherited", b);
    }

}
