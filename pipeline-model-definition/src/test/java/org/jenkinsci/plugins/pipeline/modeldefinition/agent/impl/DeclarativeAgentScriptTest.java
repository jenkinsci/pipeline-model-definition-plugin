/*
 * The MIT License
 *
 * Copyright 2025 CloudBees, Inc.
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

package org.jenkinsci.plugins.pipeline.modeldefinition.agent.impl;

import static jenkins.test.RunMatchers.logContains;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.not;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.support.steps.input.InputAction;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.recipes.LocalData;

/**
 * Verify that serialized program state can be resumed across versions.
 * Despite referring to agents, actually loading an old program using an agent in a test is hard.
 * {@code org.jenkinsci.plugins.workflow.cps.SerialFormTest.persistence} managed it with Docker.
 * Using the built-in node also does not work well as it hard-codes paths and environment variables from the host.
 * Therefore only {@code agent none} is directly tested for now.
 * The tests also skip using an SCM for simplicity.
 */
public class DeclarativeAgentScriptTest {

    @ClassRule public static BuildWatcher buildWatcher = new BuildWatcher();
    @Rule public JenkinsRule r = new JenkinsRule();

    @LocalData
    @Test public void noneCompat() throws Exception {
        /*
        var p = r.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition("""
            pipeline {
              agent none
              stages {
                stage('one') {
                  steps {
                    echo 'OK ran stage one'
                  }
                }
                stage('two') {
                  steps {
                    input 'continue stage two'
                  }
                }
                stage('three') {
                  steps {
                    echo 'OK ran stage three'
                  }
                }
              }
            }
            """, true));
        var b = p.scheduleBuild2(0).waitForStart();
        r.waitForMessage("continue stage two", b);
        FileUtils.deleteDirectory(new File("/tmp/jh"));
        FileUtils.copyDirectory(r.jenkins.root, new File("/tmp/jh"));
        */
        var p = r.jenkins.getItemByFullName("p", WorkflowJob.class);
        var b = p.getLastBuild();
        assertThat(b, allOf(logContains("OK ran stage one"), logContains("continue stage two"), not(logContains("OK ran stage three"))));
        b.getAction(InputAction.class).getExecutions().get(0).proceed(null);
        r.assertBuildStatusSuccess(r.waitForCompletion(b));
        assertThat(b, logContains("OK ran stage three"));
    }

}
