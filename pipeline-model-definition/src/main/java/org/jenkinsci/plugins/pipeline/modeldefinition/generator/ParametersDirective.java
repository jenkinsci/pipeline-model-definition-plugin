/*
 * The MIT License
 *
 * Copyright (c) 2018, CloudBees, Inc.
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
import hudson.ExtensionList;
import hudson.model.Descriptor;
import hudson.model.ParameterDefinition;
import org.jenkinsci.plugins.structs.describable.UninstantiatedDescribable;
import org.jenkinsci.plugins.workflow.cps.Snippetizer;
import org.kohsuke.stapler.DataBoundConstructor;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class ParametersDirective extends AbstractDirective<ParametersDirective> {
    private List<ParameterDefinition> parameters = new ArrayList<>();

    @DataBoundConstructor
    public ParametersDirective(List<ParameterDefinition> parameters) {
        if (parameters != null) {
            this.parameters.addAll(parameters);
        }
    }

    @Nonnull
    public List<ParameterDefinition> getParameters() {
        return parameters;
    }

    @Extension
    public static class DescriptorImpl extends DirectiveDescriptor<ParametersDirective> {
        @Override
        @Nonnull
        public String getName() {
            return "parameters";
        }

        @Override
        @Nonnull
        public String getDisplayName() {
            return "Parameters";
        }

        @Override
        @Nonnull
        public List<Descriptor> getDescriptors() {
            return ExtensionList.lookup(ParameterDefinition.ParameterDescriptor.class).stream()
                    .filter(d -> DirectiveDescriptor.symbolForDescriptor(d) != null)
                    .sorted(Comparator.comparing(d -> DirectiveDescriptor.symbolForDescriptor(d)))
                    .collect(Collectors.toList());
        }

        @Override
        @Nonnull
        public String toGroovy(@Nonnull ParametersDirective directive) {
            StringBuilder result = new StringBuilder("parameters {\n");
            for (ParameterDefinition param : directive.parameters) {
                result.append(Snippetizer.object2Groovy(UninstantiatedDescribable.from(param)));
                result.append("\n");
            }
            result.append("}\n");
            return result.toString();
        }
    }
}
