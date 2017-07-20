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
 */
package org.jenkinsci.plugins.pipeline.modeldefinition.model

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import org.codehaus.groovy.ast.ASTNode
import org.codehaus.groovy.ast.builder.AstBuilder
import org.codehaus.groovy.ast.expr.MethodCallExpression
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTBuildCondition
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTStage
import org.jenkinsci.plugins.pipeline.modeldefinition.parser.ASTParserUtils
import org.jenkinsci.plugins.pipeline.modeldefinition.parser.BlockStatementMatch

import javax.annotation.CheckForNull

import static org.jenkinsci.plugins.pipeline.modeldefinition.parser.ASTParserUtils.getAst


/**
 * A container for a closure representing a block of steps to execute.
 *
 * @author Andrew Bayer
 */
@ToString
@EqualsAndHashCode
@SuppressFBWarnings(value="SE_NO_SERIALVERSIONID")
class StepsBlock implements Serializable {
    Closure closure

    /**
     * Empty constructor to get around some weirdness...
     */
    StepsBlock() {

    }

    StepsBlock(Closure c) {
        this.closure = c
    }

    // Jumping through weird hoops to get around the ejection for cases of JENKINS-26481.
    void setClosure(Object c) {
        this.closure = (Closure) c
    }

    @CheckForNull
    static ASTNode transformToRuntimeAST(@CheckForNull ModelASTStage original) {
        Object origSrc = original?.sourceLocation
        if (origSrc != null && origSrc instanceof MethodCallExpression) {
            BlockStatementMatch stageMatch = ASTParserUtils.blockStatementFromExpression(origSrc)
            if (stageMatch != null) {
                MethodCallExpression stepsMethod = ASTParserUtils.asBlock(stageMatch.body.code).statements.find { s ->
                    ASTParserUtils.matchMethodCall(s)?.methodAsString == "steps"
                }
                if (stepsMethod != null) {
                    BlockStatementMatch stepsMatch = ASTParserUtils.blockStatementFromExpression(stepsMethod)
                    if (stepsMatch != null) {
                        return getAst(new AstBuilder().buildFromSpec {
                            returnStatement {
                                constructorCall(StepsBlock) {
                                    argumentList {
                                        expression.add(stepsMatch.body)
                                    }
                                }
                            }
                        })
                    }
                }
            }
        }

        return null
    }

    @CheckForNull
    static ASTNode transformToRuntimeAST(@CheckForNull ModelASTBuildCondition original) {
        Object origSrc = original?.sourceLocation
        if (origSrc != null && origSrc instanceof MethodCallExpression) {
            BlockStatementMatch condMatch = ASTParserUtils.blockStatementFromExpression(origSrc)
            return getAst(new AstBuilder().buildFromSpec {
                returnStatement {
                    constructorCall(StepsBlock) {
                        argumentList {
                            expression.add(condMatch.body)
                        }
                    }
                }
            })
        } else {
            return null
        }
    }
}
