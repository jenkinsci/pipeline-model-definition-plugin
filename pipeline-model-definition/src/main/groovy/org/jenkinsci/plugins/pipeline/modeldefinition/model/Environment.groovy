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
import org.codehaus.groovy.ast.ASTNode
import org.codehaus.groovy.ast.ClassHelper
import org.codehaus.groovy.ast.expr.ArgumentListExpression
import org.codehaus.groovy.ast.expr.BinaryExpression
import org.codehaus.groovy.ast.expr.ClosureExpression
import org.codehaus.groovy.ast.expr.ConstantExpression
import org.codehaus.groovy.ast.expr.ElvisOperatorExpression
import org.codehaus.groovy.ast.expr.Expression
import org.codehaus.groovy.ast.expr.GStringExpression
import org.codehaus.groovy.ast.expr.MapExpression
import org.codehaus.groovy.ast.expr.MethodCallExpression
import org.codehaus.groovy.ast.expr.PropertyExpression
import org.codehaus.groovy.ast.expr.VariableExpression
import org.codehaus.groovy.ast.stmt.ExpressionStatement
import org.codehaus.groovy.runtime.MetaClassHelper
import org.codehaus.groovy.syntax.Types
import org.jenkinsci.plugins.pipeline.modeldefinition.Utils
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTElement
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTEnvironment
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTInternalFunctionCall
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTValue
import org.jenkinsci.plugins.pipeline.modeldefinition.parser.ASTParserUtils
import org.jenkinsci.plugins.scriptsecurity.sandbox.whitelists.Whitelisted
import org.jenkinsci.plugins.workflow.cps.CpsScript

import javax.annotation.CheckForNull
import javax.annotation.Nonnull

import static org.codehaus.groovy.ast.tools.GeneralUtils.args
import static org.codehaus.groovy.ast.tools.GeneralUtils.callX
import static org.codehaus.groovy.ast.tools.GeneralUtils.constX

/**
 * Special wrapper for environment to deal with mapped closure problems with property declarations.
 *
 * @author Andrew Bayer
 */
@SuppressFBWarnings(value="SE_NO_SERIALVERSIONID")
public class Environment implements Serializable {

    private final EnvironmentResolver envResolver
    private final EnvironmentResolver credsResolver

    @Whitelisted
    Environment(EnvironmentResolver envResolver, EnvironmentResolver credsResolver) {
        this.envResolver = envResolver
        this.credsResolver = credsResolver
        this.credsResolver.setFallback(this.envResolver)
    }

    EnvironmentResolver getEnvResolver() {
        return envResolver
    }

    EnvironmentResolver getCredsResolver() {
        return credsResolver
    }

    static ASTNode transformToRuntimeAST(@CheckForNull ModelASTEnvironment original) {
        if (ASTParserUtils.isGroovyAST(original) && !original.variables.isEmpty()) {
            return ASTParserUtils.buildAst {
                constructorCall(Environment) {
                    argumentList {
                        expression.add(generateResolver(original, ModelASTValue.class))
                        expression.add(generateResolver(original, ModelASTInternalFunctionCall.class))
                    }
                }
            }
        }
        return constX(null)
    }

    /**
     * Create the AST for the {@link EnvironmentResolver} for this environment value type.
     *
     * @param original The parsed model of the environment
     * @param valueType Either {@link ModelASTInternalFunctionCall} for credentials or {@link ModelASTValue} for env.
     * @return The AST for instantiating the resolver.
     */
    private static ASTNode generateResolver(@Nonnull ModelASTEnvironment original, @Nonnull Class valueType) {
        Set<String> keys = new HashSet<>()

        // We need to keep track of the environment keys for use in
        keys.addAll(original.variables.findAll { k, v -> v instanceof ModelASTValue }.collect { k, v -> k.key })

        MapExpression closureMap = new MapExpression()

        original.variables.each { k, v ->
            // Filter for only the desired value type - ModelASTValue for env vars, ModelASTInternalFunctionCall for
            // credentials.
            if (v instanceof ModelASTElement && valueType.isInstance(v) && v.sourceLocation != null) {
                if (v.sourceLocation instanceof Expression) {
                    Expression toTransform = (Expression)v.sourceLocation

                    if (valueType == ModelASTInternalFunctionCall && toTransform instanceof MethodCallExpression) {
                        List<Expression> args = ASTParserUtils.methodCallArgs((MethodCallExpression)toTransform)
                        if (args.size() == 1) {
                            toTransform = args.get(0)
                        }
                    }
                    ClosureExpression expr = translateValue(toTransform, keys)
                    if (expr != null) {
                        closureMap.addMapEntryExpression(constX(k.key), expr)
                    } else {
                        throw new IllegalArgumentException("Empty closure for ${k.key}")
                    }
                }
            }
        }

        return callX(ClassHelper.make(EnvironmentResolver), "instanceFromMap",
            args(closureMap))
    }

    private static MethodCallExpression translateAndCall(Expression expr, Set<String> keys) {
        return callX(translateValue(expr, keys), constX("call"), new ArgumentListExpression())
    }

    @CheckForNull
    private static ClosureExpression translateValue(Expression expr, Set<String> keys) {
        ASTNode node = ASTParserUtils.buildAst {
            closure {
                parameters {}
                block {
                    returnStatement {
                        if (expr instanceof ConstantExpression) {
                            expression.add(expr)
                        } else if (expr instanceof BinaryExpression &&
                            ((BinaryExpression) expr).getOperation().getType() == Types.PLUS) {
                            BinaryExpression binExpr = (BinaryExpression) expr
                            binary {
                                expression.add(translateAndCall(binExpr.leftExpression, keys))
                                token "+"
                                expression.add(translateAndCall(binExpr.rightExpression, keys))
                            }
                        } else if (expr instanceof GStringExpression) {
                            GStringExpression gStrExpr = (GStringExpression) expr

                            gString(gStrExpr.text) {
                                strings {
                                    gStrExpr.strings.each { s ->
                                        expression.add(s)
                                    }
                                }
                                values {
                                    gStrExpr.values.each { v ->
                                        expression.add(translateAndCall(v, keys))
                                    }
                                }
                            }
                        } else if (expr instanceof PropertyExpression) {
                            PropertyExpression propExpr = (PropertyExpression) expr
                            if (propExpr.objectExpression instanceof VariableExpression) {
                                System.err.println("propExpr: ${propExpr}")
                                if (((VariableExpression)propExpr.objectExpression).name == "env" &&
                                    keys.contains(propExpr.propertyAsString)) {
                                    expression.add(getterCall(propExpr.propertyAsString))
                                } else {
                                    property {
                                        expression.add(translateAndCall(propExpr.objectExpression, keys))
                                        expression.add(propExpr.property)
                                    }
                                }
                            } else {
                                expression.add(propExpr)
                            }
                        } else if (expr instanceof MethodCallExpression) {
                            MethodCallExpression mce = (MethodCallExpression) expr
                            methodCall {
                                expression.add(translateAndCall(mce.objectExpression, keys))
                                expression.add(mce.method)
                                argumentList {
                                    mce.arguments.each { a ->
                                        expression.add(translateAndCall(a, keys))
                                    }
                                }
                            }
                        } else if (expr instanceof VariableExpression) {
                            VariableExpression ve = (VariableExpression)expr
                            if (keys.contains(ve.name)) {
                                expression.add(getterCall(ve.name))
                            } else if (ve.name == "this") {
                                expression.add(ve)
                            } else {
                                System.err.println("ve.name: ${ve.name}")
                                methodCall {
                                    variable("this")
                                    constant "getScriptPropOrParam"
                                    argumentList {
                                        constant ve.name
                                    }
                                }
                            }
                        } else if (expr instanceof ElvisOperatorExpression) {
                            ElvisOperatorExpression elvis = (ElvisOperatorExpression) expr
                            elvisOperator {
                                expression.add(translateAndCall(elvis.trueExpression, keys))
                                expression.add(translateAndCall(elvis.falseExpression, keys))
                            }
                        } else if (expr instanceof ClosureExpression) {
                            ClosureExpression cl = (ClosureExpression) expr
                            closure {
                                parameters {
                                    cl.parameters.each {
                                        expression.add(it)
                                    }
                                }
                                block {
                                    ASTParserUtils.eachStatement(cl.code) { s ->
                                        if (s instanceof ExpressionStatement) {
                                            ExpressionStatement stmt = (ExpressionStatement) s
                                            expression {
                                                expression.add(translateAndCall(stmt.expression, keys))
                                            }
                                        } else {
                                            // TODO: Message asking to report this so I can address it.

                                            throw new IllegalArgumentException("Got an unexpected" + s.getClass())
                                        }

                                    }
                                }
                            }
                        } else {
                            // TODO: Message asking to report this so I can address it.
                            throw new IllegalArgumentException("Got an unexpected " + expr.getClass())
                        }
                    }
                }
            }
        }

        return node instanceof ClosureExpression ? (ClosureExpression) node : null
    }

    private static MethodCallExpression getterCall(String name) {
        return (MethodCallExpression)ASTParserUtils.buildAst {
            methodCall {
                methodCall {
                    variable("this")
                    constant "getClosure"
                    argumentList {
                        constant name
                    }
                }
                constant "call"
                argumentList{}
            }
        }
    }

    static String getterName(String name) {
        return "get" + MetaClassHelper.capitalize(name)
    }

    static class EnvCounter {
        private int count = 0

        int getAndIncr() {
            return ++count
        }
    }

    static class EnvironmentResolver implements Serializable {
        private static final long serialVersionUID = 1L

        private CpsScript script
        private Map<String,Closure> closureMap = new TreeMap<>()
        private EnvironmentResolver fallback

        @Whitelisted
        EnvironmentResolver() {
        }

        @Whitelisted
        void setScript(CpsScript script) {
            this.script = script
        }

        @Whitelisted
        CpsScript getScript() {
            return script
        }

        void setFallback(EnvironmentResolver fallback) {
            this.fallback = fallback
        }

        void addClosure(String key, Closure closure) {
            this.closureMap.put(key, closure)
        }

        @Whitelisted
        Closure getClosure(String key) {
            if (closureMap.containsKey(key)) {
                return closureMap.get(key)
            } else if (fallback != null) {
                return fallback.getClosure(key)
            } else {
                return null
            }
        }

        @Whitelisted
        Object getScriptPropOrParam(String name) {
            return Utils.getScriptPropOrParam(script, name)
        }

        Object callClosure(String key) {
            return getClosure(key).call()
        }

        Map<String,Closure> getClosureMap() {
            return closureMap
        }

        @Whitelisted
        static EnvironmentResolver instanceFromMap(Map<String, Closure> closureMap) {
            EnvironmentResolver resolver = new EnvironmentResolver()
            closureMap.each { k, v ->
                v.delegate = resolver
                resolver.addClosure(k, v)
            }

            return resolver
        }
    }

}
