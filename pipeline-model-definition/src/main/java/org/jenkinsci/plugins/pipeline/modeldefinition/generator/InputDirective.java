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
import hudson.model.Descriptor;
import hudson.model.ParameterDefinition;
import hudson.util.FormValidation;
import org.apache.commons.lang3.StringUtils;
import org.jenkinsci.plugins.structs.describable.UninstantiatedDescribable;
import org.jenkinsci.plugins.workflow.cps.Snippetizer;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Collections;
import java.util.List;

public class InputDirective extends AbstractDirective<InputDirective> {
    private final String message;
    private String id;
    private String submitter;
    private String submitterParameter;
    private List<ParameterDefinition> parameters = Collections.emptyList();
    private String ok;

    @DataBoundConstructor
    public InputDirective(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }

    @DataBoundSetter
    public void setOk(String ok) {
        this.ok = ok;
    }

    public String getOk() {
        return ok;
    }

    @DataBoundSetter
    public void setSubmitterParameter(String submitterParameter) {
        this.submitterParameter = submitterParameter;
    }

    public String getSubmitterParameter() {
        return submitterParameter;
    }

    @DataBoundSetter
    public void setSubmitter(String submitter) {
        this.submitter = submitter;
    }

    public String getSubmitter() {
        return submitter;
    }

    @DataBoundSetter
    public void setId(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    @DataBoundSetter
    public void setParameters(List<ParameterDefinition> parameters) {
        this.parameters = parameters;
    }

    public List<ParameterDefinition> getParameters() {
        return parameters;
    }

    @Extension
    public static class DescriptorImpl extends DirectiveDescriptor<InputDirective> {
        @Override
        @NonNull
        public String getName() {
            return "input";
        }

        @Override
        @NonNull
        public String getDisplayName() {
            return "Input";
        }

        @Override
        @NonNull
        public List<Descriptor> getDescriptors() {
            return Collections.singletonList(StepDescriptor.byFunctionName("input"));
        }

        public Descriptor getInputDescriptor() {
            return StepDescriptor.byFunctionName("input");
        }

        public FormValidation doCheckMessage(@QueryParameter String value) {
            if (StringUtils.isEmpty(value)) {
                return FormValidation.error("Input message must be provided.");
            } else {
                return FormValidation.ok();
            }
        }

        public FormValidation doCheckId(@QueryParameter String id) {
            // TODO post SECURITY-2880 update the pipeline-input-step dependency and call the InputStep descriptor check

            // https://www.rfc-editor.org/rfc/rfc3986.txt
            // URLs may only contain ascii
            // and only some parts are allowed
            //      segment       = *pchar
            //      segment-nz    = 1*pchar
            //      segment-nz-nc = 1*( unreserved / pct-encoded / sub-delims / "@" )
            //                      ; non-zero-length segment without any colon ":"
            //      pchar         = unreserved / pct-encoded / sub-delims / ":" / "@"
            //      unreserved    = ALPHA / DIGIT / "-" / "." / "_" / "~"
            //      sub-delims    = "!" / "$" / "&" / "'" / "(" / ")"
            //                      / "*" / "+" / "," / ";" / "="

            // but we are not allowing pct-encoded here.
            // additionally "." and ".." should be rejected.
            // and as we are using html / javascript in places we disallow "'"
            // and to prevent escaping hell disallow "&"

            // as well as anything unsafe we disallow . and .. (but we can have a dot inside the string so foo.bar is ok)
            // also Jenkins dissallows ; in the request parameter so don't allow that either.
            if (id == null || id.isEmpty()) {
                // the id will be provided by a hash of the message
                return FormValidation.ok();
            }
            if (id.equals(".")) {
                return FormValidation.error("The ID is required to be URL safe and is limited to the characters a-z A-Z, the digits 0-9 and additionally the characters ':' '@' '=' '+' '$' ',' '-' '_' '.' '!' '~' '*' '(' ')'.");
            }
            if (id.equals("..")) {
                return FormValidation.error("The ID is required to be URL safe and is limited to the characters a-z A-Z, the digits 0-9 and additionally the characters ':' '@' '=' '+' '$' ',' '-' '_' '.' '!' '~' '*' '(' ')'.");
            }
            if (!id.matches("^[a-zA-Z0-9[-]._~!$()*+,:@=]+$")) { // escape the - inside another [] so it does not become a range of , - _
                return FormValidation.error("The ID is required to be URL safe and is limited to the characters a-z A-Z, the digits 0-9 and additionally the characters ':' '@' '=' '+' '$' ',' '-' '_' '.' '!' '~' '*' '(' ')'.");
            }
            return FormValidation.ok();
        }

        @Override
        @NonNull
        public String toGroovy(@NonNull InputDirective directive) {
            if (directive.getMessage() != null) {
                StringBuilder result = new StringBuilder("input {\n");
                result.append("message ").append(Snippetizer.object2Groovy(directive.getMessage())).append("\n");
                if (!StringUtils.isEmpty(directive.getId())) {
                    result.append("id ").append(Snippetizer.object2Groovy(directive.getId())).append("\n");
                }
                if (!StringUtils.isEmpty(directive.getOk())) {
                    result.append("ok ").append(Snippetizer.object2Groovy(directive.getOk())).append("\n");
                }
                if (!StringUtils.isEmpty(directive.getSubmitter())) {
                    result.append("submitter ").append(Snippetizer.object2Groovy(directive.getSubmitter())).append("\n");
                }
                if (!StringUtils.isEmpty(directive.getSubmitterParameter())) {
                    result.append("submitterParameter ").append(Snippetizer.object2Groovy(directive.getSubmitterParameter())).append("\n");
                }
                if (!directive.getParameters().isEmpty()) {
                    result.append("parameters {\n");
                    for (ParameterDefinition p : directive.getParameters()) {
                        result.append(Snippetizer.object2Groovy(UninstantiatedDescribable.from(p))).append("\n");
                    }
                    result.append("}\n");
                }
                result.append("}\n");
                return result.toString();
            }

            return "// Input not defined\n";
        }
    }
}
