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
 *
 */

package org.jenkinsci.plugins.pipeline.modeldefinition.environment.impl;

import hudson.Extension;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.pipeline.modeldefinition.environment.DeclarativeEnvironmentContributor;
import org.jenkinsci.plugins.pipeline.modeldefinition.environment.DeclarativeEnvironmentContributorDescriptor;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Read environment from a properties file in the scm.
 */
public class TrustedProperties extends DeclarativeEnvironmentContributor<TrustedProperties> {

    private final String path;

    @DataBoundConstructor
    public TrustedProperties(String path) {
        this.path = path;
    }

    public String getPath() {
        return path;
    }

    @Extension @Symbol("properties")
    public static class DescriptorImpl extends DeclarativeEnvironmentContributorDescriptor<TrustedProperties> {

    }
}
