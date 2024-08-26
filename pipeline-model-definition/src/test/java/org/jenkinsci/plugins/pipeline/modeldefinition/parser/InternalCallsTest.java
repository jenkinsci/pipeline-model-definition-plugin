/*
 * The MIT License
 *
 * Copyright 2024 CloudBees, Inc.
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

package org.jenkinsci.plugins.pipeline.modeldefinition.parser;

import com.cloudbees.jenkins.support.api.Container;
import com.cloudbees.jenkins.support.api.Content;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.cps.CpsFlowExecution;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.RealJenkinsRule;

public final class InternalCallsTest {

    @Rule public final RealJenkinsRule rr = new RealJenkinsRule();
//    @ClassRule public static final BuildWatcher bw = new BuildWatcher();

    @Test public void declarative() throws Throwable {
        rr.then(InternalCallsTest::_declarative);
    }

    private static void _declarative(JenkinsRule r) throws Throwable {
        var p = r.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition("pipeline {options {quietPeriod 0}; agent any; stages {stage('x') {steps {echo(/constructing ${new hudson.EnvVars()}/)}}}}", true));
        r.assertLogContains("constructing [:]", r.buildAndAssertSuccess(p));
        var baos = new ByteArrayOutputStream();
        new CpsFlowExecution.PipelineInternalCalls().addContents(new Container() {
            @Override public void add(Content content) {
                try {
                    content.writeTo(baos);
                } catch (IOException x) {
                    assert false : x;
                }
            }
        });
        assertThat(baos.toString(), allOf(
            containsString("hudson.EnvVars.<init>"),
            not(containsString("org.jenkinsci.plugins.pipeline.modeldefinition"))));
    }

}
