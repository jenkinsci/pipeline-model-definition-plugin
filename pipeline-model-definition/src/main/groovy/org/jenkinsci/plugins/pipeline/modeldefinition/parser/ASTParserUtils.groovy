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

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings
import groovy.transform.stc.ClosureParams
import groovy.transform.stc.FirstParam
import hudson.model.Describable
import hudson.model.Descriptor
import org.codehaus.groovy.ast.ASTNode
import org.codehaus.groovy.ast.ClassHelper
import org.codehaus.groovy.ast.MethodNode
import org.codehaus.groovy.ast.ModuleNode
import org.codehaus.groovy.ast.expr.*
import org.codehaus.groovy.ast.stmt.*
import org.jenkinsci.plugins.pipeline.modeldefinition.DescriptorLookupCache
import org.jenkinsci.plugins.pipeline.modeldefinition.Utils
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.*
import org.jenkinsci.plugins.pipeline.modeldefinition.when.DeclarativeStageConditional
import org.jenkinsci.plugins.pipeline.modeldefinition.when.DeclarativeStageConditionalDescriptor
import org.jenkinsci.plugins.structs.SymbolLookup
import org.jenkinsci.plugins.structs.describable.UninstantiatedDescribable
import org.jenkinsci.plugins.workflow.steps.StepDescriptor

import javax.annotation.CheckForNull
import javax.annotation.Nonnull

import static org.codehaus.groovy.ast.tools.GeneralUtils.*

/**
 * Misc. utilities used across both {@link ModelParser} and {@link RuntimeASTTransformer}.
 */
@SuppressFBWarnings(value="SE_NO_SERIALVERSIONID")
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

    // TODO: Remove or otherwise cleanup so that it's not always firing!
    static printer(String s, int ind) {
        System.err.println("${' ' * ind * 2}${s}")
    }

    // TODO: Remove or otherwise cleanup so that it's not always firing!
    static prettyPrint(ASTNode n, int ind = -1) {
        ind++
        if (n instanceof ReturnStatement) {
            printer("- return:", ind)
            prettyPrint(n.expression, ind)
        } else if (n instanceof ArgumentListExpression) {
            printer("- args:", ind)
            n.expressions.each { prettyPrint(it, ind) }
        } else if (n instanceof ClosureExpression) {
            printer("- closure:", ind)
            prettyPrint(n.code, ind)
        } else if (n instanceof BlockStatement) {
            printer("- block", ind)
            n.statements.each {
                prettyPrint(it, ind)
            }
        } else if (n instanceof ConstructorCallExpression) {
            printer("- constructor of ${n.type.typeClass}:", ind)
            n.arguments.each {
                prettyPrint(it, ind)
            }
        } else if (n instanceof MapExpression) {
            printer("- map:", ind)
            n.mapEntryExpressions.each {
                prettyPrint(it, ind)
            }
        } else if (n instanceof ListExpression) {
            printer("- list:", ind)
            n.expressions.each {
                prettyPrint(it, ind)
            }
        } else if (n instanceof StaticMethodCallExpression) {
            printer("- static method '${n.method}':", ind)
            prettyPrint(n.receiver, ind)
            prettyPrint(n.arguments, ind)
        } else if (n instanceof MethodCallExpression) {
            printer("- method '${n.method}':", ind)
            prettyPrint(n.receiver, ind)
            prettyPrint(n.arguments, ind)
        }else if (n instanceof MapEntryExpression) {
            prettyPrint(n.keyExpression, ind)
            prettyPrint(n.valueExpression, ind)
        } else if (n instanceof ExpressionStatement) {
            prettyPrint(n.expression, ind)
        } else if (n instanceof GStringExpression) {
            printer("- gstring:", ind)
            ind++
            printer("- strings:", ind)
            n.strings.each { prettyPrint(it, ind) }
            printer("- values:", ind)
            n.values.each { prettyPrint(it, ind) }
        } else if (n instanceof PropertyExpression) {
            printer("- property:", ind)
            prettyPrint(n.objectExpression, ind)
            prettyPrint(n.property, ind)
        } else if (n instanceof ModuleNode) {
            printer("- module:", ind)
            ind++
            printer("- methods:", ind)
            n.methods.each { prettyPrint(it, ind) }
            printer("- statements:", ind)
            n.statementBlock.statements.each { prettyPrint(it, ind) }
        } else if (n instanceof MethodNode) {
            printer("- methodNode:", ind)
            prettyPrint(n.code, ind)
        } else if (n instanceof ThrowStatement) {
            printer("- throw:", ind)
            prettyPrint(n.expression, ind)
        } else if (n instanceof CastExpression) {
            printer("- cast:", ind)
            prettyPrint(n.expression, ind)
        } else if (n instanceof VariableExpression) {
            printer("- var:", ind)
            printer("  - name: ${n.name}", ind)
            printer("  - accessedVariable: ${n.accessedVariable}", ind)
        } else if (n instanceof PrefixExpression) {
            printer("- prefix (${n.operation}):", ind)
            prettyPrint(n.expression, ind)
        } else if (n instanceof PostfixExpression) {
            printer("- postfix (${n.operation}):", ind)
            prettyPrint(n.expression, ind)
        } else {
            printer("- ${n}", ind)
        }
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
     * Takes a statement and iterates over its contents - if the statement is not a {@link BlockStatement}, it gets
     * wrapped in a new block to simplify iteration.
     */
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

    /**
     * Takes a list of {@link ModelASTElement}s corresponding to {@link Describable}s (such as {@link JobProperty}s, etc),
     * and transforms their Groovy AST nodes into AST from {@link #methodCallToDescribable(MethodCallExpression)}.
     */
    @Nonnull
    static Expression transformListOfDescribables(@CheckForNull List<ModelASTElement> children) {
        ListExpression descList = new ListExpression()

        children?.each { d ->
            if (d.sourceLocation instanceof Statement) {
                MethodCallExpression m = matchMethodCall((Statement) d.sourceLocation)
                if (m != null) {
                    descList.addExpression(methodCallToDescribable(m))
                } else {
                    throw new IllegalArgumentException("Expected a method call expression but received ${d.sourceLocation}")
                }
            } else {
                throw new IllegalArgumentException("Expected a statement but received ${d.sourceLocation}")
            }
        }

        return descList
    }

    /**
     * Transforms a container for describables, such as {@link Triggers}, into AST for instantation.
     * @param original A {@link ModelASTElement} such as {@link ModelASTTriggers} or {@link ModelASTBuildParameters}
     * @param children The children for the original element - passed as a separate argument since the getter will
     * be different.
     * @param containerClass The class we will be instantiating, i.e., {@link Parameters} or {@link Triggers}.
     * @return The AST for instantiating the container and its contents.
     */
    static Expression transformDescribableContainer(@CheckForNull ModelASTElement original,
                                                 @CheckForNull List<ModelASTElement> children,
                                                 @Nonnull Class containerClass) {
        if (isGroovyAST(original) && !children?.isEmpty()) {
            return ctorX(ClassHelper.make(containerClass), args(transformListOfDescribables(children)))
        }
        return constX(null)
    }

    /**
     * Transform a when condition, and its children if any exist, into instantiation AST.
     */
    static Expression transformWhenContentToRuntimeAST(@CheckForNull ModelASTWhenContent original) {
        if (original instanceof ModelASTElement && isGroovyAST((ModelASTElement)original)) {
            DeclarativeStageConditionalDescriptor parentDesc =
                (DeclarativeStageConditionalDescriptor) SymbolLookup.get().findDescriptor(
                    DeclarativeStageConditional.class, original.name)
            if (original instanceof ModelASTWhenCondition) {
                ModelASTWhenCondition cond = (ModelASTWhenCondition) original
                if (cond.getSourceLocation() != null && cond.getSourceLocation() instanceof Statement) {
                    MethodCallExpression methCall = matchMethodCall((Statement) cond.getSourceLocation())

                    if (methCall != null) {
                        if (cond.children.isEmpty()) {
                            return methodCallToDescribable(methCall)
                        } else {
                            MapExpression argMap = new MapExpression()
                            if (parentDesc.allowedChildrenCount == 1) {
                                argMap.addMapEntryExpression(constX(UninstantiatedDescribable.ANONYMOUS_KEY),
                                    transformWhenContentToRuntimeAST(cond.children.first()))
                            } else {
                                argMap.addMapEntryExpression(constX(UninstantiatedDescribable.ANONYMOUS_KEY),
                                    new ListExpression(cond.children.collect { transformWhenContentToRuntimeAST(it) }))
                            }
                            return callX(ClassHelper.make(Utils.class),
                                "instantiateDescribable",
                                args(
                                    classX(parentDesc.clazz),
                                    argMap
                                ))
                        }
                    }
                }
            } else if (original instanceof ModelASTWhenExpression) {
                return parentDesc.transformToRuntimeAST(original)
            }
        }
        return constX(null)
    }

    /**
     * Transforms the AST for a "mapped closure" - i.e., a closure of "foo 'bar'" method calls - into a
     * {@link MapExpression}. Recurses for nested "mapped closures" as well.
     * @param original a possibly null {@link ClosureExpression} to transform
     * @return A {@link MapExpression}, or null if the original expression was null.
     */
    @CheckForNull
    static Expression recurseAndTransformMappedClosure(@CheckForNull ClosureExpression original) {
        if (original != null) {
            MapExpression mappedClosure = new MapExpression()
            eachStatement(original.code) { s ->
                MethodCallExpression mce = matchMethodCall(s)
                if (mce != null) {
                    List<Expression> args = methodCallArgs(mce)
                    if (args.size() == 1) {
                        Expression singleArg = args.get(0)
                        if (singleArg instanceof ClosureExpression) {
                            mappedClosure.addMapEntryExpression(mce.method, recurseAndTransformMappedClosure(singleArg))
                        } else {
                            mappedClosure.addMapEntryExpression(mce.method, singleArg)
                        }
                    }
                }
            }
            return mappedClosure
        }

        return null
    }

    /**
     * Takes a list of expressions used as arguments that could contain describables, and creates a MapExpression
     * suitable for DescribableModel.instantiate.
     * @param args A list of arguments
     * @return A MapExpression
     */
    @CheckForNull
    static Expression argsMap(List<Expression> args) {
        MapExpression tmpMap = new MapExpression()
        args.each { singleArg ->
            if (singleArg instanceof MapExpression) {
                singleArg.mapEntryExpressions.each { entry ->
                    if (entry.valueExpression instanceof MethodCallExpression) {
                        MethodCallExpression m = (MethodCallExpression) entry.valueExpression
                        tmpMap.addMapEntryExpression(entry.keyExpression, methodCallToDescribable(m))
                    } else {
                        tmpMap.addMapEntryExpression(entry)
                    }
                }
            } else {
                if (singleArg instanceof MethodCallExpression) {
                    tmpMap.addMapEntryExpression(constX(UninstantiatedDescribable.ANONYMOUS_KEY),
                        methodCallToDescribable(singleArg))
                } else {
                    tmpMap.addMapEntryExpression(constX(UninstantiatedDescribable.ANONYMOUS_KEY), singleArg)
                }
            }
        }

        return tmpMap
    }

    /**
     * A shortcut for taking the Expression at MethodCallExpression.arguments and turning it into a list of Expressions.
     * @param expr A method call
     * @return A possibly empty list of expressions
     */
    @Nonnull
    static List<Expression> methodCallArgs(@Nonnull MethodCallExpression expr) {
        return ((TupleExpression) expr.arguments).expressions
    }

    /**
     * Transforms a {@link MethodCallExpression} into either a map of name and arguments for steps, or a call to
     * {@link Utils#instantiateDescribable(Class,Map)} that can be invoked at runtime to actually instantiated.
     * @param expr A method call.
     * @return The appropriate transformation, or the original expression if it didn't correspond to a Describable.
     */
    @CheckForNull
    static Expression methodCallToDescribable(MethodCallExpression expr) {
        def methodName = matchMethodName(expr)
        List<Expression> methArgs = methodCallArgs(expr)

        DescriptorLookupCache lookupCache = DescriptorLookupCache.getPublicCache()

        Descriptor<? extends Describable> funcDesc = lookupCache.lookupFunction(methodName)
        StepDescriptor stepDesc = lookupCache.lookupStepDescriptor(methodName)
        // This is the case where we've got a wrapper in options
        if (stepDesc != null || (funcDesc != null && !StepDescriptor.metaStepsOf(methodName).isEmpty())) {
            MapExpression m = new MapExpression()
            m.addMapEntryExpression(constX("name"), constX(methodName))
            m.addMapEntryExpression(constX("args"), argsMap(methArgs))
            return m
        } else if (funcDesc != null) {
            // Ok, now it's a non-executable descriptor. Phew.
            Class<? extends Describable> descType = funcDesc.clazz

            return callX(ClassHelper.make(Utils.class), "instantiateDescribable",
                args(classX(descType), argsMap(methArgs)))
        } else {
            // Not a describable at all!
            return expr
        }
    }

    /**
     * Determine whether this element can be used for Groovy AST transformation
     * @param original
     * @return True if the element isn't null, it has a source location, and that source location is an {@link ASTNode}
     */
    static boolean isGroovyAST(ModelASTElement original) {
        return original != null && original.sourceLocation != null && original.sourceLocation instanceof ASTNode
    }
}
