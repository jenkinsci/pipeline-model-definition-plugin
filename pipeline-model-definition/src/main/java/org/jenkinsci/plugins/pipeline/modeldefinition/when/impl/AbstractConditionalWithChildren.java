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

package org.jenkinsci.plugins.pipeline.modeldefinition.when.impl;

import org.codehaus.groovy.ast.ASTNode;
import org.codehaus.groovy.ast.expr.MethodCallExpression;
import org.codehaus.groovy.ast.stmt.Statement;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTWhenCondition;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTWhenContent;
import org.jenkinsci.plugins.pipeline.modeldefinition.parser.ASTParserUtils;
import org.jenkinsci.plugins.pipeline.modeldefinition.when.DeclarativeStageConditional;

import javax.annotation.CheckForNull;
import java.util.List;

/**
 * Match all of a list of stage conditions
 */
public abstract class AbstractConditionalWithChildren<C extends AbstractConditionalWithChildren<C>> extends DeclarativeStageConditional<C> {
    private final List<DeclarativeStageConditional<? extends DeclarativeStageConditional>> children;

    public AbstractConditionalWithChildren(List<DeclarativeStageConditional<? extends DeclarativeStageConditional>> children) {
        this.children = children;
    }

    public List<DeclarativeStageConditional<? extends DeclarativeStageConditional>> getChildren() {
        return children;
    }

    @CheckForNull
    public static ASTNode transformToRuntimeAST(@CheckForNull ModelASTWhenContent original) {
        if (original != null && original instanceof ModelASTWhenCondition) {
            ModelASTWhenCondition cond = (ModelASTWhenCondition) original;
            if (cond.getSourceLocation() != null && cond.getSourceLocation() instanceof Statement) {
                MethodCallExpression methCall = ASTParserUtils.matchMethodCall((Statement) cond.getSourceLocation());

                if (methCall != null) {
                    return ASTParserUtils.methodCallToDescribable(methCall);
                }
            }
        }
        return null;
    }
}
