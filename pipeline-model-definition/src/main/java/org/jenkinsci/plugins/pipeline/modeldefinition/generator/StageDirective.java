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

import com.google.common.collect.ImmutableList;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.JobPropertyDescriptor;
import hudson.util.FormValidation;
import jenkins.model.Jenkins;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTPipelineDef;
import org.jenkinsci.plugins.pipeline.modeldefinition.model.Options;
import org.jenkinsci.plugins.pipeline.modeldefinition.options.DeclarativeOptionDescriptor;
import org.jenkinsci.plugins.pipeline.modeldefinition.validator.BlockedStepsAndMethodCalls;
import org.jenkinsci.plugins.structs.SymbolLookup;
import org.jenkinsci.plugins.structs.describable.UninstantiatedDescribable;
import org.jenkinsci.plugins.workflow.cps.Snippetizer;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class StageDirective extends AbstractDirective<StageDirective> {
    private List<AbstractDirective> directives = new ArrayList<>();
    private String name;
    private int contentType;

    @DataBoundConstructor
    public StageDirective(List<AbstractDirective> directives, @Nonnull String name, int contentType) {
        if (directives != null) {
            this.directives.addAll(directives);
        }
        this.name = name;
        this.contentType = contentType;
    }

    @Nonnull
    public String getName() {
        return name;
    }

    /**
     * What the content of this stage is - currently steps or parallel
     * @return 0 for steps, 1 for parallel.
     */
    public int getContentType() {
        return contentType;
    }

    @Nonnull
    public List<AbstractDirective> getDirectives() {
        return directives;
    }

    @Extension
    public static class DescriptorImpl extends DirectiveDescriptor<StageDirective> {
        @Override
        @Nonnull
        public String getName() {
            return "stage";
        }

        @Override
        @Nonnull
        public String getDisplayName() {
            return "Stage";
        }

        @Override
        @Nonnull
        public List<Descriptor> getDescriptors() {
            List<Descriptor> descriptors = new ArrayList<>();
            descriptors.add(Jenkins.getActiveInstance().getDescriptorByType(AgentDirective.DescriptorImpl.class));
            descriptors.add(Jenkins.getActiveInstance().getDescriptorByType(InputDirective.DescriptorImpl.class));
            descriptors.add(Jenkins.getActiveInstance().getDescriptorByType(EnvironmentDirective.DescriptorImpl.class));
            descriptors.add(Jenkins.getActiveInstance().getDescriptorByType(OptionsDirective.DescriptorImpl.class));
            descriptors.add(Jenkins.getActiveInstance().getDescriptorByType(WhenDirective.DescriptorImpl.class));
            descriptors.add(Jenkins.getActiveInstance().getDescriptorByType(ToolsDirective.DescriptorImpl.class));
            descriptors.add(Jenkins.getActiveInstance().getDescriptorByType(PostDirective.DescriptorImpl.class));

            return descriptors;
        }

        public FormValidation doCheckName(@QueryParameter String value) {
            if (StringUtils.isEmpty(value)) {
                return FormValidation.error("Stage name must be provided.");
            } else {
                return FormValidation.ok();
            }
        }

        @Override
        @Nonnull
        public String toGroovy(@Nonnull StageDirective directive) {
            StringBuilder result = new StringBuilder("stage(");
            result.append("'").append(directive.name).append("') {\n");
            switch (directive.contentType) {
                case 0:
                    result.append("steps {\n");
                    result.append("// One or more steps need to be included within the steps block.\n");
                    result.append("}\n");
                    break;
                case 1:
                    result.append("parallel {\n");
                    result.append("// One or more stages need to be included within the parallel block.\n");
                    result.append("}\n");
                    break;
                default:
                    result.append("// Unknown stage content - only steps and parallel currently available.\n");
            }
            for (AbstractDirective d : directive.directives) {
                result.append("\n").append(d.toGroovy(false));
            }
            result.append("}\n");
            return result.toString();
        }
    }
}
