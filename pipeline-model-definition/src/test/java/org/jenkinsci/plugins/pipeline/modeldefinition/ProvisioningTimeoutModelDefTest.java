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
import hudson.model.Slave;
import hudson.slaves.RetentionStrategy;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.jvnet.hudson.test.Issue;

/**
 * @author Jose Taboada
 *
 * The goal of this test is check the nuances between top level and stage level agents regarding timeouts (options)
 *  Top Level Agents: timeout is only for the build itself, therefore won't fail.
 *  Stage Level Agents: timeout includes provisioning, in this example is +200ms
 *
 * Jira Ticket JENKINS-60994 to describe this problem.
 */
@RunWith(Parameterized.class)
public class ProvisioningTimeoutModelDefTest extends AbstractModelDefTest {

    private static Slave s;

    @BeforeClass
    public static void setUpAgent() throws Exception {
        s = j.createOnlineSlave();
        s.setNumExecutors(1);
        s.setLabelString("some-label docker");
    }

    @Parameterized.Parameters
    public static Object[][] data() {
        return new Object[10][0];
    }

    @Test
    @Issue("JENKINS-60994")
    public void topLevelAgent() throws Exception {
        expect("options/topLevelAgentSuccessWithTimeout")
                .go();
    }

    @Test
    @Issue("JENKINS-60994")
    public void stageLevelAgent() throws Exception {
        expect(Result.ABORTED, "options/stageLevelAgentFailsWithTimeout")
                .go();
    }
}
