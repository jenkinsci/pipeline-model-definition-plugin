/*
 * The MIT License
 *
 * Copyright (c) 2020, CloudBees, Inc.
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
package org.jenkinsci.plugins.pipeline.modeldefinition.generator;

import hudson.Extension;
import hudson.model.Descriptor;
import org.kohsuke.stapler.DataBoundConstructor;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.List;

public class AxisDirective extends AbstractDirective<AxisDirective> {

    private String name;
    private String values;

    @DataBoundConstructor
    public AxisDirective(String name, String values) {
        this.name = name;
        this.values = values;
    }

    @Nonnull
    public String getName() {
        return name;
    }

    public String getValues() {
        return values;
    }

    @Extension
    public static class DescriptorImpl extends DirectiveDescriptor<AxisDirective> {
        @Override
        @Nonnull
        public String getName() {
            return "axis";
        }

        @Override
        @Nonnull
        public String getDisplayName() {
            return "Axis";
        }

        @Nonnull
        @Override
        public List<Descriptor> getDescriptors() {
            return Collections.emptyList();
        }

        @Override
        @Nonnull
        public String toGroovy(@Nonnull AxisDirective axis) {
            return String.format(
                    "axis {%n" +
                    "name %s%n"+
                    "values %s%n"+
                    "}", axis.name, axis.values
            );
        }
    }

}
