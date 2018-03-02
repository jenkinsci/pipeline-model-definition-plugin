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

package org.jenkinsci.plugins.pipeline.modeldefinition.when.impl;

import hudson.Extension;
import org.apache.tools.ant.types.selectors.SelectorUtils;
import org.codehaus.groovy.ast.expr.Expression;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTWhenContent;
import org.jenkinsci.plugins.pipeline.modeldefinition.parser.ASTParserUtils;
import org.jenkinsci.plugins.pipeline.modeldefinition.when.DeclarativeStageConditional;
import org.jenkinsci.plugins.pipeline.modeldefinition.when.DeclarativeStageConditionalDescriptor;
import org.kohsuke.stapler.DataBoundConstructor;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.io.File;

import static org.apache.commons.lang.StringUtils.isEmpty;

/**
 * Stage condition based on the current branch. i.e. the env var BRANCH_NAME.
 * As populated by {@link jenkins.branch.BranchNameContributor}
 */
public class BranchConditional extends DeclarativeStageConditional<BranchConditional> {
    private final String compare;

    @DataBoundConstructor
    public BranchConditional(String compare) {
        this.compare = compare;
    }

    public String getCompare() {
        return compare;
    }

    public boolean branchMatches(String toCompare, String actualBranch) {
        if (isEmpty(actualBranch) && isEmpty(toCompare)) {
            return true;
        } else if (isEmpty(actualBranch) || isEmpty(toCompare)) {
            return false;
        }
        // Replace the Git directory separator character (always '/')
        // with the platform specific directory separator before
        // invoking Ant's platform specific path matching.
        String safeCompare = toCompare.replace('/', File.separatorChar);
        String safeName = actualBranch.replace('/', File.separatorChar);
        return SelectorUtils.matchPath(safeCompare, safeName, false);
    }

    @Extension
    @Symbol("branch")
    public static class DescriptorImpl extends DeclarativeStageConditionalDescriptor<BranchConditional> {
        @Override
        @Nonnull
        public String getDisplayName() {
            return "Execute the stage if the current branch matches a pattern";
        }

        @Override
        public Expression transformToRuntimeAST(@CheckForNull ModelASTWhenContent original) {
            return ASTParserUtils.transformWhenContentToRuntimeAST(original);
        }
    }
}
