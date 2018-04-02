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

package org.jenkinsci.plugins.pipeline.modeldefinition;

import hudson.Functions;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.junit.runners.model.TestTimedOutException;

import java.lang.management.ThreadInfo;
import java.util.logging.Logger;

public class TimeoutPerTestRule implements TestRule {
    private static final Logger LOGGER = Logger.getLogger(TimeoutPerTestRule.class.getName());

    private int seconds;

    public TimeoutPerTestRule() {
        this.seconds = Integer.getInteger("jenkins.test.timeout", System.getProperty("maven.surefire.debug") == null ? 180 : 0);
    }

    public Statement apply(final Statement base, final Description description) {
        if (seconds <= 0) {
            System.out.println("Test timeout disabled.");
            return base;
        } else {
            final Statement timeoutStatement = Timeout.seconds(seconds).apply(base, description);
            return new Statement() {
                @Override
                public void evaluate() throws Throwable {
                    try {
                        timeoutStatement.evaluate();
                    } catch (TestTimedOutException x) {
                        // withLookingForStuckThread does not work well; better to just have a full thread dump.
                        LOGGER.warning(String.format("Test timed out (after %d seconds).", seconds));
                        dumpThreads();
                        throw x;
                    }
                }
            };
        }
    }

    private static void dumpThreads() {
        ThreadInfo[] threadInfos = Functions.getThreadInfos();
        Functions.ThreadGroupMap m = Functions.sortThreadsAndGetGroupMap(threadInfos);
        for (ThreadInfo ti : threadInfos) {
            System.err.println(Functions.dumpThreadInfo(ti, m));
        }
    }
}
