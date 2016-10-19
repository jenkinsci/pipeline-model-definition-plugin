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
import hudson.model.Describable;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.Job;
import hudson.model.Run;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.pipeline.modeldefinition.Messages;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import javax.annotation.Nonnull;

/**
 * Provides folder level configuration.
 */
public class FolderConfig extends AbstractFolderProperty<AbstractFolder<?>> {
    private String dockerLabel;

    @DataBoundConstructor
    public FolderConfig() {
    }

    /**
     * For testing
     *
     * @param dockerLabel the docker label to use
     */
    public FolderConfig(String dockerLabel) {
        this.dockerLabel = dockerLabel;
    }

    public String getDockerLabel() {
        return dockerLabel;
    }

    @DataBoundSetter
    public void setDockerLabel(String dockerLabel) {
        this.dockerLabel = dockerLabel;
    }

    @Extension @Symbol("pmd")
    public static class DescriptorImpl extends AbstractFolderPropertyDescriptor {

        @Nonnull
        @Override
        public String getDisplayName() {
            return Messages.PipelineModelDefinition_DisplayName();
        }
    }

    @Extension(ordinal = 10000) //First to be asked
    public static class FolderDockerLabelProvider extends DockerLabelProvider {

        @Override
        public String getLabel(Run run) {
            Job job = run.getParent();
            ItemGroup parent = job.getParent();
            while(parent != null) {

                if (parent instanceof AbstractFolder) {
                    AbstractFolder folder = (AbstractFolder)parent;
                    FolderConfig config = (FolderConfig)folder.getProperties().get(FolderConfig.class);
                    if (config != null) {
                        String label = config.getDockerLabel();
                        if (!StringUtils.isBlank(label)) {
                            return label;
                        }
                    }
                }

                if (parent instanceof Item) {
                    parent = ((Item)parent).getParent();
                } else {
                    parent = null;
                }
            }
            return null;
        }
    }
}
