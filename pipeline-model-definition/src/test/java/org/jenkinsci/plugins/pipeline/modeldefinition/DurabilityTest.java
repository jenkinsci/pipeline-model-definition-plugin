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

import hudson.model.labels.LabelAtom;
import hudson.slaves.DumbSlave;
import hudson.slaves.EnvironmentVariablesNodeProperty;
import org.hamcrest.Matchers;
import org.jenkinsci.plugins.pipeline.modeldefinition.config.GlobalConfig;
import org.jenkinsci.plugins.workflow.actions.WorkspaceAction;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.cps.CpsFlowExecution;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.graph.FlowGraphWalker;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.test.steps.SemaphoreStep;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runners.model.Statement;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.LoggerRule;
import org.jvnet.hudson.test.RestartableJenkinsRule;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.logging.Level;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;

public class DurabilityTest extends AbstractDeclarativeTest {
    @ClassRule
    public static BuildWatcher buildWatcher = new BuildWatcher();

    @Rule
    public RestartableJenkinsRule story = new RestartableJenkinsRule();

    @Rule
    public LoggerRule logger = new LoggerRule();

    @Issue("JENKINS-42027")
    @Test
    public void globalConfigPersists() throws Exception {
        story.addStep(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                GlobalConfig.get().setDockerLabel("config_docker");
                GlobalConfig.get().save();
            }
        });

        story.addStep(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                assertEquals("config_docker", GlobalConfig.get().getDockerLabel());
            }
        });
    }

    @Test
    public void survivesRestart() throws Exception {
        onAllowedOS(PossibleOS.LINUX, PossibleOS.MAC);

        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                logger.record(CpsFlowExecution.class, Level.WARNING).capture(100);
                DumbSlave s = story.j.createOnlineSlave();
                s.setLabelString("remote quick");
                s.getNodeProperties().add(new EnvironmentVariablesNodeProperty(new EnvironmentVariablesNodeProperty.Entry("ONAGENT", "true")));

                WorkflowJob p = story.j.jenkins.createProject(WorkflowJob.class, "demo");
                p.setDefinition(new CpsFlowDefinition(
                        "pipeline {\n" +
                                "  agent {\n" +
                                "    label 'remote'\n" +
                                "  }\n" +
                                "  stages {\n" +
                                "    stage('foo') {\n" +
                                "      steps {\n" +
                                "        sh('echo before=`basename $PWD`')\n" +
                                "        sh('echo ONAGENT=$ONAGENT')\n" +
                                "        semaphore 'wait'\n" +
                                "        sh('echo after=$PWD')\n" +
                                "      }\n" +
                                "    }\n" +
                                "    stage('bar') {\n" +
                                "      when {\n" +
                                "        expression {\n" +
                                "          return true\n" +
                                "        }\n" +
                                "      }\n" +
                                "      steps {\n" +
                                "        sh('echo reallyAfterFoo')\n" +
                                "      }\n" +
                                "    }\n" +
                                "    stage('baz') {\n" +
                                "      when {\n" +
                                "        expression {\n" +
                                "          return false\n" +
                                "        }\n" +
                                "      }\n" +
                                "      steps {\n" +
                                "        sh('echo neverShouldReach')\n" +
                                "      }\n" +
                                "    }\n" +
                                "  }\n" +
                                "}",
                        true));
                WorkflowRun b = p.scheduleBuild2(0).waitForStart();
                SemaphoreStep.waitForStart("wait/1", b);
            }
        });
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                WorkflowJob p = (WorkflowJob) story.j.jenkins.getItem("demo");
                assertNotNull(p);
                WorkflowRun b = p.getLastBuild();
                SemaphoreStep.success("wait/1", null);
                story.j.assertBuildStatusSuccess(story.j.waitForCompletion(b));

                story.j.assertLogContains("before=demo", b);
                story.j.assertLogContains("ONAGENT=true", b);
                story.j.assertLogContains("reallyAfterFoo", b);
                story.j.assertLogNotContains("neverShouldReach", b);
                FlowExecution execution = b.getExecution();
                assertNotNull(execution);
                FlowGraphWalker walker = new FlowGraphWalker(execution);
                List<WorkspaceAction> actions = new ArrayList<>();
                for (FlowNode n : walker) {
                    WorkspaceAction a = n.getAction(WorkspaceAction.class);
                    if (a != null) {
                        actions.add(a);
                    }
                }
                assertEquals(1, actions.size());
                assertEquals(new HashSet<>(Arrays.asList(LabelAtom.get("remote"), LabelAtom.get("quick"))), actions.get(0).getLabels());
                assertThat(logger.getRecords(), Matchers.hasSize(Matchers.equalTo(0)));

            }
        });

    }
}
