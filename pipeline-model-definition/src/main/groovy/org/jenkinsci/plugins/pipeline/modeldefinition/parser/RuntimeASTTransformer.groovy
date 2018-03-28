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
import hudson.model.JobProperty
import hudson.model.ParameterDefinition
import hudson.model.Run
import hudson.triggers.Trigger
import org.codehaus.groovy.ast.ClassHelper
import org.codehaus.groovy.ast.DynamicVariable
import org.codehaus.groovy.ast.expr.*
import org.codehaus.groovy.ast.stmt.BlockStatement
import org.codehaus.groovy.ast.stmt.CatchStatement
import org.codehaus.groovy.ast.stmt.ExpressionStatement
import org.codehaus.groovy.ast.stmt.ForStatement
import org.codehaus.groovy.ast.stmt.IfStatement
import org.codehaus.groovy.ast.stmt.ReturnStatement
import org.codehaus.groovy.ast.stmt.Statement
import org.codehaus.groovy.ast.stmt.TryCatchStatement
import org.codehaus.groovy.ast.stmt.WhileStatement
import org.codehaus.groovy.syntax.Types
import org.jenkinsci.plugins.pipeline.modeldefinition.Utils
import org.jenkinsci.plugins.pipeline.modeldefinition.actions.ExecutionModelAction
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.*
import org.jenkinsci.plugins.pipeline.modeldefinition.model.*
import org.jenkinsci.plugins.pipeline.modeldefinition.options.DeclarativeOption
import org.jenkinsci.plugins.pipeline.modeldefinition.when.DeclarativeStageConditional
import org.jenkinsci.plugins.pipeline.modeldefinition.when.DeclarativeStageConditionalDescriptor
import org.jenkinsci.plugins.structs.SymbolLookup
import org.jenkinsci.plugins.workflow.steps.StepDescriptor

import javax.annotation.CheckForNull
import javax.annotation.Nonnull

import static org.codehaus.groovy.ast.tools.GeneralUtils.*
import static org.jenkinsci.plugins.pipeline.modeldefinition.parser.ASTParserUtils.*

/**
 * Transforms a given {@link ModelASTPipelineDef} into the {@link Root} used for actual runtime. Also attaches a
 * {@link ExecutionModelAction} to the run.
 */
@SuppressFBWarnings(value="SE_NO_SERIALVERSIONID")
class RuntimeASTTransformer {
    RuntimeASTTransformer() {
    }

    /**
     * Given a run, transform a {@link ModelASTPipelineDef}, attach the {@link ModelASTStages} for that {@link ModelASTPipelineDef} to the
     * run, and return an {@link ArgumentListExpression} containing a closure that returns the {@Root} we just created.
     */
    ArgumentListExpression transform(@Nonnull ModelASTPipelineDef pipelineDef, @CheckForNull Run<?,?> run) {
        Expression root = transformRoot(pipelineDef)
        if (run != null) {
            ModelASTStages stages = pipelineDef.stages
            stages.removeSourceLocation()
            ExecutionModelAction action = run.getAction(ExecutionModelAction.class)
            if (action == null) {
                run.addAction(new ExecutionModelAction(stages))
            } else {
                action.addStages(stages)
                run.save()
            }
        }

        return args(
            closureX(
                block(
                    returnS(root)
                )
            )
        )
    }

    /**
     * Generate the AST (to be CPS-transformed) for instantiating a {@link AbstractBuildConditionResponder}.
     *
     * @param original The parsed AST model.
     * @param container The class of the container we're instantiating.
     * @return The AST for a constructor call for this container class, or the constant null expression if the original
     * cannot be transformed.
     */
    Expression transformBuildConditionsContainer(@CheckForNull ModelASTBuildConditionsContainer original,
                                              @Nonnull Class container) {
        if (isGroovyAST(original) && !original.conditions.isEmpty()) {
            MapExpression nameToSteps = new MapExpression()
            original.conditions.each { cond ->
                Expression steps = transformStepsFromBuildCondition(cond)
                if (steps != null) {
                    nameToSteps.addMapEntryExpression(constX(cond.condition), steps)
                }
            }
            return ctorX(ClassHelper.make(container), args(nameToSteps))
        }
        return constX(null)
    }

    /**
     * Generates the AST (to be CPS-transformed) for instantiating an {@link Agent}.
     *
     * @param original The parsed AST model
     * @return The AST for a constructor call for {@link Agent}, or the constant null expression if the original
     * cannot be transformed.
     */
    Expression transformAgent(@CheckForNull ModelASTAgent original) {
        if (isGroovyAST(original) && original.agentType != null) {
            ArgumentListExpression argList = new ArgumentListExpression()
            if (original.variables == null ||
                (original.variables instanceof ModelASTClosureMap &&
                    ((ModelASTClosureMap)original.variables).variables.isEmpty())) {
                // Zero-arg agent type
                MapExpression zeroArg = new MapExpression()
                zeroArg.addMapEntryExpression(constX(original.agentType.key), constX(true))
                argList.addExpression(zeroArg)
            } else {
                BlockStatementMatch match =
                    matchBlockStatement((Statement) original.sourceLocation)
                if (match != null) {
                    argList.addExpression(recurseAndTransformMappedClosure(match.body))
                } else {
                    throw new IllegalArgumentException("Expected a BlockStatement for agent but got an instance of ${original.sourceLocation.class}")
                }
            }
            return ctorX(ClassHelper.make(Agent.class), argList)
        }

        return constX(null)
    }

    /**
     * Generates the AST (to be CPS-transformed) for instantiating an {@link Environment}.
     *
     * @param original The parsed AST model
     * @return The AST for a constructor call for {@link Environment}, or the constant null expression if the original
     * cannot be transformed.
     */
    Expression transformEnvironment(@CheckForNull ModelASTEnvironment original) {
        if (isGroovyAST(original) && !original.variables.isEmpty()) {
            return ctorX(ClassHelper.make(Environment.class),
                args(
                    generateEnvironmentResolver(original, ModelASTValue.class),
                    generateEnvironmentResolver(original, ModelASTInternalFunctionCall.class)
                ))
        }
        return constX(null)
    }

    /**
     * Create the AST for the {@link Environment.EnvironmentResolver} for this environment value type.
     *
     * @param original The parsed model of the environment
     * @param valueType Either {@link ModelASTInternalFunctionCall} for credentials or {@link ModelASTValue} for env.
     * @return The AST for instantiating the resolver.
     */
    private Expression generateEnvironmentResolver(@Nonnull ModelASTEnvironment original, @Nonnull Class valueType) {
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
                        List<Expression> args = methodCallArgs((MethodCallExpression)toTransform)
                        if (args.size() == 1) {
                            toTransform = args.get(0)
                        }
                    }
                    Expression expr = translateEnvironmentValue(k.key, toTransform, keys)
                    if (expr != null) {
                        if (expr instanceof ClosureExpression) {
                            closureMap.addMapEntryExpression(constX(k.key), expr)
                        } else {
                            closureMap.addMapEntryExpression(constX(k.key), closureX(block(returnS(expr))))
                        }
                    } else {
                        throw new IllegalArgumentException("Empty closure for ${k.key}")
                    }
                }
            }
        }

        return callX(ClassHelper.make(Environment.EnvironmentResolver.class), "instanceFromMap",
            args(closureMap))
    }

    /**
     * Calls {@link #translateEnvironmentValue} and calls the result. This won't be evaluated until the parent closure
     * itself is evaluated, as this is only called for children. May be null if the translation is null.
     */
    @CheckForNull
    private Expression translateEnvironmentValueAndCall(String targetVar, Expression expr, Set<String> keys) {
        Expression translated = translateEnvironmentValue(targetVar, expr, keys)
        if (translated != null) {
            if (translated instanceof ClosureExpression) {
                return callX(translated, "call")
            } else {
                return translated
            }
        } else {
            return null
        }
    }

    /**
     * Recursively translate any nested expressions within the given expression, setting any attempt to reference an
     * environment variable we've defined to instead lazily call the closure defined in the resolver for that value.
     */
    @CheckForNull
    private Expression translateEnvironmentValue(String targetVar, Expression expr, Set<String> keys) {
        Expression body = null
        if (expr instanceof ConstantExpression) {
            // If the expression is a constant, like 1, "foo", etc, just use that.
            return expr
        } else if (expr instanceof ClassExpression) {
            // If the expression is a class, just use that.
            return expr
        } else if (expr instanceof EmptyExpression) {
            // If it's an empty expression, just use that
            return expr
        } else if (expr instanceof BinaryExpression &&
            ((BinaryExpression) expr).getOperation().getType() == Types.PLUS) {
            // If the expression is a binary expression of plusses, translate its components.
            BinaryExpression binExpr = (BinaryExpression) expr
            return plusX(
                translateEnvironmentValueAndCall(targetVar, binExpr.leftExpression, keys),
                translateEnvironmentValueAndCall(targetVar, binExpr.rightExpression, keys)
            )
        } else if (expr instanceof GStringExpression) {
            // If the expression is a GString, translate its values.
            GStringExpression gStrExpr = (GStringExpression) expr
            return new GStringExpression(gStrExpr.text,
                gStrExpr.strings,
                gStrExpr.values.collect { translateEnvironmentValueAndCall(targetVar, it, keys) }
            )
        } else if (expr instanceof PropertyExpression) {
            PropertyExpression propExpr = (PropertyExpression) expr
            if (propExpr.objectExpression instanceof VariableExpression &&
                ((VariableExpression) propExpr.objectExpression).name == "env" &&
                    keys.contains(propExpr.propertyAsString)) {
                if (propExpr.propertyAsString == targetVar) {
                    // If this is the same variable we're setting, use getScriptPropOrParam, which will first try
                    // script.getProperty(name), then script.getProperty('params').get(name).
                    body = callX(
                        varX("this"),
                        constX("getScriptPropOrParam"),
                        args(constX(propExpr.propertyAsString))
                    )

                } else {
                    // If the property this expression refers to is env.whatever, replace with the env getter.
                    body = environmentValueGetterCall(propExpr.propertyAsString)
                }
            } else {
                // Otherwise, if the property is still on a variable, translate everything
                return propX(
                    translateEnvironmentValueAndCall(targetVar, propExpr.objectExpression, keys),
                    translateEnvironmentValueAndCall(targetVar, propExpr.property, keys)
                )
            }
        } else if (expr instanceof MethodCallExpression) {
            // If the expression is a method call, translate its arguments.
            MethodCallExpression mce = (MethodCallExpression) expr
            return callX(
                translateEnvironmentValueAndCall(targetVar, mce.objectExpression, keys),
                mce.method,
                args(mce.arguments.collect { a ->
                    translateEnvironmentValueAndCall(targetVar, a, keys)
                })
            )
        } else if (expr instanceof VariableExpression) {
            VariableExpression ve = (VariableExpression) expr
            if (keys.contains(ve.name) && ve.name != targetVar) {
                // If the variable name is one we know is an environment variable, use the env getter, unless the reference
                // is to the same variable we're setting!
                body = environmentValueGetterCall(ve.name)
            } else if (ve.name == "this" || !(ve.accessedVariable instanceof DynamicVariable)) {
                // If the variable is this, or if this is a real variable, not a dynamic variable, just use it.
                return ve
            } else {
                // Otherwise, fall back to getScriptPropOrParam, which will first try script.getProperty(name), then
                // script.getProperty('params').get(name).
                body = callX(
                    varX("this"),
                    constX("getScriptPropOrParam"),
                    args(constX(ve.name))
                )
            }
        } else if (expr instanceof ElvisOperatorExpression) {
            // If the expression is ?:, translate its components.
            ElvisOperatorExpression elvis = (ElvisOperatorExpression) expr
            return new ElvisOperatorExpression(
                translateEnvironmentValueAndCall(targetVar, elvis.trueExpression, keys),
                translateEnvironmentValueAndCall(targetVar, elvis.falseExpression, keys)
            )
        } else if (expr instanceof ClosureExpression) {
            // If the expression is a closure, translate its statements.
            ClosureExpression cl = (ClosureExpression) expr
            BlockStatement closureBlock = block()
            eachStatement(cl.code) { s ->
                closureBlock.addStatement(translateClosureStatement(targetVar, s, keys))
            }
            return closureX(
                cl.parameters,
                closureBlock
            )
        } else if (expr instanceof ArrayExpression) {
            // If the expression is an array, transform its contents.
            ArrayExpression a = (ArrayExpression) expr
            List<Expression> sizes = a.sizeExpression?.collect {
                translateEnvironmentValueAndCall(targetVar, it, keys)
            }
            List<Expression> expressions = a.expressions?.collect {
                translateEnvironmentValueAndCall(targetVar, it, keys)
            }

            return new ArrayExpression(a.elementType, expressions, sizes)
        } else if (expr instanceof ListExpression) {
            // If the expression is a list, transform its contents
            ListExpression l = (ListExpression) expr
            List<Expression> expressions = l.expressions?.collect {
                translateEnvironmentValueAndCall(targetVar, it, keys)
            }

            return new ListExpression(expressions)
        } else if (expr instanceof MapExpression) {
            // If the expression is a map, translate its entries.
            MapExpression m = (MapExpression) expr
            List<MapEntryExpression> entries = m.mapEntryExpressions?.collect {
                (MapEntryExpression)translateEnvironmentValueAndCall(targetVar, it, keys)
            }

            return new MapExpression(entries)
        } else if (expr instanceof MapEntryExpression) {
            // If the expression is a map entry, translate its key and value
            MapEntryExpression m = (MapEntryExpression) expr

            return new MapEntryExpression(translateEnvironmentValueAndCall(targetVar, m.keyExpression, keys),
                translateEnvironmentValueAndCall(targetVar, m.valueExpression, keys))
        } else if (expr instanceof BitwiseNegationExpression) {
            // Translate the nested expression - note, no test coverage due to bitwiseNegate not being whitelisted
            return new BitwiseNegationExpression(translateEnvironmentValueAndCall(targetVar, expr.expression, keys))
        } else if (expr instanceof BooleanExpression) {
            // Translate the nested expression
            return new BooleanExpression(translateEnvironmentValueAndCall(targetVar, expr.expression, keys))
        } else if (expr instanceof CastExpression) {
            // Translate the nested expression
            Expression transformed = translateEnvironmentValueAndCall(targetVar, expr.expression, keys)
            def cast = new CastExpression(expr.type, transformed, expr.ignoringAutoboxing)
            return cast
        } else if (expr instanceof ConstructorCallExpression) {
            // Translate the arguments
            return ctorX(expr.type, translateEnvironmentValueAndCall(targetVar, expr.arguments, keys))
        } else if (expr instanceof MethodPointerExpression) {
            // Translate the nested expression and method
            return new MethodPointerExpression(translateEnvironmentValueAndCall(targetVar, expr.expression, keys),
                translateEnvironmentValueAndCall(targetVar, expr.methodName, keys))
        } else if (expr instanceof PostfixExpression) {
            // Translate the nested expression
            return new PostfixExpression(translateEnvironmentValueAndCall(targetVar, expr.expression, keys), expr.operation)
        } else if (expr instanceof PrefixExpression) {
            // Translate the nested expression
            return new PrefixExpression(expr.operation, translateEnvironmentValueAndCall(targetVar, expr.expression, keys))
        } else if (expr instanceof RangeExpression) {
            // Translate the from and to
            return new RangeExpression(translateEnvironmentValueAndCall(targetVar, expr.from, keys),
                translateEnvironmentValueAndCall(targetVar, expr.to, keys),
                expr.inclusive)
        } else if (expr instanceof TernaryExpression) {
            // Translate the true, false and boolean expressions
            TernaryExpression t = (TernaryExpression) expr
            return ternaryX(translateEnvironmentValueAndCall(targetVar, t.booleanExpression, keys),
                translateEnvironmentValueAndCall(targetVar, t.trueExpression, keys),
                translateEnvironmentValueAndCall(targetVar, t.falseExpression, keys))
        } else if (expr instanceof ArgumentListExpression) {
            // Translate the contents
            List<Expression> expressions = expr.expressions.collect {
                translateEnvironmentValueAndCall(targetVar, it, keys)
            }
            return args(expressions)
        } else if (expr instanceof TupleExpression) {
            // Translate the contents
            List<Expression> expressions = expr.expressions.collect {
                translateEnvironmentValueAndCall(targetVar, it, keys)
            }
            return new TupleExpression(expressions)
        } else if (expr instanceof UnaryMinusExpression) {
            // Translate the nested expression - unary ops are also not whitelisted and so aren't tested
            return new UnaryMinusExpression(translateEnvironmentValueAndCall(targetVar, expr.expression, keys))
        } else if (expr instanceof UnaryPlusExpression) {
            // Translate the nested expression
            return new UnaryPlusExpression(translateEnvironmentValueAndCall(targetVar, expr.expression, keys))
        } else if (expr instanceof BinaryExpression) {
            // Translate the component expressions
            return new BinaryExpression(translateEnvironmentValueAndCall(targetVar, expr.leftExpression, keys),
                expr.operation,
                translateEnvironmentValueAndCall(targetVar, expr.rightExpression, keys))
        } else {
            throw new IllegalArgumentException("Got an unexpected " + expr.getClass() + " in environment, please report " +
                "at issues.jenkins-ci.org")
        }

        if (body != null) {
            return closureX(
                block(
                    returnS(
                        body
                    )
                )
            )
        }

        return null
    }

    private Statement translateClosureStatement(String targetVar, Statement s, Set<String> keys) {
        if (s == null) {
            return null
        } else if (s instanceof ExpressionStatement) {
            // Translate the nested expression
            return stmt(translateEnvironmentValueAndCall(targetVar, s.expression, keys))
        } else if (s instanceof ReturnStatement) {
            // Translate the nested expression
            return stmt(translateEnvironmentValueAndCall(targetVar, s.expression, keys))
        } else if (s instanceof IfStatement) {
            // Translate the boolean expression, the if block and the else block
            return ifElseS(translateEnvironmentValueAndCall(targetVar, s.booleanExpression, keys),
                translateClosureStatement(targetVar, s.ifBlock, keys),
                translateClosureStatement(targetVar, s.elseBlock, keys))
        } else if (s instanceof ForStatement) {
            // Translate the collection and loop block
            return new ForStatement(s.variable,
                translateEnvironmentValueAndCall(targetVar, s.collectionExpression, keys),
                translateClosureStatement(targetVar, s.loopBlock, keys))
        } else if (s instanceof WhileStatement) {
            // Translate the boolean expression's contents and the loop block
            BooleanExpression newBool = new BooleanExpression(translateEnvironmentValueAndCall(targetVar,
                s.booleanExpression?.expression, keys))
            return new WhileStatement(newBool, translateClosureStatement(targetVar, s.loopBlock, keys))
        } else if (s instanceof TryCatchStatement) {
            // Translate the try and finally statements, as well as any catch statements
            TryCatchStatement t = (TryCatchStatement) s
            TryCatchStatement newTry = new TryCatchStatement(translateClosureStatement(targetVar, t.tryStatement, keys),
                translateClosureStatement(targetVar, t.finallyStatement, keys))
            t.catchStatements.each { c ->
                newTry.addCatch((CatchStatement)translateClosureStatement(targetVar, c, keys))
            }
            return newTry
        } else if (s instanceof CatchStatement) {
            // Translate the nested statement
            return catchS(s.variable, translateClosureStatement(targetVar, s.code, keys))
        } else {
            throw new IllegalArgumentException("Got an unexpected " + s.getClass() + " in environment, please " +
                "report at issues.jenkins-ci.org")
        }
    }

    /**
     * Generates the method call for fetching the closure for a given environment key and calling it.
     */
    private MethodCallExpression environmentValueGetterCall(String name) {
        return callX(callThisX("getClosure", constX(name)), "call")
    }

    /**
     * Generates the AST (to be CPS-transformed) for instantiating {@link Libraries}.
     *
     * @param original The parsed AST model
     * @return The AST for a constructor call for {@link Libraries}, or the constant null expression if the original
     * cannot be transformed.
     */
    Expression transformLibraries(@CheckForNull ModelASTLibraries original) {
        if (isGroovyAST(original) && !original.libs.isEmpty()) {
            ListExpression listArg = new ListExpression()
            original.libs.each { l ->
                if (l.sourceLocation instanceof Expression) {
                    listArg.addExpression((Expression)l.sourceLocation)
                }
            }
            return ctorX(ClassHelper.make(Libraries.class), args(listArg))
        }

        return constX(null)
    }

    /**
     * Generates the AST (to be CPS-transformed) for instantiating {@link Options}.
     *
     * @param original The parsed AST model
     * @return The AST for a constructor call for {@link Options}, or the constant null expression if the original
     * cannot be transformed.
     */
    Expression transformOptions(@CheckForNull ModelASTOptions original) {
        if (isGroovyAST(original) && !original.options.isEmpty()) {
            List<ModelASTOption> jobProps = new ArrayList<>()
            List<ModelASTOption> options = new ArrayList<>()
            List<ModelASTOption> wrappers = new ArrayList<>()

            SymbolLookup symbolLookup = SymbolLookup.get()

            original.options.each { o ->
                if (!original.inStage && symbolLookup.findDescriptor(JobProperty.class, o.name) != null) {
                    jobProps.add(o)
                } else if (symbolLookup.findDescriptor(DeclarativeOption.class, o.name) != null) {
                    options.add(o)
                } else if (StepDescriptor.byFunctionName(o.name) != null) {
                    wrappers.add(o)
                }
            }
            MapExpression optsMap = new MapExpression()
            MapExpression wrappersMap = new MapExpression()

            options.each { o ->
                if (o.getSourceLocation() instanceof Statement) {
                    MethodCallExpression expr = matchMethodCall((Statement) o.getSourceLocation())
                    if (expr != null) {
                        optsMap.addMapEntryExpression(constX(o.name), methodCallToDescribable(expr, DeclarativeOption.class))
                    }
                }
            }
            wrappers.each { w ->
                if (w.getSourceLocation() instanceof Statement) {
                    MethodCallExpression expr = matchMethodCall((Statement) w.getSourceLocation())
                    if (expr != null) {
                        List<Expression> methArgs = methodCallArgs(expr)
                        if (methArgs.size() == 1) {
                            wrappersMap.addMapEntryExpression(constX(w.name), methArgs.get(0))
                        } else if (methArgs.size() > 1) {
                            ListExpression argList = new ListExpression(methArgs)
                            wrappersMap.addMapEntryExpression(constX(w.name), argList)
                        } else {
                            wrappersMap.addMapEntryExpression(constX(w.name), constX(null))
                        }
                    }
                }
            }

            if (original.inStage) {
                return ctorX(ClassHelper.make(StageOptions.class), args(optsMap, wrappersMap))
            } else {
                return ctorX(ClassHelper.make(Options.class),
                    args(transformListOfDescribables(jobProps, JobProperty.class), optsMap, wrappersMap))
            }
        }

        return constX(null)
    }

    /**
     * Generates the AST (to be CPS-transformed) for instantiating {@link Parameters}.
     *
     * @param original The parsed AST model
     * @return The AST for a constructor call for {@link Parameters}, or the constant null expression if the original
     * cannot be transformed.
     */
    Expression transformParameters(@CheckForNull ModelASTBuildParameters original) {
        return transformDescribableContainer(original, original?.parameters, Parameters.class, ParameterDefinition.class)
    }

    /**
     * Generates the AST (to be CPS-transformed) for instantiating {@link PostBuild}.
     *
     * @param original The parsed AST model
     * @return The AST for a constructor call for {@link PostBuild}, or the constant null expression if the original
     * cannot be transformed.
     */
    Expression transformPostBuild(@CheckForNull ModelASTPostBuild original) {
        return transformBuildConditionsContainer(original, PostBuild.class)
    }

    /**
     * Generates the AST (to be CPS-transformed) for instantiating {@link PostStage}.
     *
     * @param original The parsed AST model
     * @return The AST for a constructor call for {@link PostStage}, or the constant null expression if the original
     * cannot be transformed.
     */
    Expression transformPostStage(@CheckForNull ModelASTPostStage original) {
        return transformBuildConditionsContainer(original, PostStage.class)
    }

    /**
     * Generates the AST (to be CPS-transformed) for instantiating {@link Root}.
     *
     * @param original The parsed AST model
     * @return The AST for a constructor call for {@link Root}, or the constant null expression if the original
     * cannot be transformed.
     */
    Expression transformRoot(@CheckForNull ModelASTPipelineDef original) {
        if (isGroovyAST(original)) {
            return ctorX(ClassHelper.make(Root.class),
                args(transformAgent(original.agent),
                    transformStages(original.stages),
                    transformPostBuild(original.postBuild),
                    transformEnvironment(original.environment),
                    transformTools(original.tools),
                    transformOptions(original.options),
                    transformTriggers(original.triggers),
                    transformParameters(original.parameters),
                    transformLibraries(original.libraries),
                    constX(original.stages.getUuid().toString())))
        }

        return constX(null)
    }

    /**
     * Generates the AST (to be CPS-transformed) for instantiating {@link Stage}.
     *
     * @param original The parsed AST model
     * @return The AST for a constructor call for {@link Stage}, or the constant null expression if the original
     * cannot be transformed.
     */
    Expression transformStage(@CheckForNull ModelASTStage original) {
        if (isGroovyAST(original)) {
            return ctorX(ClassHelper.make(Stage.class),
                args(constX(original.name),
                    transformStepsFromStage(original),
                    transformAgent(original.agent),
                    transformPostStage(original.post),
                    transformStageConditionals(original.when),
                    transformTools(original.tools),
                    transformEnvironment(original.environment),
                    constX(original.failFast != null ? original.failFast : false),
                    transformOptions(original.options),
                    transformStageInput(original.input, original.name),
                    transformParallelContent(original),
                    transformStages(original.stages)))
        }

        return constX(null)
    }

    Expression transformStageInput(@CheckForNull ModelASTStageInput original, String stageName) {
        if (isGroovyAST(original)) {
            Expression paramsExpr = constX(null)
            if (!original.parameters.isEmpty()) {
                paramsExpr = transformListOfDescribables(original.parameters, ParameterDefinition.class)
            }
            return ctorX(ClassHelper.make(StageInput.class),
                args(valueOrNull(original.message),
                    valueOrNull(original.id, stageName),
                    valueOrNull(original.ok),
                    valueOrNull(original.submitter),
                    valueOrNull(original.submitterParameter),
                    paramsExpr))
        }
        return constX(null)
    }

    private Expression valueOrNull(@CheckForNull ModelASTValue value, Object defaultValue = null) {
        if (value?.sourceLocation instanceof Expression) {
            return (Expression)value.sourceLocation
        } else {
            return constX(defaultValue)
        }
    }

    /**
     * Instantiates a stage's when variable. Note that this does not instantiate the when conditions inside, it just
     * creates a closure that will return them when needed. This is to ensure lazy evaluation of variables.
     *
     * @param original
     * @return
     */
    Expression transformStageConditionals(@CheckForNull ModelASTWhen original) {
        if (isGroovyAST(original) && !original.getConditions().isEmpty()) {
            ListExpression closList = new ListExpression()
            original.getConditions().each { cond ->
                if (cond.name != null) {
                    DeclarativeStageConditionalDescriptor desc =
                        (DeclarativeStageConditionalDescriptor) SymbolLookup.get().findDescriptor(
                            DeclarativeStageConditional.class, cond.name)
                    if (desc != null) {
                        closList.addExpression(desc.transformToRuntimeAST(cond))
                    }
                }
            }

            return ctorX(ClassHelper.make(StageConditionals.class),
                args(closureX(block(returnS(closList))),
                    constX(original.beforeAgent != null ? original.beforeAgent : false)))
        }
        return constX(null)
    }

    /**
     * Generates the AST (to be CPS-transformed) for instantiating {@link Stages}.
     *
     * @param original The parsed AST model
     * @return The AST for a constructor call for {@link Stages}, or the constant null expression if the original
     * cannot be transformed.
     */
    Expression transformStages(@CheckForNull ModelASTStages original) {
        if (isGroovyAST(original) && !original.stages.isEmpty()) {
            ListExpression argList = new ListExpression()
            original.stages.each { s ->
                argList.addExpression(transformStage(s))
            }

            return ctorX(ClassHelper.make(Stages.class), args(argList))
        }

        return constX(null)
    }

    /**
     * Generates the AST (to be CPS-transformed) for instantiating a list of {@link Stage}s.
     *
     * @param original The parsed AST model of a stage
     * @return The AST for a list of {@link Stage}s, or the constant null expression if the original
     * cannot be transformed.
     */
    Expression transformParallelContent(@CheckForNull ModelASTStage original) {
        if (isGroovyAST(original) && original?.parallelContent) {
            ListExpression argList = new ListExpression()
            original.parallelContent.each { c ->
                if (c instanceof ModelASTStage) {
                    argList.addExpression(transformStage(c))
                } else {
                    argList.addExpression(constX(null))
                }
            }
            return argList
        }

        return constX(null)
    }

    /**
     * Generates the AST (to be CPS-transformed) for instantiating {@link StepsBlock} for a {@link Stage}.
     *
     * @param original The parsed AST model
     * @return A method call of {@link Utils#createStepsBlock(Closure)}, or a constant null expression if the original
     * is null.
     */
    Expression transformStepsFromStage(@CheckForNull ModelASTStage original) {
        if (isGroovyAST(original)) {
            BlockStatementMatch stageMatch = matchBlockStatement((Statement)original.sourceLocation)
            if (stageMatch != null) {
                Statement stepsMethod = asBlock(stageMatch.body.code).statements.find { s ->
                    matchMethodCall(s)?.methodAsString == "steps"
                }
                if (stepsMethod != null) {
                    BlockStatementMatch stepsMatch = matchBlockStatement(stepsMethod)
                    if (stepsMatch != null) {
                        ClosureExpression transformedBody = StepRuntimeTransformerContributor.transformStage(original, stepsMatch.body)
                        return callX(ClassHelper.make(Utils.class), "createStepsBlock",
                            args(transformedBody))
                    }
                }
            }
        }

        return constX(null)
    }

    /**
     * Generates the AST (to be CPS-transformed) for instantiating {@link StepsBlock} for a post condition.
     *
     * @param original The parsed AST model
     * @return A method call of {@link Utils#createStepsBlock(Closure)}, or a constant null expression if the original
     * is null.
     */
    Expression transformStepsFromBuildCondition(@CheckForNull ModelASTBuildCondition original) {
        if (isGroovyAST(original)) {
            BlockStatementMatch condMatch = matchBlockStatement((Statement) original.sourceLocation)
            ClosureExpression transformedBody = StepRuntimeTransformerContributor.transformBuildCondition(original, condMatch.body)
            return callX(ClassHelper.make(Utils.class), "createStepsBlock",
                args(transformedBody))
        }
        return constX(null)
    }

    /**
     * Generates the AST (to be CPS-transformed) for instantiating {@link Tools}.
     *
     * @param original The parsed AST model
     * @return The AST for a constructor call for {@link Tools}, or the constant null expression if the original
     * cannot be transformed.
     */
    Expression transformTools(@CheckForNull ModelASTTools original) {
        if (isGroovyAST(original) && !original.tools?.isEmpty()) {
            MapExpression toolsMap = new MapExpression()
            original.tools.each { k, v ->
                if (v.sourceLocation != null && v.sourceLocation instanceof Expression) {
                    if (v.sourceLocation instanceof ClosureExpression) {
                        toolsMap.addMapEntryExpression(constX(k.key), (ClosureExpression) v.sourceLocation)
                    } else {
                        toolsMap.addMapEntryExpression(constX(k.key), closureX(block(returnS((Expression) v.sourceLocation))))
                    }
                }
            }
            return ctorX(ClassHelper.make(Tools.class), args(toolsMap))
        }
        return constX(null)
    }

    /**
     * Generates the AST (to be CPS-transformed) for instantiating {@link Triggers}.
     *
     * @param original The parsed AST model
     * @return The AST for a constructor call for {@link Triggers}, or the constant null expression if the original
     * cannot be transformed.
     */
    Expression transformTriggers(@CheckForNull ModelASTTriggers original) {
        return transformDescribableContainer(original, original?.triggers, Triggers.class, Trigger.class)
    }

}
