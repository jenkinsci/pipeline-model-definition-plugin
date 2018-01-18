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
 *
 */

package org.jenkinsci.plugins.pipeline.modeldefinition.when.impl;

import hudson.Extension;
import org.apache.commons.lang.StringUtils;
import org.apache.tools.ant.types.selectors.SelectorUtils;
import org.codehaus.groovy.ast.expr.Expression;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTWhenContent;
import org.jenkinsci.plugins.pipeline.modeldefinition.parser.ASTParserUtils;
import org.jenkinsci.plugins.pipeline.modeldefinition.when.DeclarativeStageConditional;
import org.jenkinsci.plugins.pipeline.modeldefinition.when.DeclarativeStageConditionalDescriptor;
import org.kohsuke.stapler.DataBoundConstructor;

import javax.annotation.CheckForNull;
import java.io.File;

public class TagConditional extends DeclarativeStageConditional<TagConditional> {
    private final String compare;

    @DataBoundConstructor
    public TagConditional(String compare) {
        this.compare = compare;
    }

    public String getCompare() {
        return compare;
    }

    public boolean tagMatches(String actualTag) {
        if (StringUtils.isEmpty(actualTag)) {
            return false; //This is no tag build.
        }
        if (StringUtils.isEmpty(compare) && StringUtils.isNotEmpty(actualTag)) {
            return true; //This is a tag build and user doesn't care what the name is
        }

        // Replace the Git directory separator character (always '/')
        // with the platform specific directory separator before
        // invoking Ant's platform specific path matching.
        String safeCompare = compare.replace('/', File.separatorChar);
        String safeName = actualTag.replace('/', File.separatorChar);
        return SelectorUtils.matchPath(safeCompare, safeName, false);
    }

    @Extension
    @Symbol("tag")
    public static class DescriptorImpl extends DeclarativeStageConditionalDescriptor<TagConditional> {
        @Override
        public Expression transformToRuntimeAST(@CheckForNull ModelASTWhenContent original) {
            return ASTParserUtils.transformWhenContentToRuntimeAST(original);
        }
    }
}
