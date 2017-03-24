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
package org.jenkinsci.plugins.pipeline.modeldefinition.parser

import org.codehaus.groovy.ast.ASTNode
import org.codehaus.groovy.ast.ClassCodeExpressionTransformer
import org.codehaus.groovy.ast.ClassHelper
import org.codehaus.groovy.ast.expr.ClosureExpression
import org.codehaus.groovy.ast.expr.ConstructorCallExpression
import org.codehaus.groovy.ast.expr.DeclarationExpression
import org.codehaus.groovy.ast.expr.Expression
import org.codehaus.groovy.ast.expr.MethodCallExpression
import org.codehaus.groovy.control.CompilePhase
import org.codehaus.groovy.control.SourceUnit
import org.codehaus.groovy.transform.ASTTransformation
import org.codehaus.groovy.transform.GroovyASTTransformation

import static org.codehaus.groovy.ast.tools.GeneralUtils.callX

/**
 * Utility transformer used solely in non-runtime validation/conversion to suppress possible errors due to missing classes.
 */
@GroovyASTTransformation(phase = CompilePhase.CONVERSION)
class NotInCpsThreadConstructorTransformer extends ClassCodeExpressionTransformer implements ASTTransformation {
    private SourceUnit sourceUnit;

    public void visit(ASTNode[] nodes, final SourceUnit source) {
        sourceUnit = source
        source.getAST().getStatementBlock().visit(this)
    }

    @Override
    protected SourceUnit getSourceUnit() {
        return sourceUnit
    }

    @Override
    public Expression transform(Expression expr) {
        if (expr == null) return null;
        if (expr instanceof ConstructorCallExpression) {
            ConstructorCallExpression cce = (ConstructorCallExpression) expr
            cce.setType(ClassHelper.DYNAMIC_TYPE)
            return cce
        } else if (expr instanceof DeclarationExpression) {
            DeclarationExpression de = (DeclarationExpression) expr;
            Expression left = de.getLeftExpression();
            Expression right = transform(de.getRightExpression());
            DeclarationExpression newDecl = new DeclarationExpression(left, de.getOperation(), right);
            newDecl.addAnnotations(de.getAnnotations());
            return newDecl;
        } else if (expr instanceof MethodCallExpression) {
            MethodCallExpression mce = (MethodCallExpression) expr;
            Expression args = transform(mce.getArguments());
            Expression method = transform(mce.getMethod());
            Expression object = transform(mce.getObjectExpression());
            MethodCallExpression transformed = callX(object, method, args);
            transformed.setImplicitThis(mce.isImplicitThis());
            transformed.setSourcePosition(mce);
            return transformed;
        } else if (expr instanceof ClosureExpression) {
            ClosureExpression ce = (ClosureExpression) expr;
            ce.getCode().visit(this);
        }
        return expr.transformExpression(this)
    }
}