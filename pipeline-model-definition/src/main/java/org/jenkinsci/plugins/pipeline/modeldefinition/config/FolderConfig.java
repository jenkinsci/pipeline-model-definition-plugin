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

package org.jenkinsci.plugins.pipeline.modeldefinition.config;

import com.cloudbees.hudson.plugins.folder.AbstractFolder;
import com.cloudbees.hudson.plugins.folder.AbstractFolderProperty;
import com.cloudbees.hudson.plugins.folder.AbstractFolderPropertyDescriptor;
import hudson.Extension;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.Job;
import hudson.model.Run;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.docker.commons.credentials.DockerRegistryEndpoint;
import org.jenkinsci.plugins.pipeline.modeldefinition.Messages;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Provides folder level configuration.
 */
public class FolderConfig extends AbstractFolderProperty<AbstractFolder<?>> {
    private String dockerLabel;
    private DockerRegistryEndpoint registry;

    @DataBoundConstructor
    public FolderConfig() {
    }

    /**
     * For testing
     *
     * @param dockerLabel the docker label to use
     * @param url The registry URL
     * @param creds the registry credentials ID
     */
    public FolderConfig(String dockerLabel, String url, String creds) {
        this.dockerLabel = dockerLabel;
        this.registry = new DockerRegistryEndpoint(url, creds);
    }

    public String getDockerLabel() {
        return dockerLabel;
    }

    @DataBoundSetter
    public void setDockerLabel(String dockerLabel) {
        this.dockerLabel = dockerLabel;
    }

    public DockerRegistryEndpoint getRegistry() {
        return registry;
    }

    @DataBoundSetter
    public void setRegistry(DockerRegistryEndpoint registry) {
        this.registry = registry;
    }

    @Extension @Symbol("pipeline-model")
    public static class DescriptorImpl extends AbstractFolderPropertyDescriptor {

        @Nonnull
        @Override
        public String getDisplayName() {
            return Messages.PipelineModelDefinition_DisplayName();
        }
    }

    @Extension(ordinal = 10000) //First to be asked
    public static class FolderDockerPropertiesProvider extends DockerPropertiesProvider {

        @Override
        public String getLabel(@Nullable Run run) {
            if (run != null) {
                Job job = run.getParent();
                ItemGroup parent = job.getParent();
                while (parent != null) {

                    if (parent instanceof AbstractFolder) {
                        AbstractFolder folder = (AbstractFolder) parent;
                        FolderConfig config = (FolderConfig) folder.getProperties().get(FolderConfig.class);
                        if (config != null) {
                            String label = config.getDockerLabel();
                            if (!StringUtils.isBlank(label)) {
                                return label;
                            }
                        }
                    }

                    if (parent instanceof Item) {
                        parent = ((Item) parent).getParent();
                    } else {
                        parent = null;
                    }
                }
            }
            return null;
        }

        @Override
        public String getRegistryUrl(@Nullable Run run) {
            if (run != null) {
                Job job = run.getParent();
                ItemGroup parent = job.getParent();
                while (parent != null) {

                    if (parent instanceof AbstractFolder) {
                        AbstractFolder folder = (AbstractFolder) parent;
                        FolderConfig config = (FolderConfig) folder.getProperties().get(FolderConfig.class);
                        if (config != null) {
                            DockerRegistryEndpoint registry = config.getRegistry();
                            if (registry != null && !StringUtils.isBlank(registry.getUrl())) {
                                return registry.getUrl();
                            }
                        }
                    }

                    if (parent instanceof Item) {
                        parent = ((Item) parent).getParent();
                    } else {
                        parent = null;
                    }
                }
            }
            return null;
        }

        @Override
        public String getRegistryCredentialsId(@Nullable Run run) {
            if (run != null) {
                Job job = run.getParent();
                ItemGroup parent = job.getParent();
                while (parent != null) {

                    if (parent instanceof AbstractFolder) {
                        AbstractFolder folder = (AbstractFolder) parent;
                        FolderConfig config = (FolderConfig) folder.getProperties().get(FolderConfig.class);
                        if (config != null) {
                            DockerRegistryEndpoint registry = config.getRegistry();
                            if (registry != null && !StringUtils.isBlank(registry.getCredentialsId())) {
                                return registry.getCredentialsId();
                            }
                        }
                    }

                    if (parent instanceof Item) {
                        parent = ((Item) parent).getParent();
                    } else {
                        parent = null;
                    }
                }
            }
            return null;
        }
    }
}
