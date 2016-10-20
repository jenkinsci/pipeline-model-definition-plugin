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
 *
 */

package org.jenkinsci.plugins.pipeline.modeldefinition.steps;

import hudson.Extension;
import hudson.model.TaskListener;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.pipeline.modeldefinition.config.DockerLabelProvider;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractSynchronousNonBlockingStepExecution;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.Serializable;

/**
 * The node label expression to use for running docker.
 *
 * @see org.jenkinsci.plugins.pipeline.modeldefinition.config.DockerLabelProvider
 */
public class DockerLabelStep extends AbstractStepImpl implements Serializable {
    private static final long serialVersionUID = 1L;

    @DataBoundConstructor
    public DockerLabelStep() {
    }

    @Extension
    public static final class DescriptorImpl extends AbstractStepDescriptorImpl {

        public DescriptorImpl() {
            super(StepExecutionImpl.class);
        }

        @Override
        public String getFunctionName() {
            return "dockerLabel";
        }
    }


    public static class StepExecutionImpl extends AbstractSynchronousNonBlockingStepExecution<String> {
        private static final long serialVersionUID = 1L;

        @StepContextParameter
        transient TaskListener listener;

        @StepContextParameter
        transient WorkflowRun run;

        @Override
        protected String run() throws Exception {
            for (DockerLabelProvider provider : DockerLabelProvider.all()) {
                String label = provider.getLabel(run);
                if (!StringUtils.isBlank(label)) {
                    return label;
                }
            }
            return null;
        }
    }
}
