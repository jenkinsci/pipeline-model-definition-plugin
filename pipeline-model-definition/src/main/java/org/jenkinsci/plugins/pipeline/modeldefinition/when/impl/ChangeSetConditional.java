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

package org.jenkinsci.plugins.pipeline.modeldefinition.when.impl;

import hudson.Extension;
import org.codehaus.groovy.ast.expr.Expression;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTWhenContent;
import org.jenkinsci.plugins.pipeline.modeldefinition.parser.ASTParserUtils;
import org.jenkinsci.plugins.pipeline.modeldefinition.when.DeclarativeStageConditional;
import org.jenkinsci.plugins.pipeline.modeldefinition.when.DeclarativeStageConditionalDescriptor;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

/**
 * Conditional that checks the affected file paths recorded in the changelog.
 *
 * The build must first have collected the changelog via for example <code>checkout scm</code>.
 */
public class ChangeSetConditional extends DeclarativeStageConditional<ChangeSetConditional> {

    private String glob;
    private boolean caseSensitive;

    @DataBoundConstructor
    public ChangeSetConditional(String glob) {
        this.glob = glob;
        this.caseSensitive = false;
    }

    public String getGlob() {
        return glob;
    }

    public boolean isCaseSensitive() {
        return caseSensitive;
    }

    @DataBoundSetter
    public void setCaseSensitive(boolean caseSensitive) {
        this.caseSensitive = caseSensitive;
    }

    @Extension
    @Symbol("changeset")
    public static class DescriptorImpl extends DeclarativeStageConditionalDescriptor<ChangeSetConditional> {
        @Override
        @Nonnull
        public String getDisplayName() {
            return "Execute the stage if the changeset contains a file matching a pattern";
        }

        @Override
        public Expression transformToRuntimeAST(@CheckForNull ModelASTWhenContent original) {
            return ASTParserUtils.transformWhenContentToRuntimeAST(original);
        }
    }
}
