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

package org.jenkinsci.plugins.pipeline.modeldefinition

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings
import org.codehaus.groovy.ast.ClassHelper
import org.codehaus.groovy.ast.expr.Expression
import org.codehaus.groovy.ast.expr.ListExpression
import org.codehaus.groovy.ast.expr.MapExpression
import org.codehaus.groovy.ast.expr.MethodCallExpression
import org.codehaus.groovy.ast.stmt.Statement
import org.codehaus.groovy.ast.tools.GeneralUtils
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTElement
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTWhenCondition
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTWhenContent
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTWhenExpression
import org.jenkinsci.plugins.pipeline.modeldefinition.parser.ASTParserUtils
import org.jenkinsci.plugins.pipeline.modeldefinition.when.DeclarativeStageConditional
import org.jenkinsci.plugins.pipeline.modeldefinition.when.DeclarativeStageConditionalDescriptor
import org.jenkinsci.plugins.structs.SymbolLookup
import org.jenkinsci.plugins.structs.describable.UninstantiatedDescribable

import javax.annotation.CheckForNull

@SuppressFBWarnings(value="SE_NO_SERIALVERSIONID")
class DeclarativeExtensionUtils {

    /**
     * Transform a when condition, and its children if any exist, into instantiation AST.
     */
    static Expression transformWhenContentToRuntimeAST(@CheckForNull ModelASTWhenContent original) {
        if (original instanceof ModelASTElement && ASTParserUtils.isGroovyAST((ModelASTElement)original)) {
            DeclarativeStageConditionalDescriptor parentDesc =
                (DeclarativeStageConditionalDescriptor) SymbolLookup.get().findDescriptor(
                    DeclarativeStageConditional.class, original.name)
            if (original instanceof ModelASTWhenCondition) {
                ModelASTWhenCondition cond = (ModelASTWhenCondition) original
                if (cond.getSourceLocation() != null && cond.getSourceLocation() instanceof Statement) {
                    MethodCallExpression methCall = ASTParserUtils.matchMethodCall((Statement) cond.getSourceLocation())

                    if (methCall != null) {
                        if (cond.children.isEmpty()) {
                            return ASTParserUtils.methodCallToDescribable(methCall, null)
                        } else {
                            MapExpression argMap = new MapExpression()
                            if (parentDesc.allowedChildrenCount == 1) {
                                argMap.addMapEntryExpression(GeneralUtils.constX(UninstantiatedDescribable.ANONYMOUS_KEY),
                                    transformWhenContentToRuntimeAST(cond.children.first()))
                            } else {
                                argMap.addMapEntryExpression(GeneralUtils.constX(UninstantiatedDescribable.ANONYMOUS_KEY),
                                    new ListExpression(cond.children.collect { transformWhenContentToRuntimeAST(it) }))
                            }
                            return GeneralUtils.callX(ClassHelper.make(ASTParserUtils.class),
                                "instantiateDescribable",
                                GeneralUtils.args(
                                    GeneralUtils.classX(parentDesc.clazz),
                                    argMap
                                ))
                        }
                    }
                }
            } else if (original instanceof ModelASTWhenExpression) {
                return parentDesc.transformToRuntimeAST(original)
            }
        }
        return GeneralUtils.constX(null)
    }
}
