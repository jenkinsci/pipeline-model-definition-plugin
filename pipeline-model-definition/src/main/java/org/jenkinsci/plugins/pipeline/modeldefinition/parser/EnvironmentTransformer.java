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

package org.jenkinsci.plugins.pipeline.modeldefinition.parser;

import groovyjarjarasm.asm.Opcodes;
import org.codehaus.groovy.ast.ClassCodeExpressionTransformer;
import org.codehaus.groovy.ast.ClassHelper;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.MethodNode;
import org.codehaus.groovy.ast.Parameter;
import org.codehaus.groovy.ast.expr.ArgumentListExpression;
import org.codehaus.groovy.ast.expr.BinaryExpression;
import org.codehaus.groovy.ast.expr.CastExpression;
import org.codehaus.groovy.ast.expr.ConstantExpression;
import org.codehaus.groovy.ast.expr.ConstructorCallExpression;
import org.codehaus.groovy.ast.expr.Expression;
import org.codehaus.groovy.ast.expr.GStringExpression;
import org.codehaus.groovy.ast.expr.MethodCallExpression;
import org.codehaus.groovy.ast.expr.PropertyExpression;
import org.codehaus.groovy.ast.expr.StaticMethodCallExpression;
import org.codehaus.groovy.ast.expr.TupleExpression;
import org.codehaus.groovy.ast.expr.VariableExpression;
import org.codehaus.groovy.ast.stmt.BlockStatement;
import org.codehaus.groovy.ast.stmt.ExpressionStatement;
import org.codehaus.groovy.ast.stmt.ReturnStatement;
import org.codehaus.groovy.ast.stmt.Statement;
import org.codehaus.groovy.ast.tools.GeneralUtils;
import org.codehaus.groovy.control.SourceUnit;
import org.codehaus.groovy.runtime.MetaClassHelper;
import org.codehaus.groovy.syntax.Token;
import org.codehaus.groovy.syntax.Types;
import org.jenkinsci.plugins.pipeline.modeldefinition.Utils;
import org.jenkinsci.plugins.workflow.cps.CpsScript;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class EnvironmentTransformer extends ClassCodeExpressionTransformer {
    private final SourceUnit sourceUnit;
    private final EnvCounter envCounter;

    public EnvironmentTransformer(SourceUnit sourceUnit, EnvCounter envCounter) {
        this.sourceUnit = sourceUnit;
        this.envCounter = envCounter;
    }

    @Override
    protected SourceUnit getSourceUnit() {
        return sourceUnit;
    }

    @Override
    public Expression transform(Expression exp) {
        if (exp != null && exp instanceof MethodCallExpression) {
            MethodCallExpression methodCallExpression = (MethodCallExpression) exp;
            BlockStatementMatch block = ModelParser.blockStatementFromExpression(methodCallExpression);

            // Match only {@code environment { ... } }
            if (block != null &&
                    block.methodName.equals("environment") &&
                    block.arguments.getExpressions().size() == 1 &&
                    block.body != null) {
                BlockStatement body = ModelParser.asBlock(block.body.getCode());

                Map<String,Expression> rawEntries = new HashMap<>();

                for (Statement s : body.getStatements()) {
                    if (s instanceof ExpressionStatement) {
                        ExpressionStatement expr = (ExpressionStatement) s;

                        if (expr.getExpression() instanceof BinaryExpression &&
                                ((BinaryExpression) expr.getExpression()).getOperation().getType() == Types.EQUAL) {
                            BinaryExpression binaryExpression = (BinaryExpression) expr.getExpression();

                            if (binaryExpression.getLeftExpression() instanceof VariableExpression) {
                                String varName = ((VariableExpression) binaryExpression.getLeftExpression()).getName();

                                rawEntries.put(varName, binaryExpression.getLeftExpression());
                            } else {
                                // TODO: The left side isn't a variable!
                            }
                        } else {
                            // TODO: It's not a binary expression or it's not a foo = bar, which is bad.
                        }
                    } else {
                        // TODO: It's not any kind of expression, something is deeply wrong.
                    }
                }

                ClassNode classNode = new ClassNode("EnvironmentResolver" + envCounter.getAndIncr(),
                        Opcodes.ACC_PUBLIC,
                        ClassHelper.make(AbstractEnvironmentResolver.class));

                for (Map.Entry<String,Expression> entry : rawEntries.entrySet()) {
                    String k = entry.getKey();
                    Expression expr = entry.getValue();

                    classNode.addMethod(createGetter(k, expr, rawEntries.keySet()));
                }

                sourceUnit.getAST().addClass(classNode);

                return new ConstructorCallExpression(classNode, ArgumentListExpression.EMPTY_ARGUMENTS);

            }
        }

        return exp;
    }

    private MethodNode createGetter(String name, Expression valueExpr, Set<String> keys) {
        String methodName = getterName(name);
        ClassNode returnType = ClassHelper.STRING_TYPE;
        BlockStatement body = new BlockStatement();

        body.addStatement(GeneralUtils.returnS(expressionTranslation(valueExpr, keys)));
        return new MethodNode(methodName,
                Opcodes.ACC_PUBLIC,
                returnType,
                Parameter.EMPTY_ARRAY,
                ClassNode.EMPTY_ARRAY,
                body);
    }

    private Expression expressionTranslation(Expression expr, Set<String> keys) {
        if (expr instanceof ConstantExpression) {
            // If it's a constant, just return it, with a String.valueOf call if needed.
            if (!(expr.getType().equals(ClassHelper.STRING_TYPE))) {
                return new StaticMethodCallExpression(ClassHelper.STRING_TYPE, "valueOf", expr);
            } else {
                return expr;
            }
        } else if (expr instanceof BinaryExpression &&
                ((BinaryExpression) expr).getOperation().getType() == Types.PLUS) {
            // If it's a valid BinaryExpression, return a new BinaryExpression composed of this called on its left
            // and right expressions.
            BinaryExpression binExpr = (BinaryExpression) expr;
            return new BinaryExpression(expressionTranslation(binExpr.getLeftExpression(), keys),
                    Token.newSymbol(Types.PLUS, -1, -1),
                    expressionTranslation(binExpr.getRightExpression(), keys));
        } else if (expr instanceof GStringExpression) {
            // If it's a GString, return a new GString after recursing on its values.
            GStringExpression gStringExpr = (GStringExpression) expr;
            List<Expression> transformedValues = new ArrayList<>();
            for (Expression valueExpr : gStringExpr.getValues()) {
                transformedValues.add(expressionTranslation(valueExpr, keys));
            }
            return new GStringExpression(gStringExpr.getText(), gStringExpr.getStrings(), transformedValues);
        } else if (expr instanceof PropertyExpression) {
            // If it's a PropertyExpression...
            PropertyExpression propExpr = (PropertyExpression) expr;
            if (propExpr.getObjectExpression() instanceof VariableExpression) {
                if (((VariableExpression) propExpr.getObjectExpression()).getName().equals("env") &&
                        keys.contains(propExpr.getPropertyAsString())) {
                    // If it's an "env.foo" where "foo" is one of the keys, return the appropriate getter call.
                    return getterCall(propExpr.getPropertyAsString());
                } else {
                    // If it's an object property, transform the object expression.
                    return new PropertyExpression(expressionTranslation(propExpr.getObjectExpression(), keys),
                            propExpr.getProperty());
                }
            } else {
                // If it's just an arbitrary property, well, return that.
                return propExpr;
            }
        } else if (expr instanceof MethodCallExpression) {
            // If it's a method call, iterate over its arguments.
            MethodCallExpression methExpr = (MethodCallExpression) expr;
            List<Expression> arguments = new ArrayList<>();
            for (Expression arg : ((TupleExpression)methExpr.getArguments()).getExpressions()) {
                arguments.add(expressionTranslation(arg, keys));
            }
            return new MethodCallExpression(methExpr.getObjectExpression(), methExpr.getMethod(),
                    new TupleExpression(arguments));
        } else if (expr instanceof VariableExpression) {
            VariableExpression varExpr = (VariableExpression) expr;
            if (keys.contains(varExpr.getName())) {
                // If it's a variable expression, replace it with the appropriate getter if it's a known key.
                return getterCall(varExpr.getName());
            } else {
                // If it's not a known key, fall back on getScript().getProperty(name)
                return new MethodCallExpression(GeneralUtils.callThisX("getScript"),
                        "getProperty",
                        new TupleExpression(new ConstantExpression(varExpr.getName())));
            }
        } else {
            // What kind of expression got past us here? Let's find out!
            throw new IllegalArgumentException("Got an unexpected " + expr.getClass() + ": '" +
                    Utils.getSourceTextForASTNode(expr, sourceUnit));
        }
    }

    private MethodCallExpression getterCall(String name) {
        return GeneralUtils.callThisX(getterName(name));
    }

    private String getterName(String name) {
        return "get" + MetaClassHelper.capitalize(name);
    }

    public static class EnvCounter {
        private int count = 0;

        public int getAndIncr() {
            return ++count;
        }
    }

    public static abstract class AbstractEnvironmentResolver {
        private CpsScript script;

        public void setScript(CpsScript script) {
            this.script = script;
        }

        public CpsScript getScript() {
            return script;
        }
    }
}
