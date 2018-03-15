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

package org.jenkinsci.plugins.pipeline.modeldefinition.agent.impl;

import hudson.Extension;
import hudson.model.Item;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.docker.commons.credentials.DockerRegistryEndpoint;
import org.jenkinsci.plugins.pipeline.modeldefinition.agent.DeclarativeAgent;
import org.jenkinsci.plugins.pipeline.modeldefinition.agent.DeclarativeAgentDescriptor;
import org.jenkinsci.plugins.pipeline.modeldefinition.agent.DeclarativeDockerUtils;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import javax.annotation.Nonnull;

/**
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
public class ContainerAgent extends DeclarativeAgent<ContainerAgent> {

    private final String image;

    private String registryCredentials;

    @DataBoundConstructor
    public ContainerAgent(@Nonnull String image) {
        this.image = image;
    }

    @DataBoundSetter
    public void setRegistryCredentials(String registryCredentials) {
        this.registryCredentials = registryCredentials;
    }

    @Extension(ordinal = 1000, optional = true) @Symbol("container")
    public static class DescriptorImpl extends DeclarativeAgentDescriptor<ContainerAgent> {

        private ListBoxModel doFillRegistryCredentialsItems(@AncestorInPath Item item) {
            final DockerRegistryEndpoint.DescriptorImpl descriptor =
                    (DockerRegistryEndpoint.DescriptorImpl)
                            Jenkins.getInstance().getDescriptorOrDie(DockerRegistryEndpoint.class);
            return descriptor.doFillCredentialsIdItems(item);
        }


        @Nonnull
        @Override
        public String getScriptClass() {
            final ContainerAgentProvider provider = DeclarativeDockerUtils.getProvider();
            return provider != null ? provider.getScriptClass() : "org.jenkinsci.plugins.pipeline.modeldefinition.agent.impl.ContainerAgentScript";
        }
    }

}
