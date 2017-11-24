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

import com.google.common.base.Predicate
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings
import groovy.transform.stc.ClosureParams
import groovy.transform.stc.FirstParam
import hudson.model.Describable
import hudson.model.Descriptor
import hudson.model.JobProperty
import org.codehaus.groovy.ast.ASTNode
import org.codehaus.groovy.ast.ClassHelper
import org.codehaus.groovy.ast.MethodNode
import org.codehaus.groovy.ast.ModuleNode
import org.codehaus.groovy.ast.expr.*
import org.codehaus.groovy.ast.stmt.*
import org.jenkinsci.plugins.pipeline.modeldefinition.DescriptorLookupCache
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.*
import org.jenkinsci.plugins.scriptsecurity.sandbox.whitelists.Whitelisted
import org.jenkinsci.plugins.structs.describable.DescribableModel
import org.jenkinsci.plugins.structs.describable.UninstantiatedDescribable
import org.jenkinsci.plugins.workflow.actions.LabelAction
import org.jenkinsci.plugins.workflow.actions.ThreadNameAction
import org.jenkinsci.plugins.workflow.cps.CpsThread
import org.jenkinsci.plugins.workflow.cps.nodes.StepStartNode
import org.jenkinsci.plugins.workflow.flow.FlowExecution
import org.jenkinsci.plugins.workflow.graph.FlowNode
import org.jenkinsci.plugins.workflow.graph.StepNode
import org.jenkinsci.plugins.workflow.graphanalysis.Filterator
import org.jenkinsci.plugins.workflow.graphanalysis.FlowScanningUtils
import org.jenkinsci.plugins.workflow.graphanalysis.ForkScanner
import org.jenkinsci.plugins.workflow.steps.StepDescriptor
import org.kohsuke.accmod.Restricted
import org.kohsuke.accmod.restrictions.NoExternalUse

import javax.annotation.CheckForNull
import javax.annotation.Nonnull
import javax.annotation.Nullable

import static org.codehaus.groovy.ast.tools.GeneralUtils.*

/**
 * Misc. utilities used for AST interactions.
 */
@SuppressFBWarnings(value="SE_NO_SERIALVERSIONID")
class ASTParserUtils {

    /**
     * Attempts to match a method call of the form {@code foo(...)} and
     * return 'foo' as a string.
     */
    static @CheckForNull String matchMethodName(MethodCallExpression exp) {
        def lhs = exp.objectExpression
        if (lhs instanceof VariableExpression) {
            if (lhs.name == "this") {
                return exp.methodAsString // getMethodAsString() returns null if the method isn't a constant
            }
        }
        return null
    }

    // TODO: Remove or otherwise cleanup so that it's not always firing!
    static String printer(String s, int ind) {
        return "${' ' * ind * 2}${s}"
    }

    // TODO: Remove or otherwise cleanup so that it's not always firing!
    static String prettyPrint(ASTNode n, int ind = -1) {
        List<String> s = []

        ind++
        if (n instanceof ReturnStatement) {
            s << printer("- return:", ind)
            s << prettyPrint(n.expression, ind)
        } else if (n instanceof ArgumentListExpression) {
            s << printer("- args:", ind)
            n.expressions.each { s << prettyPrint(it, ind) }
        } else if (n instanceof ClosureExpression) {
            s << printer("- closure:", ind)
            s << prettyPrint(n.code, ind)
        } else if (n instanceof BlockStatement) {
            s << printer("- block", ind)
            n.statements.each {
                s << prettyPrint(it, ind)
            }
        } else if (n instanceof ConstructorCallExpression) {
            s << printer("- constructor of ${n.type.typeClass}:", ind)
            n.arguments.each {
                s << prettyPrint(it, ind)
            }
        } else if (n instanceof MapExpression) {
            s << printer("- map:", ind)
            n.mapEntryExpressions.each {
                s << prettyPrint(it, ind)
            }
        } else if (n instanceof ListExpression) {
            s << printer("- list:", ind)
            n.expressions.each {
                s << prettyPrint(it, ind)
            }
        } else if (n instanceof StaticMethodCallExpression) {
            s << printer("- static method '${n.method}':", ind)
            s << prettyPrint(n.receiver, ind)
            s << prettyPrint(n.arguments, ind)
        } else if (n instanceof MethodCallExpression) {
            s << printer("- method '${n.method}':", ind)
            s << prettyPrint(n.receiver, ind)
            s << prettyPrint(n.arguments, ind)
        }else if (n instanceof MapEntryExpression) {
            s << prettyPrint(n.keyExpression, ind)
            s << prettyPrint(n.valueExpression, ind)
        } else if (n instanceof ExpressionStatement) {
            s << prettyPrint(n.expression, ind)
        } else if (n instanceof GStringExpression) {
            s << printer("- gstring:", ind)
            ind++
            s << printer("- strings:", ind)
            n.strings.each { s << prettyPrint(it, ind) }
            s << printer("- values:", ind)
            n.values.each { s << prettyPrint(it, ind) }
        } else if (n instanceof PropertyExpression) {
            s << printer("- property:", ind)
            s << prettyPrint(n.objectExpression, ind)
            s << prettyPrint(n.property, ind)
        } else if (n instanceof ModuleNode) {
            s << printer("- module:", ind)
            ind++
            s << printer("- methods:", ind)
            n.methods.each { s << prettyPrint(it, ind) }
            s << printer("- statements:", ind)
            n.statementBlock.statements.each { s << prettyPrint(it, ind) }
        } else if (n instanceof MethodNode) {
            s << printer("- methodNode:", ind)
            s << prettyPrint(n.code, ind)
        } else if (n instanceof ThrowStatement) {
            s << printer("- throw:", ind)
            s << prettyPrint(n.expression, ind)
        } else if (n instanceof CastExpression) {
            s << printer("- cast:", ind)
            s << prettyPrint(n.expression, ind)
        } else if (n instanceof VariableExpression) {
            s << printer("- var:", ind)
            s << printer("  - name: ${n.name}", ind)
            s << printer("  - accessedVariable: ${n.accessedVariable}", ind)
        } else if (n instanceof PrefixExpression) {
            s << printer("- prefix (${n.operation}):", ind)
            s << prettyPrint(n.expression, ind)
        } else if (n instanceof PostfixExpression) {
            s << printer("- postfix (${n.operation}):", ind)
            s << prettyPrint(n.expression, ind)
        } else if (n instanceof ElvisOperatorExpression) {
            s << printer("- elvis:", ind)
            s << prettyPrint(n.trueExpression, ind)
            s << prettyPrint(n.falseExpression, ind)
        } else if (n instanceof BinaryExpression) {
            s << printer("- binary:", ind)
            s << printer("  - left:", ind)
            s << prettyPrint(n.leftExpression, ind)
            s << printer(" - op: ${n.operation.toString()}", ind)
            s << printer("  - right:", ind)
            s << prettyPrint(n.rightExpression, ind)
        } else if (n instanceof DeclarationExpression) {
            s << printer("- decl:", ind)
            s << printer("  - left:", ind)
            s << prettyPrint(n.leftExpression, ind)
            s << printer(" - op: ${n.operation.toString()}", ind)
            s << printer("  - right:", ind)
            s << prettyPrint(n.rightExpression, ind)
        } else {
            s << printer("- ${n}", ind)
        }

        return s.join("\n")
    }

    /**
     * Splits out and returns the {@link BlockStatementMatch} corresponding to  the given {@link MethodCallExpression}.
     */
    @CheckForNull
    static BlockStatementMatch blockStatementFromExpression(@Nonnull MethodCallExpression exp) {
        def methodName = matchMethodName(exp)
        def args = (TupleExpression)exp.arguments
        int sz = args.expressions.size()
        if (sz>0 && methodName!=null) {
            def last = args.getExpression(sz - 1)
            if (last instanceof ClosureExpression) {
                return new BlockStatementMatch(exp,methodName,last)
            }
        }

        return null
    }

    /**
     * Normalizes a statement to a block of statement by creating a wrapper if need be.
     */
    static BlockStatement asBlock(Statement st) {
        if (st instanceof BlockStatement) {
            return st
        } else {
            def bs = new BlockStatement()
            bs.addStatement(st)
            return bs
        }
    }

    /**
     * Attempts to match a given statement as a method call, or return null
     */
    static @CheckForNull MethodCallExpression matchMethodCall(Statement st) {
        if (st instanceof ExpressionStatement) {
            def exp = st.expression
            if (exp instanceof MethodCallExpression) {
                return exp
            }
        }
        return null
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
        def whole = matchMethodCall(st)
        if (whole!=null) {
            return blockStatementFromExpression(whole)
        }

        return null
    }

    /**
     * Takes a list of {@link ModelASTElement}s corresponding to {@link Describable}s (such as {@link JobProperty}s, etc),
     * and transforms their Groovy AST nodes into AST from {@link #methodCallToDescribable(MethodCallExpression,Class)}.
     */
    @Nonnull
    static Expression transformListOfDescribables(@CheckForNull List<ModelASTElement> children, Class<? extends Describable> descClass) {
        ListExpression descList = new ListExpression()

        children?.each { d ->
            if (d.sourceLocation instanceof Statement) {
                MethodCallExpression m = matchMethodCall((Statement) d.sourceLocation)
                if (m != null) {
                    descList.addExpression(methodCallToDescribable(m,descClass))
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
     * Transforms a container for describables, such as {@code Triggers}, into AST for instantation.
     * @param original A {@link ModelASTElement} such as {@link ModelASTTriggers} or {@link ModelASTBuildParameters}
     * @param children The children for the original element - passed as a separate argument since the getter will
     * be different.
     * @param containerClass The class we will be instantiating, i.e., {@code Parameters} or {@code Triggers}.
     * @param descClass the describable class we're inheriting from
     * @return The AST for instantiating the container and its contents.
     */
    static Expression transformDescribableContainer(@CheckForNull ModelASTElement original,
                                                    @CheckForNull List<ModelASTElement> children,
                                                    @Nonnull Class containerClass,
                                                    @Nonnull Class<? extends Describable> descClass) {
        if (isGroovyAST(original) && !children?.isEmpty()) {
            return ctorX(ClassHelper.make(containerClass), args(transformListOfDescribables(children, descClass)))
        }
        return constX(null)
    }


    @Whitelisted
    @Restricted(NoExternalUse.class)
    static <T> T instantiateDescribable(Class<T> c, Map<String, ?> args) {
        DescribableModel<T> model = new DescribableModel<>(c)
        return model?.instantiate(args)
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
                        tmpMap.addMapEntryExpression(entry.keyExpression, methodCallToDescribable(m, null))
                    } else {
                        tmpMap.addMapEntryExpression(entry)
                    }
                }
            } else {
                if (singleArg instanceof MethodCallExpression) {
                    tmpMap.addMapEntryExpression(constX(UninstantiatedDescribable.ANONYMOUS_KEY),
                        methodCallToDescribable(singleArg, null))
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
     * {@link #instantiateDescribable(Class,Map)} that can be invoked at runtime to actually instantiated.
     * @param expr A method call.
     * @param descClass possibly null describable parent class
     * @return The appropriate transformation, or the original expression if it didn't correspond to a Describable.
     */
    @CheckForNull
    static Expression methodCallToDescribable(MethodCallExpression expr, Class<? extends Describable> descClass) {
        def methodName = matchMethodName(expr)
        List<Expression> methArgs = methodCallArgs(expr)

        DescriptorLookupCache lookupCache = DescriptorLookupCache.getPublicCache()

        Descriptor<? extends Describable> funcDesc = lookupCache.lookupFunction(methodName, descClass)
        StepDescriptor stepDesc = lookupCache.lookupStepDescriptor(methodName)
        // This is the case where we've got a wrapper in options
        if (stepDesc != null ||
            (funcDesc != null && !StepDescriptor.metaStepsOf(methodName)?.isEmpty())) {
            MapExpression m = new MapExpression()
            m.addMapEntryExpression(constX("name"), constX(methodName))
            m.addMapEntryExpression(constX("args"), argsMap(methArgs))
            return m
        } else if (funcDesc != null) {
            // Ok, now it's a non-executable descriptor. Phew.
            Class<? extends Describable> descType = funcDesc.clazz

            return callX(ClassHelper.make(ASTParserUtils.class), "instantiateDescribable",
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

    static boolean isStageNode(@Nonnull FlowNode node) {
        if (node instanceof StepNode) {
            StepDescriptor d = ((StepNode) node).getDescriptor();
            return d != null && d.getFunctionName().equals("stage");
        } else {
            return false;
        }
    }

    static Predicate<FlowNode> isStageWithOptionalName(final String stageName = null) {
        return new Predicate<FlowNode>() {
            @Override
            boolean apply(@Nullable FlowNode input) {
                if (input != null) {
                    if (input instanceof StepStartNode &&
                        isStageNode(input) &&
                        (stageName == null || input.displayName == stageName)) {
                        // This is a true stage.
                        return true
                    } else if (input.getAction(LabelAction.class) != null &&
                        input.getAction(ThreadNameAction.class) != null &&
                        (stageName == null || input.getAction(ThreadNameAction)?.threadName == stageName)) {
                        // This is actually a parallel block
                        return true
                    }
                }

                return false
            }
        }
    }

    static List<FlowNode> findStageFlowNodes(String stageName, FlowExecution execution = null) {
        if (execution == null) {
            CpsThread thread = CpsThread.current()
            execution = thread.execution
        }

        List<FlowNode> nodes = []

        ForkScanner scanner = new ForkScanner()

        FlowNode stage = scanner.findFirstMatch(execution.currentHeads, null, isStageWithOptionalName(stageName))

        if (stage != null) {
            nodes.add(stage)

            // Additional check needed to get the possible enclosing parallel branch for a nested stage.
            Filterator<FlowNode> filtered = FlowScanningUtils.fetchEnclosingBlocks(stage)
                .filter(isParallelBranchFlowNode(stageName))

            filtered.each { f ->
                if (f != null) {
                    nodes.add(f)
                }
            }
        }

        return nodes
    }

    static Predicate<FlowNode> isParallelBranchFlowNode(final String stageName) {
        return new Predicate<FlowNode>() {
            @Override
            boolean apply(@Nullable FlowNode input) {
                if (input != null) {
                    if (input.getAction(LabelAction.class) != null &&
                        input.getAction(ThreadNameAction.class) != null &&
                        (stageName == null || input.getAction(ThreadNameAction)?.threadName == stageName)) {
                        // This is actually a parallel block
                        return true
                    }
                }
                return false
            }
        }
    }

    static boolean blockHasMethod(BlockStatement block, String methodName) {
        if (block != null) {
            return block.statements.any {
                MethodCallExpression expr = matchMethodCall(it)
                return expr != null && matchMethodName(expr) == methodName
            }
        } else {
            return false
        }
    }
}
