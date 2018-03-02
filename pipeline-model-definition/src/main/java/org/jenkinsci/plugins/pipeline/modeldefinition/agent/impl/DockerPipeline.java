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
import hudson.Util;
import hudson.util.FormValidation;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.pipeline.modeldefinition.agent.AbstractDockerAgent;
import org.jenkinsci.plugins.pipeline.modeldefinition.agent.DeclarativeAgentDescriptor;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

import javax.annotation.Nonnull;

public class DockerPipeline extends AbstractDockerAgent<DockerPipeline> {
    private String image;
    private boolean alwaysPull;

    @DataBoundConstructor
    public DockerPipeline(@Nonnull String image) {
        this.image = image;
    }

    public @Nonnull String getImage() {
        return image;
    }

    @DataBoundSetter
    public void setAlwaysPull(boolean alwaysPull) {
        this.alwaysPull = alwaysPull;
    }

    public boolean isAlwaysPull() {
        return alwaysPull;
    }

    @Extension(ordinal = 1000) @Symbol("docker")
    public static class DescriptorImpl extends DeclarativeAgentDescriptor<DockerPipeline> {
        @Override
        @Nonnull
        public String getDisplayName() {
            return "Run inside a Docker container";
        }

        public FormValidation doCheckImage(@QueryParameter String image) {
            if (StringUtils.isEmpty(Util.fixEmptyAndTrim(image))) {
                return FormValidation.error("Image is required.");
            } else {
                return FormValidation.ok();
            }
        }
    }
}
