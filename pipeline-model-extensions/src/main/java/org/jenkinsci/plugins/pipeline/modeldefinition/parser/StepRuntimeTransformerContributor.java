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
 */

package org.jenkinsci.plugins.pipeline.modeldefinition.parser;

import hudson.ExtensionList;
import hudson.ExtensionPoint;
import org.codehaus.groovy.ast.expr.ArgumentListExpression;
import org.codehaus.groovy.ast.expr.ClosureExpression;
import org.codehaus.groovy.ast.expr.MapExpression;
import org.codehaus.groovy.ast.expr.MethodCallExpression;
import org.codehaus.groovy.ast.expr.TupleExpression;
import org.codehaus.groovy.ast.expr.VariableExpression;
import org.codehaus.groovy.ast.stmt.BlockStatement;
import org.codehaus.groovy.ast.stmt.ExpressionStatement;
import org.codehaus.groovy.ast.stmt.Statement;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.AbstractModelASTCodeBlock;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTBranch;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTBuildCondition;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTStage;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTStep;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTTreeStep;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

import static org.codehaus.groovy.ast.tools.GeneralUtils.*;

public abstract class StepRuntimeTransformerContributor implements ExtensionPoint {

    /**
     * Construct the new {@link ClosureExpression} for the given stage.
     */
    @Nonnull
    public final ClosureExpression handleStage(@Nonnull ModelASTStage stage, @Nonnull ClosureExpression body) {
        if (stage.getBranches().size() == 1) {
            ModelASTBranch branch = stage.getBranches().get(0);
            body.setCode(handleBranch(branch));
        } else {
            // Parallel case - multiple branches yay.
            MethodCallExpression methExpr = getParallelMethod(body.getCode());
            if (methExpr != null) {
                TupleExpression parallelArgs = (TupleExpression)methExpr.getArguments();

                // Make sure the arguments consist of a single entry in a tuple, and that single entry is a map.
                if (parallelArgs.getExpressions().size() == 1 &&
                        parallelArgs.getExpression(0) instanceof MapExpression) {
                    MapExpression newParallelMap = new MapExpression();

                    for (ModelASTBranch b : stage.getBranches()) {
                        newParallelMap.addMapEntryExpression(constX(b.getName()), closureX(handleBranch(b)));
                    }

                    body.setCode(
                            block(
                                    stmt(
                                            callX(
                                                    varX("this"),
                                                                constX("parallel"),
                                                    args(newParallelMap)
                                            )
                                    )
                            )
                    );
                }
            }
        }

        return body;
    }

    /**
     * Construct the new {@link ClosureExpression} for the given build condition.
     */
    @Nonnull
    public final ClosureExpression handleBuildCondition(@Nonnull ModelASTBuildCondition condition, @Nonnull ClosureExpression body) {
        body.setCode(handleBranch(condition.getBranch()));
        return body;
    }

    /**
     * Construct the new {@link BlockStatement} for the given branch.
     */
    @Nonnull
    public final BlockStatement handleBranch(@Nonnull ModelASTBranch branch) {
        BlockStatement newBlock = block();

        for (ModelASTStep s : branch.getSteps()) {
            // Don't process script blocks or a step that for some reason isn't an expression statement at all
            if (s instanceof AbstractModelASTCodeBlock || !(s.getSourceLocation() instanceof ExpressionStatement)) {
                newBlock.addStatement((Statement)s.getSourceLocation());
            } else {
                ExpressionStatement es = (ExpressionStatement) s.getSourceLocation();

                if (es.getExpression() instanceof MethodCallExpression) {
                    MethodCallExpression methodCall = (MethodCallExpression) es.getExpression();
                    newBlock.addStatement(stmt(handleStep(s, methodCall)));
                } else {
                    newBlock.addStatement(es);
                }
            }
        }

        return newBlock;
    }

    /**
     * Call {@link #transformStep(ModelASTStep, MethodCallExpression)} if appropriate, after handling any nested steps as well.
     */
    @Nonnull
    public final MethodCallExpression handleStep(@Nonnull ModelASTStep step, @Nonnull MethodCallExpression methodCall) {
        // No transformation inside script blocks.
        if (step instanceof AbstractModelASTCodeBlock) {
            return methodCall;
        }

        TupleExpression originalArgs = (TupleExpression) methodCall.getArguments();
        if (step instanceof ModelASTTreeStep && originalArgs.getExpressions().size() > 0) {
            ArgumentListExpression newArgs = new ArgumentListExpression();

            // Technically we can't get here if there 0 expressions, so the loop below is safe.
            for (int i = 0; i < originalArgs.getExpressions().size() - 1; i++) {
                newArgs.addExpression(originalArgs.getExpression(i));
            }
            ClosureExpression originalClosure = (ClosureExpression) originalArgs.getExpression(originalArgs.getExpressions().size() - 1);
            BlockStatement newBlock = block();

            for (ModelASTStep nested : ((ModelASTTreeStep) step).getChildren()) {
                ExpressionStatement es = (ExpressionStatement) nested.getSourceLocation();
                newBlock.addStatement(stmt(handleStep(nested, (MethodCallExpression)es.getExpression())));
            }

            originalClosure.setCode(newBlock);

            newArgs.addExpression(originalClosure);
            methodCall.setArguments(newArgs);
        }

        return transformStep(step, methodCall);
    }

    @Nonnull
    public abstract MethodCallExpression transformStep(@Nonnull ModelASTStep step, @Nonnull MethodCallExpression methodCall);

    @CheckForNull
    private MethodCallExpression getParallelMethod(@Nonnull Statement stmt) {
        // Make sure we've got a block.
        if (stmt instanceof BlockStatement) {
            BlockStatement block = (BlockStatement)stmt;
            // Make sure that block has one statement and that one statement is an expression
            if (block.getStatements().size() == 1 && block.getStatements().get(0) instanceof ExpressionStatement) {
                ExpressionStatement exprStmt = (ExpressionStatement) block.getStatements().get(0);
                // Make sure the expression in there is a method call.
                if (exprStmt.getExpression() instanceof MethodCallExpression) {
                    MethodCallExpression methExpr = (MethodCallExpression) exprStmt.getExpression();
                    // Make sure the method is "parallel", the receiver is "this", and the arguments are a tuple
                    if ("parallel".equals(methExpr.getMethodAsString()) &&
                            methExpr.getReceiver() instanceof VariableExpression &&
                            "this".equals(((VariableExpression) methExpr.getReceiver()).getName()) &&
                            methExpr.getArguments() instanceof TupleExpression) {
                        return methExpr;
                    }
                }
            }
        }

        return null;
    }

    /**
     * Get all {@link StepRuntimeTransformerContributor}s.
     *
     * @return a list of all {@link StepRuntimeTransformerContributor}s registered.
     */
    public static ExtensionList<StepRuntimeTransformerContributor> all() {
        return ExtensionList.lookup(StepRuntimeTransformerContributor.class);
    }

    /**
     * Apply step transformation to the given stage for all {@link StepRuntimeTransformerContributor}s.
     */
    @Nonnull
    public static ClosureExpression transformStage(@Nonnull ModelASTStage stage, @Nonnull ClosureExpression body) {
        for (StepRuntimeTransformerContributor c : all()) {
            body = c.handleStage(stage, body);
        }

        return body;
    }

    /**
     * Apply step transformation to the given build condition for all {@link StepRuntimeTransformerContributor}s.
     */
    @Nonnull
    public static ClosureExpression transformBuildCondition(@Nonnull ModelASTBuildCondition condition,
                                                            @Nonnull ClosureExpression body) {
        for (StepRuntimeTransformerContributor c : all()) {
            body = c.handleBuildCondition(condition, body);
        }

        return body;
    }
}
