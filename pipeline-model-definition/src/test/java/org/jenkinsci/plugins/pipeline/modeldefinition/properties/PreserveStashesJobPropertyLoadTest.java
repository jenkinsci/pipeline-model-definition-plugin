/*
 * The MIT License
 *
 * Copyright (c) 2018, CloudBees, Inc.
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

package org.jenkinsci.plugins.pipeline.modeldefinition.properties;

import org.jenkinsci.plugins.pipeline.modeldefinition.AbstractDeclarativeTest;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.flow.StashManager;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runners.model.Statement;
import org.jvnet.hudson.test.*;

import static org.junit.Assert.*;

public class PreserveStashesJobPropertyLoadTest extends AbstractDeclarativeTest {
    @ClassRule
    public static BuildWatcher buildWatcher = new BuildWatcher();

    @Rule
    public RestartableJenkinsRule story = new RestartableJenkinsRule();

    @Rule
    public LoggerRule logger = new LoggerRule();

    @Issue("JENKINS-45455")
    @Test
    public void stashWithLoadCount() throws Exception {
        story.addStep(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                WorkflowJob p = story.j.jenkins.createProject(WorkflowJob.class, "stashWithLoadCount");
                p.setDefinition(new CpsFlowDefinition(pipelineSourceFromResources("properties/stashWithLoadCount"), true));

                // Build 10 times to make sure get 10 total builds.
                for (int i = 0; i < 10; i++) {
                    story.j.waitForCompletion(p.scheduleBuild2(0).waitForStart());
                }
            }
        });

        story.addStep(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                WorkflowJob p = (WorkflowJob)story.j.jenkins.getItem("stashWithLoadCount");
                assertNotNull(p);

                RunLoadCounter.assertMaxLoads(p, /* TODO 2.407+ should be just 1 */4, () -> {
                    // The last build will get loaded via Queue.maintain as shown in https://github.com/jenkinsci/jenkins/pull/7998 anyway:
                    assertEquals(10, p.getLastBuild().getNumber());
                    // (until 2.407+, so will #7, #8, and #9, for a total of 4 loaded builds)
                    // PreserveStashesJobProperty.StashClearingListener and .SaveStashes shouldn't trigger a full load.
                    return story.j.buildAndAssertSuccess(p);
                });

                // This is here largely to demonstrate that we do still do loads.
                assertEquals(1, RunLoadCounter.countLoads(p, () -> {
                    assertEquals(7, p.getLastFailedBuild().getNumber());
                }));

                // The first nine builds shouldn't have stashes any more.
                for (int i = 1; i < 10; i++) {
                    assertTrue(StashManager.stashesOf(p.getBuildByNumber(i)).isEmpty());
                }

                // The last two should still have stashes
                assertFalse(StashManager.stashesOf(p.getBuildByNumber(10)).isEmpty());
                assertFalse(StashManager.stashesOf(p.getBuildByNumber(11)).isEmpty());
            }
        });
    }

}
