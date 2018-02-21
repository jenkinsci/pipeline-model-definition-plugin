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

package org.jenkinsci.plugins.pipeline.modeldefinition.when.impl;

import hudson.Extension;
import hudson.model.Result;
import org.codehaus.groovy.ast.expr.Expression;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTWhenContent;
import org.jenkinsci.plugins.pipeline.modeldefinition.parser.ASTParserUtils;
import org.jenkinsci.plugins.pipeline.modeldefinition.when.DeclarativeStageConditional;
import org.jenkinsci.plugins.pipeline.modeldefinition.when.DeclarativeStageConditionalDescriptor;
import org.jenkinsci.plugins.workflow.cps.CpsFlowExecution;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.support.steps.build.RunWrapper;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

/**
 * Stage/post condition based on the current build status, aping the legacy post conditionals.
 */
public class StatusConditional extends DeclarativeStageConditional<StatusConditional> {
    private String status;
    private boolean always;
    private boolean changed;

    @DataBoundConstructor
    public StatusConditional() {
    }

    public boolean isAlways() {
        return always;
    }

    @DataBoundSetter
    public void setAlways(boolean always) {
        this.always = always;
    }

    public boolean isChanged() {
        return changed;
    }

    @DataBoundSetter
    public void setChanged(boolean changed) {
        this.changed = changed;
    }

    public String getStatus() {
        return status;
    }

    @DataBoundSetter
    public void setStatus(String status) {
        this.status = status;
    }

    public boolean statusMatches(RunWrapper runWrapper) {
        if (always) {
            return true;
        }
        WorkflowRun r = (WorkflowRun) runWrapper.getRawBuild();
        if (r != null) {
            Result runResult = r.getResult() != null ? r.getResult() : Result.SUCCESS;
            if (changed) {
                Result execResult = getExecutionResult(r);
                // Only look at the previous completed build.
                WorkflowRun prev = r.getPreviousCompletedBuild();

                Result combinedResult = execResult.combine(runResult);

                if (prev == null) {
                    return true;
                } else {
                    return !combinedResult.equals(prev.getResult());
                }
            } else if (status != null) {
                return runResult.equals(Result.fromString(status));
            } else {
                // In practice, we can never reach this, since at least one of always/changed/status needs to be specified.
                return true;
            }
        } else {
            // Something has gone weirdly wrong.
            // TODO: Logging of that.
            return false;
        }
    }

    @Nonnull
    private Result getExecutionResult(@Nonnull WorkflowRun r) {
        FlowExecution execution = r.getExecution();
        if (execution instanceof CpsFlowExecution) {
            return ((CpsFlowExecution) execution).getResult();
        } else {
            Result result = r.getResult();
            if (result == null) {
                result = Result.SUCCESS;
            }

            return result;
        }
    }

    @Extension
    @Symbol("buildStatus")
    public static class DescriptorImpl extends DeclarativeStageConditionalDescriptor<StatusConditional> {
        @Override
        public Expression transformToRuntimeAST(@CheckForNull ModelASTWhenContent original) {
            return ASTParserUtils.transformWhenContentToRuntimeAST(original);
        }
    }
}
