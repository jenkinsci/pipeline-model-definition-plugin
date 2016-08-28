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
package org.jenkinsci.plugins.pipeline.config.model.conditions

import hudson.Extension
import hudson.model.Result
import org.jenkinsci.Symbol
import org.jenkinsci.plugins.pipeline.config.model.BuildCondition
import org.jenkinsci.plugins.workflow.job.WorkflowRun

/**
 * A {@link BuildCondition} for matching builds with a different status than the previous build.
 *
 * @author Andrew Bayer
 */
@Extension @Symbol("changed")
public class Changed extends BuildCondition {
    @Override
    public boolean meetsCondition(WorkflowRun r) {
        // Only look at the previous completed build.
        WorkflowRun prev = r.getPreviousCompletedBuild()
        // If there's no previous build, we're inherently changed.
        if (prev == null) {
            return true
        }
        // If the current build's result isn't null (i.e., it's got a specified status), and it's different than the
        // previous build's result, we're changed.
        else if (r.getResult() != null && !prev.getResult().equals(r.getResult())) {
            return true
        }
        // If the current build's result is null and the previous build's result is not SUCCESS, we're changed.
        else if (r.getResult() == null && !prev.getResult().equals(Result.SUCCESS)) {
            return true
        }
        // And in any other condition, we're not changed, so return false.
        else {
            return false
        }
    }

    public static final long serialVersionUID = 1L

}
