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
import org.jenkinsci.plugins.pipeline.modeldefinition.config.DockerPropertiesProvider;
import org.jenkinsci.plugins.scriptsecurity.sandbox.whitelists.Whitelisted;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractSynchronousNonBlockingStepExecution;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import java.io.Serializable;

/**
 * The node label expression to use for running docker.
 *
 * @see org.jenkinsci.plugins.pipeline.modeldefinition.config.DockerLabelProvider
 */
public class DeclarativePropsStep extends AbstractStepImpl implements Serializable {
    private static final long serialVersionUID = 1L;

    public enum Property {
        @Whitelisted
        LABEL("Label for node to run on"),
        @Whitelisted
        REGISTRY_URL("URL for private registry to use"),
        @Whitelisted
        REGISTRY_CREDENTIALS("ID for credentials to use for private registry");

        private final String prop;

        Property(String prop) {
            this.prop = prop;
        }

        @Override
        public String toString() {
            return prop;
        }
    }

    private Property property;
    private String defaultValue;

    @DataBoundConstructor
    public DeclarativePropsStep(@Nonnull Property property) {
        this.property = property;
    }

    public Property getProperty() {
        return property;
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    @DataBoundSetter
    public void setDefaultValue(String defaultValue) {
        this.defaultValue = defaultValue;
    }

    @Extension
    public static final class DescriptorImpl extends AbstractStepDescriptorImpl {

        public DescriptorImpl() {
            super(StepExecutionImpl.class);
        }

        @Override
        public boolean isAdvanced() {
            return true;
        }

        @Override
        public String getFunctionName() {
            return "declarativeProps";
        }
    }


    public static class StepExecutionImpl extends AbstractSynchronousNonBlockingStepExecution<String> {
        private static final long serialVersionUID = 1L;

        @Inject
        private transient DeclarativePropsStep step;

        @StepContextParameter
        transient TaskListener listener;

        @StepContextParameter
        transient WorkflowRun run;

        @Override
        protected String run() throws Exception {
            String retVal = null;
            if (!StringUtils.isBlank(step.getDefaultValue())) {
                retVal = step.getDefaultValue();
            } else {
                switch (step.getProperty()) {
                    case LABEL:
                        retVal = getLabel();
                        break;
                    case REGISTRY_URL:
                        retVal = getRegistryUrl();
                        break;
                    case REGISTRY_CREDENTIALS:
                        retVal = getRegistryCredentialsId();
                        break;
                    default:
                        // TODO: Should we log here? Do we care?
                        retVal = null;
                }
            }

            if (retVal != null) {
                return retVal.trim();
            } else {
                return null;
            }
        }

        private String getLabel() {
            for (DockerPropertiesProvider provider : DockerPropertiesProvider.all()) {
                String label = provider.getLabel(run);
                if (!StringUtils.isBlank(label)) {
                    return label;
                }
            }
            return null;
        }

        private String getRegistryUrl() {
            for (DockerPropertiesProvider provider : DockerPropertiesProvider.all()) {
                String url = provider.getRegistryUrl(run);
                if (!StringUtils.isBlank(url)) {
                    return url;
                }
            }
            return null;
        }

        private String getRegistryCredentialsId() {
            for (DockerPropertiesProvider provider : DockerPropertiesProvider.all()) {
                String id = provider.getRegistryCredentialsId(run);
                if (!StringUtils.isBlank(id)) {
                    return id;
                }
            }
            return null;
        }
    }
}
