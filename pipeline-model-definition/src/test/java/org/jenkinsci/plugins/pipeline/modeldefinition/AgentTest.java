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

import hudson.model.Node;
import hudson.model.Queue;
import hudson.model.Result;
import hudson.model.Slave;
import hudson.model.queue.CauseOfBlockage;
import hudson.model.queue.QueueTaskDispatcher;
import hudson.slaves.EnvironmentVariablesNodeProperty;
import java.io.File;
import jenkins.model.Jenkins;
import org.apache.commons.io.FileUtils;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.InboundAgentRule;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.TestExtension;

/**
 * @author Andrew Bayer
 */
public class AgentTest extends AbstractModelDefTest {

    private static Slave s;
    private static Slave s2;
    @Rule public InboundAgentRule inboundAgents = new InboundAgentRule();

    @BeforeClass
    public static void setUpAgent() throws Exception {
        s = j.createSlave("some-label", null);
        s.getNodeProperties().add(new EnvironmentVariablesNodeProperty(new EnvironmentVariablesNodeProperty.Entry("ONAGENT", "true"),
                new EnvironmentVariablesNodeProperty.Entry("WHICH_AGENT", "first")));
        s.setNumExecutors(2);

        s2 = j.createSlave("other-label", null);
        s2.getNodeProperties().add(new EnvironmentVariablesNodeProperty(new EnvironmentVariablesNodeProperty.Entry("ONAGENT", "true"),
                new EnvironmentVariablesNodeProperty.Entry("WHICH_AGENT", "second")));

        j.jenkins.setNodes(j.jenkins.getNodes());
        j.waitOnline(s);
        j.waitOnline(s2);
    }

    @Issue("JENKINS-37932")
    @Test
    public void agentAny() throws Exception {
        expect("agent/agentAny")
                .logContains("[Pipeline] { (foo)", "THIS WORKS")
                .go();
    }

    // Also covers basic agentLabel usage.
    @Test
    public void noCheckoutScmInWrongContext() throws Exception {
        expect("agent/noCheckoutScmInWrongContext")
                .runFromRepo(false)
                .logContains("[Pipeline] { (foo)", "ONAGENT=true")
                .go();
    }

    @Test
    public void agentNone() throws Exception {
        expect(Result.FAILURE, "agent/agentNone")
                .logContains(Messages.ModelInterpreter_NoNodeContext())
                .go();
    }

    @Test
    public void agentNoneWithNode() throws Exception {
        expect("agent/agentNoneWithNode")
                .logContains("[Pipeline] { (foo)", "ONAGENT=true")
                .go();
    }

    @Test
    public void multipleVariablesForAgent() throws Exception {
        expect("agent/multipleVariablesForAgent")
                .logContains("[Pipeline] { (foo)",
                        "ONAGENT=true",
                        "Running in labelAndOtherField with otherField = banana",
                        "And nested: foo: monkey, bar: false")
                .go();
    }

    @Ignore("Still not sure yet whether we'll ever fix JENKINS-43911, but wanted to have a test here for if we do")
    @Issue("JENKINS-43911")
    @Test
    public void agentFromEnv() throws Exception {
        expect("agent/agentFromEnv")
                .logContains("WHICH_AGENT=first",
                        "WHICH_AGENT=second")
                .go();
    }

    @Issue("JENKINS-43911")
    @Test
    public void agentFromParentEnv() throws Exception {
        expect("agent/agentFromParentEnv")
                .logContains("WHICH_AGENT=first",
                        "WHICH_AGENT=second")
                .go();
    }

    @Issue("JENKINS-46809")
    @Test
    public void agentOnGroup() throws Exception {
        expect("agent/agentOnGroup")
                .logContains("Solo stage agent: first",
                        "First other stage agent: second",
                        "Second other stage agent: first")
                .go();
    }

    @Issue("JENKINS-54919")
    @Test
    public void paramInAgentLabel() throws Exception {
        expect("paramInAgentLabel")
                .logContains("[Pipeline] { (foo)", "ONAGENT=true")
                .go();
    }

    @Issue("JENKINS-41118")
    @Test
    public void inCustomWorkspace() throws Exception {
        expect("agent/inCustomWorkspace")
                .logMatches("Workspace dir is .*some-sub-dir")
                .go();
    }

    @Issue("JENKINS-41118")
    @Test
    public void inRelativeCustomWorkspace() throws Exception {
        onAllowedOS(PossibleOS.LINUX, PossibleOS.MAC);
        expect("agent/inRelativeCustomWorkspace")
                .logMatches("Workspace dir is .*relative/custom2/workspace3")
                .go();
    }

    @Issue("JENKINS-41118")
    @Test
    public void inAbsoluteCustomWorkspace() throws Exception {
        // Since we're using a Unix path, only run on a Unix environment
        onAllowedOS(PossibleOS.LINUX, PossibleOS.MAC);
        try {
            expect("agent/inAbsoluteCustomWorkspace")
                    .logContains("Workspace dir is /tmp/some-sub-dir")
                    .go();
        } finally {
            FileUtils.deleteDirectory(new File("/tmp/some-sub-dir"));
        }
    }

    @Issue("JENKINS-41118")
    @Test
    public void inCustomWorkspaceInStage() throws Exception {
        expect("agent/inCustomWorkspaceInStage")
                .logMatches("Workspace dir is .*some-other-sub-dir")
                .go();
    }

    @Issue("JENKINS-49707")
    @Test
    public void retryAny() throws Exception {
        Slave dumbo = inboundAgents.createAgent(j, "dumbo");
        WorkflowJob p = j.createProject(WorkflowJob.class, "p" + (Jenkins.get().getItems().size() + 1));
        // TODO convert to expect…go idiom (but need to figure out how to insert logic into middle of build)
        // Due to the way DeclarativeAgentDescriptor.zeroArgModels defines syntax, we cannot offer retries on actual agent any.
        // However it is legal to simply specify no label!
        p.setDefinition(new CpsFlowDefinition("pipeline {agent {node {label null; retries 2}}; stages {stage('main') {steps {script {if (isUnix()) {sh 'sleep 10'} else {bat 'echo + sleep && ping -n 10 localhost'}}}}}}", true));
        j.jenkins.updateNode(j.jenkins); // to force setNumExecutors to be honored
        WorkflowRun b;
        RunOnlyOnDumbo.active = true;
        try {
            b = p.scheduleBuild2(0).waitForStart();
            j.waitForMessage("Running on dumbo in ", b);
            j.waitForMessage("+ sleep", b);
            inboundAgents.stop("dumbo1");
            j.jenkins.removeNode(dumbo);
        } finally {
            RunOnlyOnDumbo.active = false;
        }
        j.waitForMessage("Retrying", b);
        j.waitForMessage("Running on " /* Jenkins or one of the other agents */, b);
        j.assertBuildStatusSuccess(j.waitForCompletion(b));
    }

    @TestExtension
    public static final class RunOnlyOnDumbo extends QueueTaskDispatcher {
        static boolean active;
        @Override
        public CauseOfBlockage canTake(Node node, Queue.BuildableItem item) {
            if (active && !node.getNodeName().equals("dumbo")) {
                return new CauseOfBlockage.BecauseNodeIsNotAcceptingTasks(node);
            } else {
                return null;
            }
        }
    }

    @Issue("JENKINS-49707")
    @Test
    public void retryLabel() throws Exception {
        Slave dumbo = inboundAgents.createAgent(j, "dumbo1");
        dumbo.setLabelString("dumb");
        WorkflowJob p = j.createProject(WorkflowJob.class, "p" + (Jenkins.get().getItems().size() + 1));
        // TODO convert to expect…go idiom as above
        p.setDefinition(new CpsFlowDefinition("pipeline {agent {node {label 'dumb'; retries 2}}; stages {stage('main') {steps {script {if (isUnix()) {sh 'sleep 10'} else {bat 'echo + sleep && ping -n 10 localhost'}}}}}}", true));
        WorkflowRun b = p.scheduleBuild2(0).waitForStart();
        j.waitForMessage("+ sleep", b);
        inboundAgents.stop("dumbo1");
        j.jenkins.removeNode(dumbo);
        j.waitForMessage("Retrying", b);
        dumbo = inboundAgents.createAgent(j, "dumbo2");
        dumbo.setLabelString("dumb");
        j.jenkins.updateNode(dumbo); // to force setLabelString to be honored
        j.waitForMessage("Running on dumbo2 in ", b);
        j.assertBuildStatusSuccess(j.waitForCompletion(b));
    }

}
