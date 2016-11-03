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

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.AbstractIdCredentialsListBoxModel;
import com.cloudbees.plugins.credentials.common.IdCredentials;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import com.google.inject.Inject;
import hudson.Extension;
import hudson.model.Item;
import hudson.model.Run;
import hudson.security.ACL;
import hudson.util.ListBoxModel;
import org.jenkinsci.plugins.credentialsbinding.BindingDescriptor;
import org.jenkinsci.plugins.pipeline.modeldefinition.model.CredentialsBindingHandler;
import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractSynchronousNonBlockingStepExecution;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;

import javax.annotation.Nonnull;
import java.io.Serializable;
import java.util.Collections;

/**
 * Returns a {@link CredentialWrapper} for simplified credentials binding in {@code environment {}}
 */
public class CredentialWrapperStep extends AbstractStepImpl implements Serializable {

    private final String id;

    @DataBoundConstructor
    public CredentialWrapperStep(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    @Extension
    public static final class DescriptorImpl extends AbstractStepDescriptorImpl {

        public DescriptorImpl() {
            super(CredentialStepExecution.class);
        }

        @Override
        public String getFunctionName() {
            return "credentials";
        }

        @SuppressWarnings("unused") //used by the view
        public ListBoxModel doFillIdItems(@AncestorInPath Item owner) {
            return new StandardListBoxModel().withAll(CredentialsProvider.lookupCredentials(StandardCredentials.class, owner, ACL.SYSTEM, Collections.<DomainRequirement>emptyList()));
        }

        @Nonnull
        @Override
        public String getDisplayName() {
            return "Provides a mechanism to set credentials into the pipeline model environment by returning a wrapper object.";
        }

        @Override
        public boolean isAdvanced() {
            return true;
        }
    }

    public static final class CredentialStepExecution extends AbstractSynchronousNonBlockingStepExecution<CredentialWrapper> {
        private static final long serialVersionUID = 1L;

        @Inject
        private transient CredentialWrapperStep step;
        @StepContextParameter
        private transient Run<?,?> run;

        @Override
        protected CredentialWrapper run() throws Exception {
            CredentialsBindingHandler handler = CredentialsBindingHandler.forId(step.getId(), run);
            return new CredentialWrapper(step.getId(), handler.getWithCredentialsParameters(step.getId()));
        }
    }
}
