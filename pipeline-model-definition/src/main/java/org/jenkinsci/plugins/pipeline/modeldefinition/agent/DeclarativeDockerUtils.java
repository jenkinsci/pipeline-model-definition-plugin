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

package org.jenkinsci.plugins.pipeline.modeldefinition.agent;

import hudson.model.Run;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.pipeline.modeldefinition.config.DockerPropertiesProvider;
import org.jenkinsci.plugins.scriptsecurity.sandbox.whitelists.Whitelisted;
import org.jenkinsci.plugins.workflow.cps.CpsFlowExecution;
import org.jenkinsci.plugins.workflow.cps.CpsThread;
import org.jenkinsci.plugins.workflow.flow.FlowExecutionOwner;

import javax.annotation.Nullable;
import java.io.IOException;

/**
 * @see org.jenkinsci.plugins.pipeline.modeldefinition.config.DockerLabelProvider
 */
public class DeclarativeDockerUtils {
    private static Run<?,?> currentRun() {
        try {
            CpsThread t = CpsThread.current();
            if (t != null) {
                CpsFlowExecution e = t.getExecution();
                if (e != null) {
                    FlowExecutionOwner o = e.getOwner();
                    if (o != null && o.getExecutable() instanceof Run) {
                        return (Run)o.getExecutable();
                    }
                }
            }

            return null;
        } catch (IOException i) {
            return null;
        }
    }

    @Whitelisted
    public static String getLabel() {
        return getLabel(null);
    }

    @Whitelisted
    public static String getLabel(@Nullable String override) {
        if (!StringUtils.isBlank(override)) {
            return override;
        } else {
            Run<?,?> r = currentRun();
            for (DockerPropertiesProvider provider : DockerPropertiesProvider.all()) {
                String label = provider.getLabel(r);
                if (!StringUtils.isBlank(label)) {
                    return label;
                }
            }
        }
        return null;
    }

    @Whitelisted
    public static String getRegistryUrl() {
        return getRegistryUrl(null);
    }

    @Whitelisted
    public static String getRegistryUrl(@Nullable String override) {
        if (!StringUtils.isBlank(override)) {
            return override;
        } else {
            Run<?,?> r = currentRun();
            for (DockerPropertiesProvider provider : DockerPropertiesProvider.all()) {
                String url = provider.getRegistryUrl(r);
                if (!StringUtils.isBlank(url)) {
                    return url;
                }
            }
        }
        return null;
    }

    @Whitelisted
    public static String getRegistryCredentialsId() {
        return getRegistryCredentialsId(null);
    }

    @Whitelisted
    public static String getRegistryCredentialsId(@Nullable String override) {
        if (!StringUtils.isBlank(override)) {
            return override;
        } else {
            Run<?,?> r = currentRun();
            for (DockerPropertiesProvider provider : DockerPropertiesProvider.all()) {
                String id = provider.getRegistryCredentialsId(r);
                if (!StringUtils.isBlank(id)) {
                    return id;
                }
            }
            return null;
        }
    }
}
