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
import hudson.model.Run
import org.codehaus.groovy.ast.ClassHelper
import org.codehaus.groovy.ast.expr.*
import org.codehaus.groovy.ast.stmt.BlockStatement
import org.codehaus.groovy.ast.stmt.ExpressionStatement
import org.codehaus.groovy.ast.stmt.Statement
import org.codehaus.groovy.ast.tools.GeneralUtils
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

@SuppressFBWarnings(value="SE_NO_SERIALVERSIONID")
class RuntimeASTTransformer {
    private final ModelASTPipelineDef pipelineDef

    RuntimeASTTransformer(@Nonnull ModelASTPipelineDef pipelineDef) {
        this.pipelineDef = pipelineDef
    }

    ArgumentListExpression transform(@CheckForNull Run<?,?> run) {
        Expression root = transformRoot(pipelineDef)
        if (run != null && run.getAction(ExecutionModelAction.class) == null) {
            ModelASTStages stages = pipelineDef.stages
            stages.removeSourceLocation()
            run.addAction(new ExecutionModelAction(stages))
        }
        return args(
            closureX(
                GeneralUtils.block(
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
                    ClosureExpression expr = translateEnvironmentValue(toTransform, keys)
                    if (expr != null) {
                        closureMap.addMapEntryExpression(constX(k.key), expr)
                    } else {
                        throw new IllegalArgumentException("Empty closure for ${k.key}")
                    }
                }
            }
        }

        return callX(ClassHelper.make(Environment.EnvironmentResolver.class), "instanceFromMap",
            args(closureMap))
    }

    private MethodCallExpression translateEnvironmentValueAndCall(Expression expr, Set<String> keys) {
        return callX(translateEnvironmentValue(expr, keys), constX("call"), new ArgumentListExpression())
    }

    @CheckForNull
    private Expression translateEnvironmentValue(Expression expr, Set<String> keys) {
        Expression body = null

        if (expr instanceof ConstantExpression) {
            body = expr
        } else if (expr instanceof BinaryExpression &&
            ((BinaryExpression) expr).getOperation().getType() == Types.PLUS) {
            BinaryExpression binExpr = (BinaryExpression) expr
            body = plusX(
                translateEnvironmentValueAndCall(binExpr.leftExpression, keys),
                translateEnvironmentValueAndCall(binExpr.rightExpression, keys)
            )
        } else if (expr instanceof GStringExpression) {
            GStringExpression gStrExpr = (GStringExpression) expr
            body = new GStringExpression(gStrExpr.text,
                gStrExpr.strings,
                gStrExpr.values.collect { translateEnvironmentValueAndCall(it, keys) }
            )
        } else if (expr instanceof PropertyExpression) {
            PropertyExpression propExpr = (PropertyExpression) expr
            if (propExpr.objectExpression instanceof VariableExpression) {
                if (((VariableExpression) propExpr.objectExpression).name == "env" &&
                    keys.contains(propExpr.propertyAsString)) {
                    body = environmentValueGetterCall(propExpr.propertyAsString)
                } else {
                    body = propX(
                        translateEnvironmentValueAndCall(propExpr.objectExpression, keys),
                        propExpr.property
                    )
                }
            } else {
                body = propExpr
            }
        } else if (expr instanceof MethodCallExpression) {
            MethodCallExpression mce = (MethodCallExpression) expr
            body = callX(
                translateEnvironmentValueAndCall(mce.objectExpression, keys),
                mce.method,
                args(mce.arguments.collect { a ->
                    translateEnvironmentValueAndCall(a, keys)
                })
            )
        } else if (expr instanceof VariableExpression) {
            VariableExpression ve = (VariableExpression) expr
            if (keys.contains(ve.name)) {
                body = environmentValueGetterCall(ve.name)
            } else if (ve.name == "this") {
                body = ve
            } else {
                body = callX(
                    varX("this"),
                    constX("getScriptPropOrParam"),
                    args(constX(ve.name))
                )
            }
        } else if (expr instanceof ElvisOperatorExpression) {
            ElvisOperatorExpression elvis = (ElvisOperatorExpression) expr
            body = new ElvisOperatorExpression(
                translateEnvironmentValueAndCall(elvis.trueExpression, keys),
                translateEnvironmentValueAndCall(elvis.falseExpression, keys)
            )
        } else if (expr instanceof ClosureExpression) {
            ClosureExpression cl = (ClosureExpression) expr
            BlockStatement closureBlock = GeneralUtils.block()
            eachStatement(cl.code) { s ->
                if (s instanceof ExpressionStatement) {
                    closureBlock.addStatement(
                        stmt(translateEnvironmentValueAndCall(s.expression, keys))
                    )
                } else {
                    // TODO: Message asking to report this so I can address it.
                    throw new IllegalArgumentException("Got an unexpected" + s.getClass())
                }
            }
            body = closureX(
                cl.parameters,
                closureBlock
            )
        } else {
            // TODO: Message asking to report this so I can address it.
            throw new IllegalArgumentException("Got an unexpected " + expr.getClass())
        }

        if (body != null) {
            return closureX(
                GeneralUtils.block(
                    returnS(
                        body
                    )
                )
            )
        }

        return constX(null)
    }

    private MethodCallExpression environmentValueGetterCall(String name) {
        return callX(callThisX("getClosure", constX(name)), "call")
    }

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

    Expression transformOptions(@CheckForNull ModelASTOptions original) {
        if (isGroovyAST(original) && !original.options.isEmpty()) {
            List<ModelASTOption> jobProps = new ArrayList<>()
            List<ModelASTOption> options = new ArrayList<>()
            List<ModelASTOption> wrappers = new ArrayList<>()

            SymbolLookup symbolLookup = SymbolLookup.get()

            original.options.each { o ->
                if (symbolLookup.findDescriptor(JobProperty.class, o.name) != null) {
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
                        optsMap.addMapEntryExpression(constX(o.name), methodCallToDescribable(expr))
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

            return ctorX(ClassHelper.make(Options.class),
                args(transformListOfDescribables(jobProps), optsMap, wrappersMap))
        }

        return constX(null)
    }

    Expression transformParameters(@CheckForNull ModelASTBuildParameters original) {
        return transformDescribableContainer(original, original?.parameters, Parameters.class)
    }

    Expression transformPostBuild(@CheckForNull ModelASTPostBuild original) {
        return transformBuildConditionsContainer(original, PostBuild.class)
    }

    Expression transformPostStage(@CheckForNull ModelASTPostStage original) {
        return transformBuildConditionsContainer(original, PostStage.class)
    }

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
                    transformLibraries(original.libraries)))
        }

        return constX(null)
    }

    Expression transformStage(@CheckForNull ModelASTStage original) {
        if (isGroovyAST(original)) {
            return ctorX(ClassHelper.make(Stage.class),
                args(constX(original.name),
                    transformStepsFromStage(original),
                    transformAgent(original.agent),
                    transformPostStage(original.post),
                    transformStageConditionals(original.when),
                    transformTools(original.tools),
                    transformEnvironment(original.environment)))
        }

        return constX(null)
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
                args(closureX(GeneralUtils.block(returnS(closList)))))
        }
        return constX(null)
    }

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
                        return callX(ClassHelper.make(Utils), "createStepsBlock",
                            args(stepsMatch.body))
                    }
                }
            }
        }

        return constX(null)
    }

    Expression transformStepsFromBuildCondition(@CheckForNull ModelASTBuildCondition original) {
        if (isGroovyAST(original)) {
            BlockStatementMatch condMatch = matchBlockStatement((Statement) original.sourceLocation)
            return callX(ClassHelper.make(Utils), "createStepsBlock",
                args(condMatch.body))
        }
        return constX(null)
    }

    Expression transformTools(@CheckForNull ModelASTTools original) {
        if (isGroovyAST(original) && !original.tools?.isEmpty()) {
            MapExpression toolsMap = new MapExpression()
            original.tools.each { k, v ->
                if (v.sourceLocation != null && v.sourceLocation instanceof Expression) {
                    toolsMap.addMapEntryExpression(constX(k.key), (Expression)v.sourceLocation)
                }
            }
            return ctorX(ClassHelper.make(Tools.class), args(toolsMap))
        }
        return constX(null)
    }

    Expression transformTriggers(@CheckForNull ModelASTTriggers original) {
        return transformDescribableContainer(original, original?.triggers, Triggers.class)
    }

}
