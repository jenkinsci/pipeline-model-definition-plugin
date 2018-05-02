/*
 * The MIT License
 *
 * Copyright (c) 2017, CloudBees, Inc.
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

import org.jenkinsci.plugins.pipeline.modeldefinition.AbstractModelDefTest;
import org.jenkinsci.plugins.workflow.flow.StashManager;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;

import java.util.Map;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class PreserveStashesJobPropertyTest extends AbstractModelDefTest {

    @Issue("JENKINS-45455")
    @Test
    public void stashWithNoProperty() throws Exception {
        WorkflowRun r = expect("properties", "stashWithNoProperty")
                .go();
        Map<String,Map<String,String>> stashMap = StashManager.stashesOf(r);
        assertTrue(stashMap.isEmpty());
    }

    @Issue("JENKINS-45455")
    @Test
    public void stashWithDefaultPropertyValue() throws Exception {
        WorkflowRun r = expect("properties", "stashWithDefaultPropertyValue")
                .go();

        assertFalse(StashManager.stashesOf(r).isEmpty());

        WorkflowJob p = r.getParent();
        assertNotNull(p);

        // Build 10 times to make sure get 11 total builds.
        for (int i = 0; i < 10; i++) {
            j.buildAndAssertSuccess(p);
        }

        // Now that we've run 11 builds, the first one shouldn't have a stash any more.
        assertTrue(StashManager.stashesOf(r).isEmpty());

        // And builds 2..11 should all still have stashes.
        for (int i = 2; i < 12; i++) {
            WorkflowRun laterRun = p.getBuildByNumber(i);
            assertNotNull(laterRun);
            assertFalse(StashManager.stashesOf(laterRun).isEmpty());
        }
    }

    @Issue("JENKINS-45455")
    @Test
    public void stashWithSpecifiedPropertyValue() throws Exception {
        WorkflowRun r = expect("properties", "stashWithSpecifiedPropertyValue")
                .go();

        assertFalse(StashManager.stashesOf(r).isEmpty());

        WorkflowJob p = r.getParent();
        assertNotNull(p);

        // Build 4 times to make sure get 5 total builds.
        for (int i = 0; i < 4; i++) {
            j.buildAndAssertSuccess(p);
        }

        // The first three builds shouldn't have stashes any more.
        assertTrue(StashManager.stashesOf(p.getBuildByNumber(1)).isEmpty());
        assertTrue(StashManager.stashesOf(p.getBuildByNumber(2)).isEmpty());
        assertTrue(StashManager.stashesOf(p.getBuildByNumber(3)).isEmpty());

        // The last two should still have stashes
        assertFalse(StashManager.stashesOf(p.getBuildByNumber(4)).isEmpty());
        assertFalse(StashManager.stashesOf(p.getBuildByNumber(5)).isEmpty());
    }

    @Issue("JENKINS-45455")
    @Test
    public void stashWithNegativePropertyValue() throws Exception {
        WorkflowRun r = expect("properties", "stashWithNegativePropertyValue")
                .go();
        // We shouldn't be keeping any stashes, since the build count isn't greater than 0.
        assertTrue(StashManager.stashesOf(r).isEmpty());
    }
}