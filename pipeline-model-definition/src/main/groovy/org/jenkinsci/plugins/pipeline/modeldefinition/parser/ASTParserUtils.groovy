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

import groovy.transform.stc.ClosureParams
import groovy.transform.stc.FirstParam
import hudson.model.Describable
import hudson.model.Descriptor
import org.codehaus.groovy.ast.ASTNode
import org.codehaus.groovy.ast.builder.AstBuilder
import org.codehaus.groovy.ast.expr.BinaryExpression
import org.codehaus.groovy.ast.expr.ClosureExpression
import org.codehaus.groovy.ast.expr.ConstantExpression
import org.codehaus.groovy.ast.expr.Expression
import org.codehaus.groovy.ast.expr.MapExpression
import org.codehaus.groovy.ast.expr.MethodCallExpression
import org.codehaus.groovy.ast.expr.TupleExpression
import org.codehaus.groovy.ast.expr.VariableExpression
import org.codehaus.groovy.ast.stmt.BlockStatement
import org.codehaus.groovy.ast.stmt.ExpressionStatement
import org.codehaus.groovy.ast.stmt.Statement
import org.codehaus.groovy.control.SourceUnit
import org.jenkinsci.plugins.pipeline.modeldefinition.DescriptorLookupCache
import org.jenkinsci.plugins.pipeline.modeldefinition.Messages
import org.jenkinsci.plugins.pipeline.modeldefinition.Utils
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTElement
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTValue
import org.jenkinsci.plugins.pipeline.modeldefinition.validator.ErrorCollector
import org.jenkinsci.plugins.structs.describable.DescribableModel
import org.jenkinsci.plugins.structs.describable.UninstantiatedDescribable
import org.jenkinsci.plugins.workflow.steps.StepDescriptor

import javax.annotation.CheckForNull
import javax.annotation.Nonnull


class ASTParserUtils {

    /**
     * Attempts to match a method call of the form {@code foo(...)} and
     * return 'foo' as a string.
     */
    static @CheckForNull String matchMethodName(MethodCallExpression exp) {
        def lhs = exp.objectExpression;
        if (lhs instanceof VariableExpression) {
            if (lhs.name.equals("this")) {
                return exp.methodAsString; // getMethodAsString() returns null if the method isn't a constant
            }
        }
        return null;
    }

    /**
     * Splits out and returns the {@link BlockStatementMatch} corresponding to  the given {@link MethodCallExpression}.
     */
    @CheckForNull
    static BlockStatementMatch blockStatementFromExpression(@Nonnull MethodCallExpression exp) {
        def methodName = matchMethodName(exp);
        def args = (TupleExpression)exp.arguments;
        int sz = args.expressions.size();
        if (sz>0 && methodName!=null) {
            def last = args.getExpression(sz - 1);
            if (last instanceof ClosureExpression) {
                return new BlockStatementMatch(exp,methodName,last);
            }
        }

        return null
    }

    /**
     * Normalizes a statement to a block of statement by creating a wrapper if need be.
     */
    static BlockStatement asBlock(Statement st) {
        if (st instanceof BlockStatement) {
            return st;
        } else {
            def bs = new BlockStatement();
            bs.addStatement(st);
            return bs;
        }
    }

    /**
     * Attempts to match a given statement as a method call, or return null
     */
    static @CheckForNull MethodCallExpression matchMethodCall(Statement st) {
        if (st instanceof ExpressionStatement) {
            def exp = st.expression;
            if (exp instanceof MethodCallExpression) {
                return exp;
            }
        }
        return null;
    }

    /**
     * Works like a regular Java cast, except if the value doesn't match the type, return null
     * instead of throwing an exception.
     */
    static <X> X castOrNull(Class<X> type, Object value) {
        if (type.isInstance(value))
            return type.cast(value);
        return null;
    }

    static List<String> methodNamesFromBlock(BlockStatement block) {
        return block.statements.collect { s ->
            def mc = matchMethodCall(s);
            if (mc != null) {
                return matchMethodName(mc);
            } else {
                return null
            }
        }
    }

    static <T> List<T> eachStatement(Statement st, @ClosureParams(FirstParam.class) Closure<T> c) {
        return asBlock(st).statements.collect(c)
    }

    /**
     * Attempts to match AST node as {@link BlockStatementMatch} or
     * return null.
     */
    static @CheckForNull BlockStatementMatch matchBlockStatement(Statement st) {
        def whole = matchMethodCall(st);
        if (whole!=null) {
            return blockStatementFromExpression(whole)
        }

        return null;
    }

    @CheckForNull
    static getAst(@CheckForNull List<ASTNode> nodes) {
        return nodes?.get(0)
    }

    @Nonnull
    static ASTNode transformListOfDescribables(@CheckForNull List<ModelASTElement> children) {
        return getAst(new AstBuilder().buildFromSpec {
            list {
                children?.each { d ->
                    if (d.sourceLocation instanceof MethodCallExpression) {
                        MethodCallExpression m = (MethodCallExpression) d.sourceLocation
                        expression.add(methodCallToDescribable(m))
                    }
                }
            }
        })
    }

    @CheckForNull
    static ASTNode transformDescribableContainer(@CheckForNull ModelASTElement original,
                                                 @CheckForNull List<ModelASTElement> children,
                                                 @Nonnull Class containerClass) {
        if (original == null ||
            children?.isEmpty() ||
            original.sourceLocation == null ||
            !(original.sourceLocation instanceof ASTNode)) {
            return null
        } else {
            return getAst(new AstBuilder().buildFromSpec {
                returnStatement {
                    constructorCall(containerClass) {
                        argumentList {
                            expression.add(transformListOfDescribables(children))
                        }
                    }
                }
            })
        }
    }

/*
    public ASTNode parseArgumentToSpec(Expression e, SourceUnit sourceUnit) {
        return new AstBuilder().buildFromSpec {
            if (e instanceof ConstantExpression) {
                expression.add(e)
            } else if (e instanceof GStringExpression) {
            GStringExpression g = (GStringExpression) e
            return {
                gString g.text, {
                    strings {
                        g.strings.each { s ->
                            constant s.value
                        }
                    }
                    values {
                        g.values.each { v ->
                            if (v instanceof VariableExpression) {
                                variable v.name
                            } else if (v instanceof ConstantExpression) {
                                constant v.value
                            }
                        }
                    }
                }
            }
        }
    }
*/

    static String parseStringLiteral(Expression exp, ErrorCollector errorCollector = null) {
        def s = matchStringLiteral(exp)
        if (s==null) {
            errorCollector?.error(ModelASTValue.fromConstant(null, exp), Messages.ModelParser_ExpectedStringLiteral())
        }
        return s?:"error";
    }

    static @CheckForNull String matchStringLiteral(Expression exp) {
        if (exp instanceof ConstantExpression) {
            return castOrNull(String,exp.value);
        } else if (exp instanceof VariableExpression) {
            return castOrNull(String,exp.name);
        }
        return null;
    }

    /**
     * Obtains the source text of the given {@link org.codehaus.groovy.ast.ASTNode}.
     */
    static String getSourceText(ASTNode n, SourceUnit sourceUnit) {
        return Utils.getSourceTextForASTNode(n, sourceUnit)
    }

    static String getSourceText(BinaryExpression e, SourceUnit sourceUnit) {
        return getSourceText(e.leftExpression, sourceUnit) + e.operation.getText() +
            getSourceText(e.rightExpression, sourceUnit)
    }

    @CheckForNull
    static ASTNode argsMap(List<Expression> args) {
        return getAst(new AstBuilder().buildFromSpec {
            map {
                args.each { singleArg ->
                    if (singleArg instanceof MapExpression) {
                        singleArg.mapEntryExpressions.each { entry ->
                            mapEntry {
                                expression.add(entry.keyExpression)
                                if (entry.valueExpression instanceof MethodCallExpression) {
                                    MethodCallExpression m = (MethodCallExpression) entry.valueExpression
                                    expression.add(methodCallToDescribable(m))
                                } else {
                                    expression.add(entry.valueExpression)
                                }
                            }
                        }
                    } else {
                        mapEntry {
                            constant UninstantiatedDescribable.ANONYMOUS_KEY
                            if (singleArg instanceof MethodCallExpression) {
                                expression.add(methodCallToDescribable(singleArg))
                            } else {
                                expression.add(singleArg)
                            }
                        }
                    }
                }
            }
        })
    }

    @CheckForNull
    static ASTNode methodCallToDescribable(MethodCallExpression expr) {
        def methodName = matchMethodName(expr)
        List<Expression> args = ((TupleExpression) expr.arguments).expressions

        DescriptorLookupCache lookupCache = DescriptorLookupCache.getPublicCache()

        Descriptor<? extends Describable> funcDesc = lookupCache.lookupFunction(methodName)
        StepDescriptor stepDesc = lookupCache.lookupStepDescriptor(methodName)
        // This is the case where we've got a wrapper in options
        if (stepDesc != null || (funcDesc != null && !StepDescriptor.metaStepsOf(methodName).isEmpty())) {
            return getAst(new AstBuilder().buildFromSpec {
                map {
                    mapEntry {
                        constant "name"
                        constant methodName
                    }
                    mapEntry {
                        constant "args"
                        expression.add(argsMap(args))
                    }
                }
            })
        } else if (funcDesc != null) {
            // Ok, now it's a non-executable descriptor. Phew.
            Class<? extends Describable> descType = funcDesc.clazz

            return getAst(new AstBuilder().buildFromSpec {
                methodCall {
                    constructorCall(DescribableModel) {
                        classNode(descType)
                    }
                    constant "instantiate"
                    argumentList {
                        expression.add(argsMap(args))
                    }
                }
            })
        } else {
            // Not a describable at all!
            return expr
        }
    }

}
