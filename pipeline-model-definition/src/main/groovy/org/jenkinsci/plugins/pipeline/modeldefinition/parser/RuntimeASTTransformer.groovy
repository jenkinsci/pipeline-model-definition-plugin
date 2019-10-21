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
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.DynamicVariable
import org.codehaus.groovy.ast.MethodNode
import org.codehaus.groovy.ast.ModuleNode
import org.codehaus.groovy.ast.Parameter
import org.codehaus.groovy.ast.expr.*
import org.codehaus.groovy.ast.stmt.*
import org.codehaus.groovy.control.SourceUnit
import org.codehaus.groovy.syntax.Types
import org.jenkinsci.plugins.pipeline.modeldefinition.RuntimeContainerBase
import org.jenkinsci.plugins.pipeline.modeldefinition.Utils
import org.jenkinsci.plugins.pipeline.modeldefinition.actions.ExecutionModelAction
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.*
import org.jenkinsci.plugins.pipeline.modeldefinition.model.*
import org.jenkinsci.plugins.pipeline.modeldefinition.options.DeclarativeOption
import org.jenkinsci.plugins.pipeline.modeldefinition.when.DeclarativeStageConditional
import org.jenkinsci.plugins.pipeline.modeldefinition.when.DeclarativeStageConditionalDescriptor
import org.jenkinsci.plugins.structs.SymbolLookup
import org.jenkinsci.plugins.workflow.cps.CpsScript
import org.jenkinsci.plugins.workflow.steps.StepDescriptor

import javax.annotation.CheckForNull
import javax.annotation.Nonnull

import static org.codehaus.groovy.ast.tools.GeneralUtils.*
import static org.jenkinsci.plugins.pipeline.modeldefinition.parser.ASTParserUtils.*
import static org.objectweb.asm.Opcodes.ACC_PUBLIC

/**
 * Transforms a given {@link ModelASTPipelineDef} into the {@link Root} used for actual runtime. Also attaches a
 * {@link ExecutionModelAction} to the run.
 */
@SuppressFBWarnings(value="SE_NO_SERIALVERSIONID")
class RuntimeASTTransformer {
    Wrapper wrapper

    RuntimeASTTransformer(SourceUnit sourceUnit) {
        wrapper = new Wrapper(sourceUnit)
    }

    /**
     * Given a run, transform a {@link ModelASTPipelineDef}, attach the {@link ModelASTStages} for that {@link ModelASTPipelineDef} to the
     * run, and return an {@link ArgumentListExpression} containing a closure that returns the {@Root} we just created.
     */
    ArgumentListExpression transform(@Nonnull ModelASTPipelineDef pipelineDef, @CheckForNull Run<?, ?> run) {
        wrapper.pipelineId = pipelineDef.toJSON().hashCode()
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

        ClosureExpression result = wrapper.createPipelineClosureX(root)

        return args(result)
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
            MapExpression m
            if (original.variables == null ||
                (original.variables instanceof ModelASTClosureMap &&
                    ((ModelASTClosureMap)original.variables).variables.isEmpty())) {
                // Zero-arg agent type
                MapExpression zeroArg = new MapExpression()
                zeroArg.addMapEntryExpression(constX(original.agentType.key), constX(true))
                m = zeroArg
            } else {
                BlockStatementMatch match =
                    matchBlockStatement((Statement) original.sourceLocation)
                if (match != null) {
                    m = recurseAndTransformMappedClosure(match.body)
                } else {
                    throw new IllegalArgumentException("Expected a BlockStatement for agent but got an instance of ${original.sourceLocation.class}")
                }
            }
            return wrapper.withExternalClassContext(ctorX(ClassHelper.make(Agent.class), args(wrapper.asWrappedScriptContextVariable(closureX(block(returnS(m)))))))
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
        if (isGroovyAST(original)) {
            return transformEnvironmentMap(original.variables)
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
    Expression transformEnvironmentMap(@Nonnull Map<ModelASTKey, ModelASTEnvironmentValue> variables, boolean disableWrapping = false) {
        if (!variables.isEmpty()) {
            return wrapper.withExternalClassContext(ctorX(ClassHelper.make(Environment.class),
                    args(
                            generateEnvironmentResolver(variables, ModelASTValue.class, disableWrapping),
                            generateEnvironmentResolver(variables, ModelASTInternalFunctionCall.class, disableWrapping)
                    )))
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
    private Expression generateEnvironmentResolver(@Nonnull Map<ModelASTKey, ModelASTEnvironmentValue> variables, @Nonnull Class valueType, boolean disableWrapping) {
        Set<String> keys = new HashSet<>()

        // We need to keep track of the environment keys for use in
        keys.addAll(variables.findAll { k, v -> v instanceof ModelASTValue }.collect { k, v -> k.key })

        MapExpression closureMap = new MapExpression()

        variables.each { k, v ->
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

        Expression result = callX(ClassHelper.make(Environment.EnvironmentResolver.class), "instanceFromMap",
                args(closureMap))

        // in the case of Matrix Cell Environment blocks, we know they don't need to be inside a closure
        // they are all literal key-values with no external references allowed.
        if (!disableWrapping) {
            result = wrapper.asWrappedScriptContextVariable(result)
        }
        return result
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
        } else if (expr instanceof NotExpression) {
            // Translate the nested expression
            return new NotExpression(translateEnvironmentValueAndCall(targetVar, expr.expression, keys))
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
            return wrapper.withExternalClassContext(ctorX(ClassHelper.make(Root.class),
                args(transformAgent(original.agent),
                    transformStages(original.stages),
                    transformPostBuild(original.postBuild),
                    transformEnvironment(original.environment),
                    transformTools(original.tools),
                    transformOptions(original.options),
                    transformTriggers(original.triggers),
                    transformParameters(original.parameters),
                    transformLibraries(original.libraries),
                    constX(original.stages.getUuid().toString()))))
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
    Expression transformStage(@CheckForNull ModelASTStage original, boolean disableWrapping = false) {
        if (isGroovyAST(original)) {
            // Matrix is a special form of parallel
            // At runtime, they behave the same
            Expression parallel = original.parallel != null ?
                    transformStages(original.parallel) :
                    transformMatrix(original.matrix)

            if (disableWrapping) {
                return ctorX(ClassHelper.make(Stage.class),
                        args(constX(original.name),
                                transformStepsFromStage(original, disableWrapping),
                                transformAgent(original.agent),
                                transformPostStage(original.post),
                                transformStageConditionals(original.when),
                                transformTools(original.tools),
                                transformEnvironment(original.environment),
                                constX(original.failFast != null ? original.failFast : false),
                                transformOptions(original.options),
                                transformStageInput(original.input, original.name),
                                transformStages(original.stages),
                                parallel,
                                constX(null)))
            } else {
                return wrapper.withExternalClassContext(ctorX(ClassHelper.make(Stage.class),
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
                                transformStages(original.stages),
                                parallel,
                                constX(null))))
            }
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

    @SuppressFBWarnings(value = "UPM_UNCALLED_PRIVATE_METHOD")
    private Expression valueOrNull(@CheckForNull ModelASTValue value, Object defaultValue = null) {
        if (value?.sourceLocation instanceof Expression) {
            return (Expression) value.sourceLocation
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
                        closList.addExpression(wrapper.asWrappedScriptContextVariable(desc.transformToRuntimeAST(cond)))
                    }
                }
            }

            return wrapper.withExternalClassContext(ctorX(ClassHelper.make(StageConditionals.class),
                    args(wrapper.asWrappedScriptContextVariable(closureX(block(returnS(closList)))),
                            constX(original.beforeAgent != null ? original.beforeAgent : false),
                            constX(original.beforeInput != null ? original.beforeInput : false)
                    )))
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
    Expression transformStages(@CheckForNull ModelASTStages original, boolean disableWrapping = false) {
        if (isGroovyAST(original) && !original.stages.isEmpty()) {
            ArrayList<Expression> argList = new ArrayList<>()
            original.stages.each { s ->
                argList.add(transformStage(s, disableWrapping))
            }

            ClassNode classNode = original instanceof ModelASTParallel ?
                    ClassHelper.make(Parallel.class) :
                    ClassHelper.make(Stages.class)

            if (disableWrapping) {
                return ctorX(classNode, args(wrapper.withExternalClassContext(argList)))
            } else {
                return wrapper.withExternalClassContext(ctorX(classNode, args(wrapper.withExternalClassContext(argList))))
            }
        }

        return constX(null)
    }

    /**
     * Generates the AST (to be CPS-transformed) for instantiating {@link Matrix}.
     * Generates a synthetic stage for each combination of axes and excludes.
     *
     * @param original The parsed AST model
     * @return The AST for a constructor call for {@link Matrix}, or the constant null expression if the original
     * cannot be transformed.
     */
    Expression transformMatrix(@CheckForNull ModelASTMatrix original) {
        if (isGroovyAST(original) && !original?.stages?.stages?.isEmpty() && !original?.axes?.axes?.isEmpty()) {

            // generate matrix combinations of axes - cartesianProduct
            Set<Map<ModelASTKey, ModelASTValue>> expansion = expandAxes(original.axes.axes)

            // remove excluded combinations
            filterExcludes(expansion, original.excludes)

            Expression stagesExpression = wrapper.asWrappedScriptContextVariable(transformStages(original.stages, true))

            ArrayList<Expression> argList = new ArrayList<>()
            expansion.each { item ->
                argList.add(transformMatrixStage(item, original, stagesExpression))
            }

            // return matrix class containing the list of generated stages
            return wrapper.withExternalClassContext(ctorX(ClassHelper.make(Matrix.class), args(wrapper.withExternalClassContext(argList))))
        }

        return constX(null)
    }

    Set<Map<ModelASTKey, ModelASTValue>> expandAxes(List<ModelASTAxis> axes) {
        def result = new LinkedHashSet<Map<ModelASTKey, ModelASTValue>>()
        // using LinkedHashMap to maintain insertion order
        // axes will be added in the order they are declared
        result.add(new LinkedHashMap<ModelASTKey, ModelASTValue>())
        axes.each { axis ->
            def interim = result
            result = new LinkedHashSet<Map<ModelASTKey, ModelASTValue>>()
            axis.values.each { value ->
                interim.each {
                    Map<ModelASTKey, ModelASTValue> generated = it.clone()
                    generated.put(axis.name, value)
                    result.add(generated)
                }
            }
        }
        return result
    }

    void filterExcludes(Set<Map<ModelASTKey, ModelASTValue>> expansion, ModelASTExcludes excludes) {
        if (isGroovyAST(excludes)) {
            excludes.excludes.each { exclude ->
                Set<Map<ModelASTKey, ModelASTValue>> filter = expansion.clone()
                exclude.getExcludeAxes().each { excludeAxis ->
                    filter.removeAll { !excludeAxis.inverse ^ excludeAxis.values.contains(it.get(excludeAxis.name)) }
                }
                expansion.removeAll(filter)
            }
        }
    }

    /**
     * Generates the AST (to be CPS-transformed) for instantiating {@link Stage}.
     *
     * @param original The parsed AST model
     * @return The AST for a constructor call for {@link Stage}, or the constant null expression if the original
     * cannot be transformed.
     */
    Expression transformMatrixStage(@CheckForNull Map<ModelASTKey, ModelASTValue> cell, @CheckForNull ModelASTMatrix original, Expression stagesExpression) {
        if (isGroovyAST(original)) {

            //     create a generated stage with unique name based on combination
            //     TODO: maybe if there is only one stage, use it as the template for these synthetic stages?  Avoid two layers of stages when one would do.

            List<String> cellLabels = new ArrayList<>();
            cell.each { cellLabels.add(it.key.key.toString() + " = '" + it.value.value.toString() + "'") }

            // TODO: Do I need to create a new ModelASTStage each time?  I don't think so.
            String name = "Matrix - " + cellLabels.join(", ")

            return wrapper.withExternalClassContext(ctorX(ClassHelper.make(Stage.class),
                    args(constX(name),
                            constX(null), // steps
                            transformAgent(original.agent),
                            transformPostStage(original.post),
                            transformStageConditionals(original.when),
                            transformTools(original.tools),
                            transformEnvironment(original.environment),
                            constX(false), // failfast on serial is not interesting
                            transformOptions(original.options),
                            transformStageInput(original.input, name),
                            stagesExpression,
                            constX(null), // parallel
                            transformEnvironmentMap(cell, true))))
            //  matrixCellEnvironment holding values for this cell in the matrix
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
    Expression transformStepsFromStage(@CheckForNull ModelASTStage original, boolean disableWrapping = false) {
        if (isGroovyAST(original)) {
            BlockStatementMatch stageMatch = matchBlockStatement((Statement) original.sourceLocation)
            if (stageMatch != null) {
                Statement stepsMethod = asBlock(stageMatch.body.code).statements.find { s ->
                    matchMethodCall(s)?.methodAsString == "steps"
                }
                if (stepsMethod != null) {
                    BlockStatementMatch stepsMatch = matchBlockStatement(stepsMethod)
                    if (stepsMatch != null) {
                        Expression transformedBody = StepRuntimeTransformerContributor.transformStage(original, stepsMatch.body)
                        if (!disableWrapping) {
                            transformedBody = wrapper.asWrappedScriptContextVariable(transformedBody)
                        }
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

    private class Wrapper {
        private SourceUnit sourceUnit
        private ModuleNode moduleNode
        private long nameCount = 0
        private final List<Statement> pipelineElementHandles = new ArrayList<>()
        private final def methodClassNode = [:]

        private static final int groupSize = 50

        int pipelineId = 0

        Wrapper(SourceUnit sourceUnit) {
            this.sourceUnit = sourceUnit
            this.moduleNode = sourceUnit.AST
        }

        /**
         *
         * @param groupName
         * @param objects
         * @return
         */
        private String createStableUniqueName(String groupName) {
            int id = Math.abs(Objects.hash(groupName, nameCount, pipelineId))
            return "__PipelineRuntime_${groupName}_${nameCount++}_${id}__"
        }

        /**
         * Creates the Pipeline closure including populating it with element handle declarations
         * @param root the Root element generated by processing the pipeline ast
         * @return a closure to be passed as an arg to the Pipeline runtime
         */
        ClosureExpression createPipelineClosureX(Expression root) {

            BlockStatement pipelineBlock = block()

            declareClosureScopedHandles(pipelineBlock)

            declareFunctionGroupedHandles(pipelineBlock)

            // finally return the pipeline runtime elements
            pipelineBlock.addStatement(returnS(root))

            return closureX(pipelineBlock)

        }


        /**
         * Adds handles to the Pipeline block directly, if needed.
         *
         * Adding all handle declarations directly to the Pipeline block/closure will error for 250 or more handles.
         * It will also often encounter "method code too large" errors long before that.
         *
         * Moving groups of handle declarations to script-level functions still adds those handles to the script binding.
         * and avoids "method code too large" errors.
         * However function declared handles cannot access a script's local variables ("def someVar = 0").
         * This is the only scenario in which function declared handles with script binding have problems.
         *
         * To maintain some support for local variables, this method adds handles to the closure when it detects that is needed.
         * Currently, it adds all handles if  if any if "pipeline {}" is not the only element in script.
         * This is sufficient for now, but a better solution would be to:
         *
         * * Specifically detect that local variables are used
         * * Log a warning that this is no advised
         * * detect which handles reference local variables and then only declare those handles in the closure.
         *
         * This would still hit method too large errors sooner or later, but it would be at quite a bit later.
         *
         * @param pipelineBlock the block statement to add declarations to
         */
        private void declareClosureScopedHandles(BlockStatement pipelineBlock) {
            if (moduleNode.statementBlock.statements.size() != 1) {
                pipelineBlock.addStatements(pipelineElementHandles)
                pipelineElementHandles.clear()
            }
        }

        /**
         * Adds groups of handle declarations to functions and adds calls to those functions to the pipeline block.
         * Avoid "method code too large" errors and other compiler breaks related to Groovy, JVM, and CPS limitations.
         * @param pipelineBlock
         */
        private void declareFunctionGroupedHandles(BlockStatement pipelineBlock) {
            if (pipelineElementHandles.size() == 0) {
                return
            }
            List<Statement> methodCalls = new ArrayList<>()

            BlockStatement currentBlock = block()
            def count = 0
            pipelineElementHandles.each { item ->
                if (count++ >= groupSize) {
                    count = 1
                    pipelineBlock.addStatement(stmt(defineMethodAndCall("Variables", ClassHelper.VOID_TYPE, currentBlock)))
                    currentBlock = block()
                }

                currentBlock.addStatement(item)
            }

            // These variable handles are declared in functions, but will still be bound to the script context
            // Doing this here ensures the variables make the trip across to run time - if they don't make it, neither did this pipeline

            pipelineBlock.addStatement(stmt(defineMethodAndCall("Variables", ClassHelper.VOID_TYPE, currentBlock)))

            pipelineElementHandles.clear()
        }

        /**
         * Turns a constructor call into a function call that returns the constructed instance.
         * The function is declared on a separate class, outside of the script context.
         * The function cannot reliably access script bindings except via handles to script context variables.
         *
         * @param type the type to be instantiated
         * @param args arguments to the constructor
         * @return call to a function that returns an instance of type
         */
        Expression withExternalClassContext(ConstructorCallExpression expression) {
            withExternalClassContext(expression.type.nameWithoutPackage, expression.type, expression)
        }

        /**
         * Turns a list of expressions into a closure that returns a list instance (not a ListExpression).
         * The closure is defined outside the script context and cannot reliably access script bindings except via handles.
         *
         * Works around limitations on ListExpression literal declaration
         * (literal "[item0, item1, ...]") being limited to 250 items.
         *
         * @param expressions the list of expressions to be wrapped in a closure call
         * @return call to a closure that returns the provided ListExpression
         */
        Expression withExternalClassContext(List<Expression> listExpression) {
            return withExternalClassContext("ListExpression", ClassHelper.make(Object.class), toListClosure(listExpression))
        }

        /**
         * Turns an expression into a script bound variable declaration returned from a closure.
         * This delays the evaluation of that contents of these variables to when the closure is called,
         * and also evaluates the contents in the context of the current script environment.
         *
         * @param expression the expression to be replaced with a closure wrapped variable
         * @return call to a closure that returns the provided expression
         */
        Expression asWrappedScriptContextVariable(Expression expression) {
            VariableExpression variable = asScriptContextVariable(closureX(block(returnS(expression))))
            return callX(variable, 'call')
        }

        /**
         * Turns an expression into a script bound variable declaration. This does not wrap in closure.
         * Evaluation will occur at the start of the pipeline run.
         *
         * @param expression the expression to be replaced with a variable
         * @return a variable expression that evaluates to expression
         */
        VariableExpression asScriptContextVariable(Expression expression, String name = null) {

            if (!name) {
                name = createStableUniqueName('Closure')
            }

            VariableExpression variable = varX(name)

            Expression declarationExpression = new BinaryExpression(variable, ASSIGN, expression)
            pipelineElementHandles.add(stmt(declarationExpression))

            variable = varX(new DynamicVariable(name, false))

            return variable
        }

        /**
         * Turns an expression into a script bound variable declaration returned from a closure.
         * This delays the evaluation of that contents of these variables to when the closure is called,
         * and also evaluates the contents in the context of the current script environment.
         *
         * @param expression the expression to be replaced with a variable
         * @return call to a function that returns the provided expression
         */
        Expression defineMethodAndCall(String groupName, ClassNode returnType, Statement statement) {

            String name = createStableUniqueName(groupName)

            // The instance method referenced via script binding
            MethodNode method = new MethodNode(name, ACC_PUBLIC, returnType, [] as Parameter[], [] as ClassNode[], statement)
            moduleNode.getScriptClassDummy().addMethod(method)

            return callThisX(name)
        }

        /**
         * Returns a call expression that will return the passed in expression.
         * This allows us to split the pipeline ast into small enough chunks to avoid JVM class and method size limits
         * @param groupName Name of the grouping to add this function to
         * @param returnType return type of the function
         * @param returnXBody expression to be returned
         * @return callX that returns the value of returnXBody
         */
        Expression withExternalClassContext(String groupName, ClassNode returnType, Expression returnXBody) {
            // We break the the ast graph into classes with static mathods to work around JVM class and method size limitations
            // However, class loading isn't free, so we also don't want a single method per class
            ClassNode classNode = methodClassNode[groupName]
            // If we don't have a classNode for this group name or if this class has reached groupSize, start a new class
            if (classNode == null || classNode.methods.size() >= groupSize) {
                // Get an uncreative unique class name
                String className = createStableUniqueName(groupName)

                classNode = new ClassNode("generated." + className, ACC_PUBLIC, ClassHelper.make(RuntimeContainerBase.class))
                classNode.addConstructor(ACC_PUBLIC, [new Parameter(ClassHelper.make(CpsScript.class), "workflowScript")] as Parameter[], [ClassHelper.make(IOException.class)] as ClassNode[],
                        block(ctorSuperS(varX("workflowScript"))))

                this.moduleNode.addClass(classNode)

                // Add an instance of this class to the variables that can be referenced from anywhere in this script
                asScriptContextVariable(ctorX(classNode, args(varX('this'))), className)

                methodClassNode[groupName] = classNode
            }

            String className = classNode.nameWithoutPackage
            Expression variable = varX(new DynamicVariable(className, false))

            // Uncreative method naming is fine
            def methodCount = classNode.methods.size()
            String name = "get${groupName}_${methodCount}"

            // The only thing these methods need to do is contain something to return
            Statement methodBody =
                    block(
                            returnS(returnXBody)
                    )

            // The instance method referenced via script binding
            MethodNode method = new MethodNode(name, ACC_PUBLIC, returnType, [] as Parameter[], [] as ClassNode[], methodBody)
            classNode.addMethod(method)

            return callX(variable, name)
        }

        /**
         * Turns a list of expressions into a closure that returns a list (not a ListExpression)
         * Works around limitations on list expression literals
         * (literal "[item0, item1, ...]") being limited to 250 items.
         * @param expressions the list expressions to be wrapped in a function call
         * @return call to a function that returns the provided ListExpression
         */
        Expression toListClosure(Iterable<Expression> expressions) {
            ListExpression currentListExpression = new ListExpression()

            final int listLimit = 250

            // Local variable in groovy ast is a variable that ... accesses itself.
            VariableExpression variable = varX(createStableUniqueName('extendedList'))
            variable.accessedVariable = variable

            Expression declarationExpression = new DeclarationExpression(variable, ASSIGN, new ListExpression())
            BlockStatement block = block()
            block.addStatement(stmt(declarationExpression))

            def count = 0
            expressions.each { item ->
                if (count++ >= listLimit) {
                    count = 1
                    block.addStatement(stmt(callX(variable, 'addAll', args(currentListExpression))))
                    currentListExpression = new ListExpression()
                }

                currentListExpression.addExpression(item)
            }

            block.addStatement(stmt(callX(variable, 'addAll', args(currentListExpression))))
            block.addStatement(returnS(variable))

//            def type = ClassHelper.make(ListExpression.class)
            return callX(closureX(block), 'call')
        }

    }
}