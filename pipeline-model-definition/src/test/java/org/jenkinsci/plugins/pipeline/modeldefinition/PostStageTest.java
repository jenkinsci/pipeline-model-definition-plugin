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
import hudson.slaves.EnvironmentVariablesNodeProperty;
import hudson.slaves.NodeProperty;
import hudson.slaves.NodePropertyDescriptor;
import hudson.util.DescribableList;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tests for {@link org.jenkinsci.plugins.pipeline.modeldefinition.model.Stage#post}
 */
public class PostStageTest extends AbstractModelDefTest {

    private static Slave s;

    @BeforeClass
    public static void setUpAgent() throws Exception {
        s = j.createOnlineSlave();
        s.setLabelString("here");
    }

    @Test
    public void globalAndLocalAlways() throws Exception {
        expect("globalAndLocalAlways").logContains("Post stage", "Notifications", "Local Always", "Global Always").go();
    }

    @Test
    public void localAlways() throws Exception {
        expect("localAlways").logContains("Post stage", "Local Always").go();
    }

    public static final String[] ALL_LOCAL_ALWAYS = {"Post stage", "hello", "Notifications", "And AAAAIIIAAAIAI", "I AM ALWAYS WITH YOU"};

    @Test
    public void withAllLocalUnstable() throws Exception {
        env(s).put("MAKE_RESULT", Result.UNSTABLE.toString()).set();
        expect(Result.UNSTABLE, "localAll").logContains(ALL_LOCAL_ALWAYS)
                .logContains("Setting build result UNSTABLE", "I AM UNSTABLE", "I HAVE CHANGED")
                .logNotContains("I WAS ABORTED", "I FAILED", "MOST DEFINITELY FINISHED").go();

    }

    @Test
    public void withAllLocalFailure() throws Exception {
        env(s).put("MAKE_RESULT", Result.FAILURE.toString()).set();
        expect(Result.FAILURE, "localAll").logContains(ALL_LOCAL_ALWAYS)
                .logContains("Setting build result FAILURE", "I FAILED", "I HAVE CHANGED")
                .logNotContains("I WAS ABORTED", "I AM UNSTABLE", "MOST DEFINITELY FINISHED").go();

    }

    @Test
    public void withAllLocalAborted() throws Exception {
        env(s).put("MAKE_RESULT", Result.ABORTED.toString()).set();
        expect(Result.ABORTED, "localAll").logContains(ALL_LOCAL_ALWAYS)
                .logContains("Setting build result ABORTED", "I WAS ABORTED", "I HAVE CHANGED")
                .logNotContains("I FAILED", "I AM UNSTABLE", "MOST DEFINITELY FINISHED").go();

    }

    @Test
    public void withAllLocalSuccess() throws Exception {
        env(s).set();
        expect(Result.SUCCESS, "localAll").logContains(ALL_LOCAL_ALWAYS)
                .logContains("All is well", "MOST DEFINITELY FINISHED", "I HAVE CHANGED")
                .logNotContains("I WAS ABORTED", "I FAILED", "I AM UNSTABLE").go();

    }

    @Test
    public void withAllLocalChanged() throws Exception {
        env(s).set();
        ExpectationsBuilder expect = expect(Result.SUCCESS, "localAll").logContains(ALL_LOCAL_ALWAYS);
        expect.logContains("All is well", "MOST DEFINITELY FINISHED", "I HAVE CHANGED")
                .logNotContains("I WAS ABORTED", "I FAILED", "I AM UNSTABLE").go();
        expect.resetForNewRun(Result.SUCCESS)
                .logContains("All is well", "MOST DEFINITELY FINISHED")
                .logNotContains("I WAS ABORTED", "I FAILED", "I AM UNSTABLE", "I HAVE CHANGED").go();

        env(s).put("MAKE_RESULT", Result.UNSTABLE.toString()).set();
        expect.resetForNewRun(Result.UNSTABLE).logContains(ALL_LOCAL_ALWAYS)
                .logContains("Setting build result UNSTABLE", "I AM UNSTABLE", "I HAVE CHANGED")
                .logNotContains("I WAS ABORTED", "I FAILED", "MOST DEFINITELY FINISHED").go();

    }

    @Test
    public void failingNotifications() throws Exception {
        expect(Result.FAILURE, "failingNotifications")
                .logContains("hello", "Post stage", "farewell", "Error in stage post:", "I AM HERE STILL")
                .logNotContains("world").go();
    }

    @Override
    protected ExpectationsBuilder expect(String resource) {
        return super.expect("postStage", resource);
    }

    @Override
    protected ExpectationsBuilder expect(Result result, String resource) {
        return super.expect(result, "postStage", resource);
    }
}
