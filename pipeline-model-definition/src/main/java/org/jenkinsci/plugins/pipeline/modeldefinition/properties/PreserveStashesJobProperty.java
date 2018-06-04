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

import hudson.Extension;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.listeners.RunListener;
import hudson.util.FormValidation;
import jenkins.model.OptionalJobProperty;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.workflow.flow.StashManager;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PreserveStashesJobProperty extends OptionalJobProperty<WorkflowJob> {

    private static final Logger LOGGER = Logger.getLogger(PreserveStashesJobProperty.class.getName());

    private int buildCount = 10;

    @DataBoundConstructor
    public PreserveStashesJobProperty() {

    }

    public int getBuildCount() {
        return buildCount;
    }

    @DataBoundSetter
    public void setBuildCount(int buildCount) {
        this.buildCount = buildCount;
    }

    @Extension @Symbol("preserveStashes")
    public static class DescriptorImpl extends OptionalJobPropertyDescriptor {
        public FormValidation doCheckBuildCount(@QueryParameter int value) {
            if (value < 0) {
                return FormValidation.error("Must be greater than or equal to 0");
            } else {
                return FormValidation.ok();
            }
        }
    }

    @Extension
    public static final class SaveStashes extends StashManager.StashBehavior {

        @Override
        public boolean shouldClearAll(@Nonnull Run<?,?> build) {
            if (build instanceof WorkflowRun) {
                WorkflowRun r = (WorkflowRun)build;
                WorkflowJob j = r.getParent();
                PreserveStashesJobProperty prop = j.getProperty(PreserveStashesJobProperty.class);
                if (prop != null) {
                    int bc = prop.getBuildCount();
                    if (bc > 0) {
                        for (WorkflowRun recentRun : j.getBuilds().completedOnly().limit(bc)) {
                            if (recentRun != null && recentRun.equals(r)) {
                                return false;
                            }
                        }
                    }
                }
            }
            return true;
        }
    }

    @Extension
    public static final class StashClearingListener extends RunListener<Run<?,?>> {
        @Override
        public void onCompleted(Run<?,?> r, TaskListener listener) {
            if (r instanceof WorkflowRun) {
                WorkflowJob j = ((WorkflowRun) r).getParent();
                PreserveStashesJobProperty prop = j.getProperty(PreserveStashesJobProperty.class);
                if (prop != null && prop.getBuildCount() > 0) {
                    // TODO: Determine if this is overzealous. Might need to ape log rotation more closely.
                    for (WorkflowRun build : j.getBuilds().completedOnly()) {
                        try {
                            StashManager.maybeClearAll(build);
                        } catch (IOException x) {
                            LOGGER.log(Level.WARNING, "failed to clean up stashes from " + build, x);
                        }
                    }
                }
            }
        }
    }
}
