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
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.pipeline.modeldefinition.agent.AbstractDockerAgent;
import org.jenkinsci.plugins.pipeline.modeldefinition.agent.DeclarativeAgentDescriptor;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import javax.annotation.Nonnull;

public class DockerPipelineFromDockerfile extends AbstractDockerAgent<DockerPipelineFromDockerfile> {
    private String filename;
    private String dir;
    private String additionalBuildArgs;

    @DataBoundConstructor
    public DockerPipelineFromDockerfile() {
    }

    public Object getFilename() {
        return filename;
    }

    @DataBoundSetter
    public void setFilename(String filename) {
        this.filename = filename;
    }

    public String getDir() {
        return dir;
    }

    @DataBoundSetter
    public void setDir(String dir) {
        this.dir = dir;
    }

    public String getAdditionalBuildArgs() {
        return additionalBuildArgs;
    }

    @DataBoundSetter
    public void setAdditionalBuildArgs(String additionalBuildArgs) {
        this.additionalBuildArgs = additionalBuildArgs;
    }

    @Nonnull
    public String getActualDir() {
        if (!StringUtils.isEmpty(dir)) {
            return dir;
        } else {
            return ".";
        }
    }

    @Nonnull
    public String getDockerfilePath(boolean isUnix) {
        StringBuilder fullPath = new StringBuilder();
        if (!StringUtils.isEmpty(dir)) {
            fullPath.append(dir);
            if (isUnix) {
                fullPath.append(IOUtils.DIR_SEPARATOR_UNIX);
            } else {
                fullPath.append(IOUtils.DIR_SEPARATOR_WINDOWS);
            }
        }
        fullPath.append(getDockerfileAsString());
        return fullPath.toString();
    }

    @Nonnull
    public String getDockerfileAsString() {
        if (filename != null) {
            return filename;
        } else {
            return "Dockerfile";
        }
    }

    @Extension(ordinal = 999) @Symbol("dockerfile")
    public static class DescriptorImpl extends DeclarativeAgentDescriptor<DockerPipelineFromDockerfile> {
        @Override
        @Nonnull
        public String getDisplayName() {
            return "Build a Dockerfile and run in a container using that image";
        }
    }
}
