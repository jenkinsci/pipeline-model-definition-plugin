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

import htmlpublisher.HtmlPublisherTarget;
import hudson.model.Slave;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.BeforeClass;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;


/**
 * @author Andrew Bayer
 */
public class StepsTest extends AbstractModelDefTest {

    private static Slave s;

    @BeforeClass
    public static void setUpAgent() throws Exception {
        s = j.createOnlineSlave();
        s.setNumExecutors(10);
        s.setLabelString("some-label");
    }

    @Test
    public void nestedTreeSteps() throws Exception {
        expect("steps/nestedTreeSteps")
                .logContains("[Pipeline] { (foo)", "[Pipeline] timeout", "[Pipeline] retry", "hello")
                .go();
    }

    @Test
    public void metaStepSyntax() throws Exception {
        env(s).set();
        expect("steps/metaStepSyntax")
                .archives("msg.out", "hello world")
                .archives("msg2.out", "goodbye world")
                .go();
    }

    @Issue("JENKINS-41456")
    @Test
    public void htmlPublisher() throws Exception {
        WorkflowRun b = expect("steps/htmlPublisher")
                .logContains("[Pipeline] { (foo)")
                .go();

        HtmlPublisherTarget.HTMLBuildAction buildReport = b.getAction(HtmlPublisherTarget.HTMLBuildAction.class);
        assertNotNull(buildReport);
        assertEquals("Test Report", buildReport.getHTMLTarget().getReportName());
    }
}
