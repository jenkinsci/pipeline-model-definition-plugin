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

import hudson.model.BooleanParameterDefinition;
import hudson.model.Job;
import hudson.model.JobProperty;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.Result;
import hudson.model.StringParameterDefinition;
import hudson.model.queue.QueueTaskFuture;
import hudson.tasks.LogRotator;
import hudson.triggers.TimerTrigger;
import hudson.triggers.Trigger;
import jenkins.model.BuildDiscarder;
import jenkins.model.BuildDiscarderProperty;

import java.lang.reflect.Field;
import java.util.concurrent.TimeUnit;

import org.jenkinsci.plugins.pipeline.modeldefinition.actions.DeclarativeJobPropertyTrackerAction;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.job.properties.DisableConcurrentBuildsJobProperty;
import org.jenkinsci.plugins.workflow.job.properties.PipelineTriggersJobProperty;
import org.junit.Ignore;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class OptionsTest extends AbstractModelDefTest {
    @Test
    public void simpleJobProperties() throws Exception {
        WorkflowRun b = expect("simpleJobProperties")
                .logContains("[Pipeline] { (foo)", "hello")
                .logNotContains("[Pipeline] { (" + SyntheticStageNames.postBuild() + ")")
                .go();

        WorkflowJob p = b.getParent();

        BuildDiscarderProperty bdp = p.getProperty(BuildDiscarderProperty.class);
        assertNotNull(bdp);
        BuildDiscarder strategy = bdp.getStrategy();
        assertNotNull(strategy);
        assertEquals(LogRotator.class, strategy.getClass());
        LogRotator lr = (LogRotator) strategy;
        assertEquals(1, lr.getNumToKeep());

    }

    @Ignore("Properties are set before withEnv is called.")
    @Test
    public void envVarInOptions() throws Exception {
        WorkflowRun b = expect("envVarInOptions")
                .logContains("[Pipeline] { (foo)", "hello")
                .logNotContains("[Pipeline] { (" + SyntheticStageNames.postBuild() + ")")
                .go();

        WorkflowJob p = b.getParent();

        BuildDiscarderProperty bdp = p.getProperty(BuildDiscarderProperty.class);
        assertNotNull(bdp);
        BuildDiscarder strategy = bdp.getStrategy();
        assertNotNull(strategy);
        assertEquals(LogRotator.class, strategy.getClass());
        LogRotator lr = (LogRotator) strategy;
        assertEquals(1, lr.getNumToKeep());

    }

    @Test
    public void multipleProperties() throws Exception {
        WorkflowRun b = expect(Result.FAILURE, "multipleProperties")
                .logContains("[Pipeline] { (foo)",
                        "[Pipeline] timeout",
                        "hello")
                .logNotContains("[Pipeline] { (" + SyntheticStageNames.postBuild() + ")")
                .go();

        WorkflowJob p = b.getParent();

        // We test for skipDefaultCheckout() in the Jenkinsfile itself by verifying that Jenkinsfile isn't in the workspace

        // Job properties
        BuildDiscarderProperty bdp = p.getProperty(BuildDiscarderProperty.class);
        assertNotNull(bdp);
        BuildDiscarder strategy = bdp.getStrategy();
        assertNotNull(strategy);
        assertEquals(LogRotator.class, strategy.getClass());
        LogRotator lr = (LogRotator) strategy;
        assertEquals(1, lr.getNumToKeep());

        DisableConcurrentBuildsJobProperty dcbjp = p.getProperty(DisableConcurrentBuildsJobProperty.class);
        assertNotNull(dcbjp);

        // Parameters
        ParametersDefinitionProperty pdp = p.getProperty(ParametersDefinitionProperty.class);
        assertNotNull(pdp);

        assertEquals(2, pdp.getParameterDefinitions().size());
        BooleanParameterDefinition bpd = getParameterOfType(pdp.getParameterDefinitions(), BooleanParameterDefinition.class);
        assertNotNull(bpd);
        assertEquals("flag", bpd.getName());
        assertTrue(bpd.isDefaultValue());

        StringParameterDefinition spd = getParameterOfType(pdp.getParameterDefinitions(), StringParameterDefinition.class);
        assertNotNull(spd);
        assertEquals("SOME_STRING", spd.getName());

        // Trigger(s)
        PipelineTriggersJobProperty trigProp = p.getProperty(PipelineTriggersJobProperty.class);
        assertNotNull(trigProp);

        assertEquals(1, trigProp.getTriggers().size());
        TimerTrigger.DescriptorImpl timerDesc = j.jenkins.getDescriptorByType(TimerTrigger.DescriptorImpl.class);

        Trigger trigger = trigProp.getTriggerForDescriptor(timerDesc);
        assertNotNull(trigger);

        assertTrue(trigger instanceof TimerTrigger);
        TimerTrigger timer = (TimerTrigger) trigger;
        assertEquals("@daily", timer.getSpec());
    }

    @Test
    public void skipCheckoutFalse() throws Exception {
        expect("skipCheckoutFalse")
                .logContains("[Pipeline] { (foo)",
                        "hello")
                .go();
    }

    @Test
    public void simpleWrapper() throws Exception {
        expect("simpleWrapper")
                .logContains("[Pipeline] { (foo)",
                        "[Pipeline] timeout",
                        "hello")
                .logNotContains("[Pipeline] { (Post Actions)")
                .go();
    }

    @Ignore("Technically we could allow env vars in wrappers, since wrappers get invoked within env block, but since " +
            "we can't for properties, triggers, or parameters due to being invoked before the env block, let's be " +
            "consistent")
    @Test
    public void envVarInWrapper() throws Exception {
        expect("envVarInWrapper")
                .logContains("[Pipeline] { (foo)",
                        "[Pipeline] timeout",
                        "hello")
                .logNotContains("[Pipeline] { (Post Actions)")
                .go();
    }

    @Test
    public void multipleWrappers() throws Exception {
        expect("multipleWrappers")
                .logContains("[Pipeline] { (foo)",
                        "[Pipeline] timeout",
                        "[Pipeline] retry",
                        "hello")
                .logNotContains("[Pipeline] { (Post Actions)")
                .go();
    }

    @Issue("JENKINS-44149")
    @Test
    public void propsRemoved() throws Exception {
        WorkflowRun b = getAndStartNonRepoBuild("simpleJobProperties");
        j.assertBuildStatusSuccess(j.waitForCompletion(b));

        WorkflowJob job = b.getParent();
        assertNotNull(job.getProperty(BuildDiscarderProperty.class));

        job.setDefinition(new CpsFlowDefinition(pipelineSourceFromResources("propsTriggersParamsRemoved"), true));
        j.buildAndAssertSuccess(job);

        assertNull(job.getProperty(BuildDiscarderProperty.class));
    }

    @Issue("JENKINS-44621")
    @Test
    public void externalPropsNotRemoved() throws Exception {
        WorkflowRun b = getAndStartNonRepoBuild("simpleJobProperties");
        j.assertBuildStatusSuccess(j.waitForCompletion(b));

        WorkflowJob job = b.getParent();
        assertNotNull(job.getProperty(BuildDiscarderProperty.class));
        job.addProperty(new DisableConcurrentBuildsJobProperty());

        job.setDefinition(new CpsFlowDefinition(pipelineSourceFromResources("propsTriggersParamsRemoved"), true));
        j.buildAndAssertSuccess(job);

        assertNull(job.getProperty(BuildDiscarderProperty.class));

        assertNotNull(job.getProperty(DisableConcurrentBuildsJobProperty.class));

        int externalPropCount = 0;
        for (JobProperty p : job.getAllProperties()) {
            if (p instanceof DisableConcurrentBuildsJobProperty) {
                externalPropCount++;
            }
        }

        assertEquals(1, externalPropCount);
    }

    @Issue("JENKINS-44809")
    @Test
    public void duplicateExternalPropsCleaned() throws Exception {
        WorkflowRun b = getAndStartNonRepoBuild("simpleParameters");
        j.assertBuildStatusSuccess(j.waitForCompletion(b));

        WorkflowJob job = b.getParent();
        job.setDefinition(new CpsFlowDefinition(pipelineSourceFromResources("simpleJobProperties"), true));
        j.buildAndAssertSuccess(job);

        assertNotNull(job.getProperty(BuildDiscarderProperty.class));
        assertNull(job.getProperty(ParametersDefinitionProperty.class));
        DeclarativeJobPropertyTrackerAction action2 = job.getAction(DeclarativeJobPropertyTrackerAction.class);
        assertNotNull(action2);
        assertTrue(action2.getParameters().isEmpty());

        job.addProperty(new DisableConcurrentBuildsJobProperty());
        job.addProperty(new DisableConcurrentBuildsJobProperty());
        job.addProperty(new DisableConcurrentBuildsJobProperty());

        job.setDefinition(new CpsFlowDefinition(pipelineSourceFromResources("propsTriggersParamsRemoved"), true));
        j.buildAndAssertSuccess(job);

        assertNull(job.getProperty(BuildDiscarderProperty.class));
        assertNull(job.getProperty(ParametersDefinitionProperty.class));

        assertNotNull(job.getProperty(DisableConcurrentBuildsJobProperty.class));

        DeclarativeJobPropertyTrackerAction action3 = job.getAction(DeclarativeJobPropertyTrackerAction.class);
        assertNotNull(action3);
        assertTrue(action3.getParameters().isEmpty());

        int externalPropCount = 0;
        for (JobProperty p : job.getAllProperties()) {
            if (p instanceof DisableConcurrentBuildsJobProperty) {
                externalPropCount++;
            }
        }

        assertEquals(1, externalPropCount);
    }

    @Issue("JENKINS-46403")
    @Test
    public void replaceLogRotatorWithBuildDiscarderProperty() throws Exception {
        WorkflowRun b = getAndStartNonRepoBuild("simpleParameters");
        j.assertBuildStatusSuccess(j.waitForCompletion(b));

        WorkflowJob job = b.getParent();
        job.setDefinition(new CpsFlowDefinition(pipelineSourceFromResources("simpleJobProperties"), true));

        // we want to test a job with an old-style logRotator property, for which there is no setter anymore
        try {
            Field deprecatedLogRotatorField = Job.class.getDeclaredField("logRotator");
            deprecatedLogRotatorField.setAccessible(true);
            deprecatedLogRotatorField.set(job, new LogRotator(-1, 42, -1, 2));
            job.save();
            assertNotNull(job.getBuildDiscarder());
            if (job.getBuildDiscarder() instanceof LogRotator) {
                LogRotator lr = (LogRotator) job.getBuildDiscarder();
                assertEquals(lr.getNumToKeep(), 42);
            }
        } catch (NoSuchFieldException e) {
            // if there is no such field anymore, then I guess there is no potential issue anymore
        }

        // run the job
        QueueTaskFuture<WorkflowRun> f = job.scheduleBuild2(0);
        assertNotNull(f);
        // wait up to 10 seconds, fail test on timeout (see livelock described in JENKINS-46403)
        try {
            j.waitUntilNoActivityUpTo(10_000);
            // we could use f.get(...) and TimeoutException instead, but this is convenient to dump threads
        } catch (AssertionError ae) {
            // makes termination faster by actually killing the job (avoids another 60 seconds)
            f.getStartCondition().get(100L, TimeUnit.MILLISECONDS).doKill();
            throw ae;
        }

        // no timeout, the build should be blue
        j.assertBuildStatusSuccess(f);

        // check the original old-style LogRotator has been replaced by something else
        assertNotNull(job.getBuildDiscarder());
        if (job.getBuildDiscarder() instanceof LogRotator) {
            LogRotator lr = (LogRotator) job.getBuildDiscarder();
            assertNotEquals(lr.getNumToKeep(), 42);
        }
    }

    @Issue("TBD")
    @Test
    public void retryOptions() throws Exception {
        expect(Result.FAILURE, "retryOptions")
                .logContains("Retrying")
                .go();
    }

    @Issue("JENKINS-48115")
    @Test
    public void disableConcurrentBuilds() throws Exception {
        WorkflowRun b = expect("disableConcurrentBuilds")
                .go();
        WorkflowJob p = b.getParent();

        DisableConcurrentBuildsJobProperty prop = p.getProperty(DisableConcurrentBuildsJobProperty.class);
        assertNotNull(prop);

    }

    @Issue("JENKINS-48380")
    @Test
    public void stageWrapper() throws Exception {
        expect("stageWrapper")
                .logContains("[Pipeline] { (foo)",
                        "[Pipeline] timeout",
                        "hello")
                .logNotContains("[Pipeline] { (Post Actions)")
                .go();
    }

    @Issue("JENKINS-48380")
    @Test
    public void skipCheckoutInStage() throws Exception {
        expect("skipCheckoutInStage")
                .logContains("[Pipeline] { (foo)",
                        "hello")
                .go();
    }
}