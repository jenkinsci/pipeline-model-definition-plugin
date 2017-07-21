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
package org.jenkinsci.plugins.pipeline.modeldefinition.parser

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings
import hudson.model.Describable
import hudson.model.Descriptor
import jenkins.model.Jenkins
import org.codehaus.groovy.ast.ASTNode
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.MethodNode
import org.codehaus.groovy.ast.ModuleNode
import org.codehaus.groovy.ast.expr.*
import org.codehaus.groovy.ast.stmt.BlockStatement
import org.codehaus.groovy.ast.stmt.ExpressionStatement
import org.codehaus.groovy.ast.stmt.Statement
import org.codehaus.groovy.control.SourceUnit
import org.codehaus.groovy.syntax.Types
import org.jenkinsci.plugins.pipeline.modeldefinition.DescriptorLookupCache
import org.jenkinsci.plugins.pipeline.modeldefinition.Messages
import org.jenkinsci.plugins.pipeline.modeldefinition.Utils
import org.jenkinsci.plugins.pipeline.modeldefinition.agent.DeclarativeAgentDescriptor
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.*
import org.jenkinsci.plugins.pipeline.modeldefinition.ModelStepLoader
import org.jenkinsci.plugins.pipeline.modeldefinition.model.BuildCondition
import org.jenkinsci.plugins.pipeline.modeldefinition.model.Root
import org.jenkinsci.plugins.pipeline.modeldefinition.validator.ErrorCollector
import org.jenkinsci.plugins.pipeline.modeldefinition.validator.ModelValidator
import org.jenkinsci.plugins.pipeline.modeldefinition.validator.ModelValidatorImpl
import org.jenkinsci.plugins.pipeline.modeldefinition.validator.SourceUnitErrorCollector
import org.jenkinsci.plugins.structs.describable.DescribableModel
import org.jenkinsci.plugins.structs.describable.DescribableParameter

import javax.annotation.CheckForNull
import javax.annotation.Nonnull

import static ASTParserUtils.getSourceText

/**
 * Recursively walks AST tree of parsed Jenkinsfile and builds validation model into {@link ModelASTPipelineDef}
 * reporting any errors as it encounters them.
 *
 * <p>
 * This class has the {@code parseXyz} series of methods and {@code matchXyz} series of methods
 * that both transform an AST node into a specific model object. The difference is that the former
 * reports an error if the input AST node doesn't match the expected form, while the latter returns
 * null under the same circumstance.
 *
 * @author Kohsuke Kawaguchi
 * @author Andrew Bayer
 */
@SuppressFBWarnings(value="SE_NO_SERIALVERSIONID")
class ModelParser implements Parser {
    /**
     * Represents the source file being processed.
     */
    private final SourceUnit sourceUnit;

    private final ModelValidator validator

    private final ErrorCollector errorCollector

    private final DescriptorLookupCache lookup

    public ModelParser(SourceUnit sourceUnit) {
        this.sourceUnit = sourceUnit;
        this.errorCollector = new SourceUnitErrorCollector(sourceUnit)
        this.validator = new ModelValidatorImpl(errorCollector)
        this.lookup = DescriptorLookupCache.getPublicCache()
    }

    public @CheckForNull ModelASTPipelineDef parse() {
        return parse(sourceUnit.AST);
    }

    public @CheckForNull List<ModelASTStep> parsePlainSteps(ModuleNode src) {
        src.statementBlock.statements.collect() {
            return parseStep(it);
        }
    }

    public void checkForNestedPipelineStep(Statement statement) {
        def b = ASTParserUtils.matchBlockStatement(statement)
        if (b != null) {
            if (b.methodName == ModelStepLoader.STEP_NAME) {
                ModelASTPipelineDef p = new ModelASTPipelineDef(statement)
                errorCollector.error(p, Messages.ModelParser_PipelineBlockNotAtTop(ModelStepLoader.STEP_NAME))
            }
            ASTParserUtils.eachStatement(b.body.code) { s ->
                checkForNestedPipelineStep(s)
            }
        }
    }

    /**
     * Given a Groovy AST that represents a parsed source code, parses
     * that into {@link ModelASTPipelineDef}
     */
    public @CheckForNull ModelASTPipelineDef parse(ModuleNode src) {
        // first, quickly ascertain if this module should be parsed at all
        // TODO: 'use script' escape hatch
        def pst = src.statementBlock.statements.find {
            MethodCallExpression m = ASTParserUtils.matchMethodCall(it)
            return m != null && ASTParserUtils.matchMethodName(m) == ModelStepLoader.STEP_NAME
        }

        if (pst==null) {
            // Check if there's a 'pipeline' step somewhere nested within the other statements and error out if that's the case.
            src.statementBlock.statements.each { checkForNestedPipelineStep(it) }
            return null; // no 'pipeline', so this doesn't apply
        }

        ModelASTPipelineDef r = new ModelASTPipelineDef(pst);

        def pipelineBlock = ASTParserUtils.matchBlockStatement(pst);
        if (pipelineBlock==null) {
            // We never get to the validator with this error
            errorCollector.error(r, Messages.ModelParser_PipelineStepWithoutBlock(ModelStepLoader.STEP_NAME))
            return null;
        }

        def sectionsSeen = new HashSet();
        ASTParserUtils.eachStatement(pipelineBlock.body.code) { stmt ->
            ModelASTKey placeholderForErrors = new ModelASTKey(stmt)
            def mc = ASTParserUtils.matchMethodCall(stmt);
            if (mc == null) {
                errorCollector.error(placeholderForErrors,
                    Messages.ModelParser_InvalidSectionDefinition(getSourceText(stmt, sourceUnit)))
            } else {
                def name = parseMethodName(mc);
                // Here, method name is a "section" name at the top level of the "pipeline" closure, which must be unique.
                if (!sectionsSeen.add(name)) {
                    // Also an error that we couldn't actually detect at model evaluation time.
                    errorCollector.error(placeholderForErrors, Messages.Parser_MultipleOfSection(name))
                }

                switch (name) {
                    case 'stages':
                        r.stages = parseStages(stmt);
                        break;
                    case 'environment':
                        r.environment = parseEnvironment(stmt);
                        break;
                    case 'post':
                        r.postBuild = parsePostBuild(stmt);
                        break;
                    case 'agent':
                        r.agent = parseAgent(stmt);
                        break;
                    case 'tools':
                        r.tools = parseTools(stmt)
                        break
                    case 'options':
                        r.options = parseOptions(stmt)
                        break
                    case 'parameters':
                        r.parameters = parseBuildParameters(stmt)
                        break
                    case 'triggers':
                        r.triggers = parseTriggers(stmt)
                        break
                    case 'libraries':
                        r.libraries = parseLibraries(stmt)
                        break
                    case 'properties':
                        errorCollector.error(placeholderForErrors, Messages.ModelParser_RenamedProperties())
                        break
                    case 'wrappers':
                        errorCollector.error(placeholderForErrors, "The 'wrappers' section has been removed as of version 0.8. Use 'options' instead.")
                        break
                    case 'jobProperties':
                        errorCollector.error(placeholderForErrors, Messages.ModelParser_RenamedJobProperties())
                        break
                    case 'notifications':
                        errorCollector.error(placeholderForErrors, Messages.ModelParser_RenamedNotifications())
                        break
                    case 'postBuild':
                        errorCollector.error(placeholderForErrors, Messages.ModelParser_RenamedPostBuild())
                        break
                    default:
                        // We need to check for unknowns here.
                        errorCollector.error(placeholderForErrors, Messages.Parser_UndefinedSection(name))
                }
            }
        }

        r.validate(validator)
        pipelineBlock.whole.arguments = (ArgumentListExpression)ASTParserUtils.buildAst {
            argumentList {
                closure {
                    parameters {}
                    block {
                        returnStatement {
                            expression.add(Root.transformToRuntimeAST(r))
                        }
                    }
                }
            }
        }
        ASTParserUtils.prettyPrint(pipelineBlock.whole.arguments)

/*        BlockStatement newBlock = new BlockStatement()
        newBlock.addStatement((Statement)ASTParserUtils.buildAst {
            expression {
                declaration {
                    variable "declarativeModelRoot"
                    token "="
                    expression.add(Root.transformToRuntimeAST(r))
                }
            }
        })
        newBlock.addStatements(src.statementBlock.getStatements())
        src.statementBlock = newBlock*/
        return r;
    }

    public @Nonnull ModelASTStages parseStages(Statement stmt) {
        def r = new ModelASTStages(stmt);

        def m = ASTParserUtils.matchBlockStatement(stmt);
        if (m==null) {
            // Should be able to get this validation later.
            return r
        } else {
            ASTParserUtils.eachStatement(m.body.code) {
                r.stages.add(parseStage(it));
            }
        }
        return r;
    }

    public @Nonnull ModelASTEnvironment parseEnvironment(Statement stmt) {
        def r = new ModelASTEnvironment(stmt);

        def m = ASTParserUtils.matchBlockStatement(stmt);
        if (m==null) {
            // Should be able to get this validation later.
            return r
            //errorCollector.error(r, "Expected a block")
        } else {
            boolean errorEncountered = false
            ASTParserUtils.eachStatement(m.body.code) { s ->
                if (s instanceof ExpressionStatement) {
                    def exp = s.expression;
                    if (exp instanceof BinaryExpression && exp.operation.type == Types.EQUAL) {
                        ModelASTKey key = parseKey(exp.leftExpression)
                        // Necessary check due to keys with identical names being equal.
                        if (r.variables.containsKey(key)) {
                            errorCollector.error(key, Messages.ModelParser_DuplicateEnvVar(key.key))
                            return
                        } else {
                            if (exp.rightExpression instanceof ConstantExpression ||
                                exp.rightExpression instanceof GStringExpression) {
                                r.variables[key] = parseArgument(exp.rightExpression)
                                return
                            } else if (exp.rightExpression instanceof MethodCallExpression) {
                                // This is special casing exclusively for credentials and will ideally be eliminated.
                                r.variables[key] = parseInternalFunctionCall((MethodCallExpression) exp.rightExpression)
                                return
                            } else if (exp.rightExpression instanceof BinaryExpression) {
                                if (((BinaryExpression)exp.rightExpression).operation.type  == Types.PLUS) {
                                    // This is to support JENKINS-42771, allowing `FOO = "b" + "a" + "r"` sorts of syntax.
                                    ModelASTValue envValue = envValueForStringConcat((BinaryExpression) exp.rightExpression)
                                    if (envValue != null) {
                                        r.variables[key] = envValue
                                    }
                                    return
                                } else {
                                    errorCollector.error(new ModelASTKey(exp.rightExpression), Messages.ModelParser_InvalidEnvironmentOperation())
                                    return
                                }
                            } else {
                                errorCollector.error(new ModelASTKey(exp.rightExpression), Messages.ModelParser_InvalidEnvironmentValue())
                                return
                            }
                        }
                    } else {
                        ModelASTKey badKey = new ModelASTKey(exp)
                        String srcTxt = getSourceText((ASTNode)exp, sourceUnit)
                        if (srcTxt.contains("=")) {
                            String keyTxt = srcTxt.split("=").first().trim()
                            errorCollector.error(badKey, Messages.ModelValidatorImpl_InvalidIdentifierInEnv(keyTxt))
                        } else {
                            errorCollector.error(badKey, Messages.ModelParser_InvalidEnvironmentIdentifier(srcTxt))

                        }
                    }
                }
                errorEncountered = true
            }
            if (errorEncountered) {
                errorCollector.error(r, Messages.ModelParser_ExpectedNVPairs())
            }
        }
        return r;
    }

    /**
     * Traverses a {@link BinaryExpression} known to be a {@link Types#PLUS}, to concatenate its various subexpressions
     * together as string values.
     * @param exp A non-null binary expression
     * @return The concatenated string equivalent of that binary expression, wrapped in an appropriate {@link ModelASTValue},
     * assuming no errors were encountered on the various subexpressions, in which case it will return null.
     */
    @CheckForNull
    private ModelASTValue envValueForStringConcat(@Nonnull BinaryExpression exp) {
        StringBuilder builder = new StringBuilder()
        boolean isLiteral = true

        if (exp.leftExpression instanceof BinaryExpression) {
            if (((BinaryExpression)exp.leftExpression).operation.type  == Types.PLUS) {
                ModelASTValue nestedString = envValueForStringConcat((BinaryExpression) exp.leftExpression)
                if (nestedString != null) {
                    if (!appendAndIsLiteral(nestedString, builder)) {
                        isLiteral = false
                    }
                } else {
                    return null
                }
            } else {
                errorCollector.error(new ModelASTKey(exp.leftExpression), Messages.ModelParser_InvalidEnvironmentOperation())
                return null
            }
        } else {
            if (!envValueFromArbitraryExpression(exp.leftExpression, builder)) {
                isLiteral = false
            }
        }
        if (!envValueFromArbitraryExpression(exp.rightExpression, builder)) {
            isLiteral = false
        }

        String valString = builder.toString()

        if (isLiteral) {
            return ModelASTValue.fromConstant(valString, exp)
        } else {
            return ModelASTValue.fromGString(valString, exp)
        }
    }

    private boolean envValueFromArbitraryExpression(@Nonnull Expression e, @Nonnull StringBuilder builder) {
        if (e instanceof ConstantExpression || e instanceof GStringExpression) {
            ModelASTValue val = parseArgument(e)
            return appendAndIsLiteral(val, builder)
        } else {
            errorCollector.error(new ModelASTKey(e), Messages.ModelParser_InvalidEnvironmentConcatValue())
            return true
        }
    }

    private boolean appendAndIsLiteral(@CheckForNull ModelASTValue val, @Nonnull StringBuilder builder) {
        if (val == null) {
            return true
        } else if (!val.isLiteral()) {
            builder.append(Utils.trimQuotes(val.value.toString()))
        } else {
            builder.append(val.value)
        }
        return val.isLiteral()

    }

    public @Nonnull ModelASTLibraries parseLibraries(Statement stmt) {
        def r = new ModelASTLibraries(stmt);

        def m = ASTParserUtils.matchBlockStatement(stmt);
        if (m==null) {
            // Should be able to get this validation later.
            return r
        } else {
            ASTParserUtils.eachStatement(m.body.code) {
                ModelASTMethodCall methCall = new ModelASTMethodCall(it)
                def mc = ASTParserUtils.matchMethodCall(it);
                if (mc == null || mc.methodAsString != "lib") {
                    errorCollector.error(r,Messages.ModelParser_ExpectedLibrary(getSourceText(it, sourceUnit)));
                } else if (ASTParserUtils.matchBlockStatement(it) != null) {
                    errorCollector.error(methCall, Messages.ModelParser_CannotHaveBlocks(Messages.Parser_Libraries()))
                } else {
                    methCall = parseMethodCall(mc)
                    if (methCall.args.isEmpty()) {
                        errorCollector.error(methCall, Messages.ModelParser_ExpectedLibrary(getSourceText(mc, sourceUnit)))
                    } else if (methCall.args.size() > 1 || !(methCall.args.first() instanceof ModelASTValue)) {
                        // TODO: Decide whether we're going to support LibraryRetrievers. If so, the above changes.
                        // It's this way explicitly to just handle 'lib("foo@1.2.3")' syntax. Well, more accurately,
                        // it's this way so that we just handle 'lib("foo@1.2.3")' for now but can easily add support
                        // for something like 'lib(identifier:"foo@1.2.3", retriever:[$class:...])' in the future without
                        // breaking backwards compatibility.
                        errorCollector.error(methCall, Messages.ModelParser_ExpectedLibrary(getSourceText(mc, sourceUnit)))
                    } else {
                        r.libs.add((ModelASTValue)methCall.args.first())
                    }
                }
            }
        }
        return r;
    }

    public @Nonnull ModelASTTools parseTools(Statement stmt) {
        def r = new ModelASTTools(stmt);

        def m = ASTParserUtils.matchBlockStatement(stmt);
        if (m==null) {
            // Should be able to get this validation later.
            return r
        } else {
            ASTParserUtils.eachStatement(m.body.code) { s ->
                def mc = ASTParserUtils.matchMethodCall(s);
                if (mc == null) {
                    // Not sure of a better way to deal with this - it's a full-on parse-time failure.
                    errorCollector.error(r, Messages.ModelParser_ExpectedTool());
                } else {
                    def toolTypeKey = parseKey(mc.method);

                    List<Expression> args = ((TupleExpression) mc.arguments).expressions
                    if (args.isEmpty()) {
                        errorCollector.error(toolTypeKey, Messages.ModelParser_NoArgForTool(toolTypeKey.key))
                    } else if (args.size() > 1) {
                        errorCollector.error(toolTypeKey, Messages.ModelParser_TooManyArgsForTool(toolTypeKey.key))
                    } else {
                        r.tools[toolTypeKey] = parseArgument(args[0])
                    }
                }
            }
        }
        return r;
    }

    public @Nonnull ModelASTStage parseStage(Statement stmt) {
        ModelASTStage stage = new ModelASTStage(stmt)
        def m = ASTParserUtils.matchBlockStatement(stmt);
        if (!m?.methodName?.equals("stage")) {
            // Not sure of a better way to deal with this - it's a full-on parse-time failure.
            errorCollector.error(stage, Messages.ModelParser_ExpectedStage());
            return stage
        }

        def nameExp = m.getArgument(0);
        if (nameExp==null) {
            // Not sure of a better way to deal with this - it's a full-on parse-time failure.
            errorCollector.error(stage, Messages.ModelParser_ExpectedStageName());
        }

        stage.name = parseStringLiteral(nameExp)
        def sectionsSeen = new HashSet()
        def bodyExp = m.getArgument(1)
        if (bodyExp == null || !(bodyExp instanceof ClosureExpression)) {
            errorCollector.error(stage, Messages.ModelParser_StageWithoutBlock())
        } else {
            ASTParserUtils.eachStatement(((ClosureExpression)bodyExp).code) { s ->
                def mc = ASTParserUtils.matchMethodCall(s);
                if (mc == null) {
                    errorCollector.error(stage, Messages.ModelParser_InvalidStageSectionDefinition(getSourceText(s, sourceUnit)))
                } else {
                    def name = parseMethodName(mc);

                    // Here, method name is a "section" name in the "stage" closure, which must be unique.
                    if (!sectionsSeen.add(name)) {
                        // Also an error that we couldn't actually detect at model evaluation time.
                        errorCollector.error(stage, Messages.Parser_MultipleOfSection(name))
                    }
                    switch (name) {
                        case 'agent':
                            stage.agent = parseAgent(s)
                            break
                        case 'when':
                            stage.when = parseWhen(s)
                            break
                        case 'steps':
                            def stepsBlock = ASTParserUtils.matchBlockStatement(s);
                            BlockStatement block = ASTParserUtils.asBlock(stepsBlock.body.code)

                            // Handle parallel as a special case
                            if (block.statements.size()==1) {
                                def parallel = matchParallel(block.statements[0]);

                                if (parallel != null) {
                                    parallel.args.each { k, v ->
                                        stage.branches.add(parseBranch(k, ASTParserUtils.asBlock(v.code)));
                                    }
                                    stage.failFast = parallel.failFast
                                } else {
                                    // otherwise it's a single line of execution
                                    stage.branches.add(parseBranch("default", block));
                                }
                            } else {
                                // otherwise it's a single line of execution
                                stage.branches.add(parseBranch("default", block));
                            }
                            break
                        case 'post':
                            stage.post = parsePostStage(s)
                            break;
                        case 'tools':
                            stage.tools = parseTools(s)
                            break
                        case 'environment':
                            stage.environment = parseEnvironment(s)
                            break
                        default:
                            errorCollector.error(stage, Messages.ModelParser_UnknownStageSection(name))
                    }
                }
            }
        }
        return stage
    }

    public ModelASTWhen parseWhen(Statement statement) {
        def stepsBlock = ASTParserUtils.matchBlockStatement(statement)
        BlockStatement block = ASTParserUtils.asBlock(stepsBlock.body.code)
        ModelASTWhen w = new ModelASTWhen(statement)
        block.statements.each { s ->
            w.conditions.add(parseWhenContent(s))
        }

        return w
    }

    /**
     * Parses a block of code into {@link ModelASTBranch}
     */
    public ModelASTBranch parseBranch(String name, BlockStatement body) {
        def b = new ModelASTBranch(body);
        b.name = name
        body.statements.each { st ->
            b.steps.add(parseStep(st));
        }
        return b;
    }

    /**
     * Parses a block of code into {@link ModelASTOptions}
     */
    public ModelASTOptions parseOptions(Statement stmt) {
        def o = new ModelASTOptions(stmt);
        def m = ASTParserUtils.matchBlockStatement(stmt);
        if (m == null) {
            // Should be able to get this validation later.
            return o
        } else {
            ASTParserUtils.eachStatement(m.body.code) { s ->
                o.options.add(parseOption(s));
            }
        }
        return o;
    }

    /**
     * Parses a statement into a {@link ModelASTOption}
     */
    public ModelASTOption parseOption(Statement st) {
        ModelASTOption thisOpt = new ModelASTOption(st)
        def mc = ASTParserUtils.matchMethodCall(st);
        if (mc == null) {
            if (st instanceof ExpressionStatement && st.expression instanceof MapExpression) {
                errorCollector.error(thisOpt, Messages.ModelParser_MapNotAllowed(Messages.Parser_Options()))
                return thisOpt
            } else {
                // Not sure of a better way to deal with this - it's a full-on parse-time failure.
                errorCollector.error(thisOpt, Messages.ModelParser_ExpectedOption());
                return thisOpt
            }
        };

        def bs = ASTParserUtils.matchBlockStatement(st);
        if (bs != null) {
            errorCollector.error(thisOpt, Messages.ModelParser_CannotHaveBlocks(Messages.Parser_Options()))
            return thisOpt
        } else {
            ModelASTMethodCall mArgs = parseMethodCall(mc)
            thisOpt.args = mArgs.args
            thisOpt.name = mArgs.name
        }

        return thisOpt
    }

    /**
     * Parses a block of code into {@link ModelASTTriggers}
     */
    public ModelASTTriggers parseTriggers(Statement stmt) {
        def triggers = new ModelASTTriggers(stmt);
        def m = ASTParserUtils.matchBlockStatement(stmt);
        if (m == null) {
            // Should be able to get this validation later.
            return triggers
        } else {
            ASTParserUtils.eachStatement(m.body.code) { s ->
                triggers.triggers.add(parseTrigger(s));
            }
        }
        return triggers;
    }

    /**
     * Parses a statement into a {@link ModelASTTrigger}
     */
    public ModelASTTrigger parseTrigger(Statement st) {
        ModelASTTrigger trig = new ModelASTTrigger(st)
        def mc = ASTParserUtils.matchMethodCall(st);
        if (mc == null) {
            if (st instanceof ExpressionStatement && st.expression instanceof MapExpression) {
                errorCollector.error(trig, Messages.ModelParser_MapNotAllowed(Messages.Parser_Triggers()))
                return trig
            } else {
                // Not sure of a better way to deal with this - it's a full-on parse-time failure.
                errorCollector.error(trig, Messages.ModelParser_ExpectedTrigger());
                return trig
            }
        };

        def bs = ASTParserUtils.matchBlockStatement(st);
        if (bs != null) {
            errorCollector.error(trig, Messages.ModelParser_CannotHaveBlocks(Messages.Parser_Triggers()))
            return trig
        } else {
            ModelASTMethodCall mArgs = parseMethodCall(mc)
            trig.args = mArgs.args
            trig.name = mArgs.name
        }

        return trig
    }

    /**
     * Parses a block of code into {@link ModelASTBuildParameters}
     */
    public ModelASTBuildParameters parseBuildParameters(Statement stmt) {
        def bp = new ModelASTBuildParameters(stmt);
        def m = ASTParserUtils.matchBlockStatement(stmt);
        if (m == null) {
            // Should be able to get this validation later.
            return bp
        } else {
            ASTParserUtils.eachStatement(m.body.code) { s ->
                bp.parameters.add(parseBuildParameter(s));
            }
        }
        return bp;
    }

    /**
     * Parses a statement into a {@link ModelASTBuildParameter}
     */
    public ModelASTBuildParameter parseBuildParameter(Statement st) {
        ModelASTBuildParameter param = new ModelASTBuildParameter(st)
        def mc = ASTParserUtils.matchMethodCall(st);
        if (mc == null) {
            if (st instanceof ExpressionStatement && st.expression instanceof MapExpression) {
                errorCollector.error(param, Messages.ModelParser_MapNotAllowed(Messages.Parser_BuildParameters()))
                return param
            } else {
                // Not sure of a better way to deal with this - it's a full-on parse-time failure.
                 errorCollector.error(param, Messages.ModelParser_ExpectedBuildParameter());
                return param
            }
        };

        def bs = ASTParserUtils.matchBlockStatement(st);
        if (bs != null) {
            errorCollector.error(param, Messages.ModelParser_CannotHaveBlocks(Messages.Parser_BuildParameters()))
            return param
        } else {
            ModelASTMethodCall mArgs = parseMethodCall(mc)
            param.args = mArgs.args
            param.name = mArgs.name
        }

        return param
    }

    public ModelASTMethodCall parseMethodCall(MethodCallExpression expr) {
        ModelASTMethodCall m = new ModelASTMethodCall(expr)
        def methodName = parseMethodName(expr);
        m.name = methodName

        List<Expression> args = ((TupleExpression) expr.arguments).expressions

        args.each { a ->
            def namedArgs = ASTParserUtils.castOrNull(MapExpression, a);
            if (namedArgs != null) {
                namedArgs.mapEntryExpressions.each { e ->
                    // Don't need to check key duplication here because Groovy compilation will do it for us.
                    ModelASTKeyValueOrMethodCallPair keyPair = new ModelASTKeyValueOrMethodCallPair(e)
                    keyPair.key = parseKey(e.keyExpression)
                    if (e.valueExpression instanceof ClosureExpression) {
                        errorCollector.error(keyPair, Messages.ModelParser_MethodCallWithClosure())
                    } else if (e.valueExpression instanceof MethodCallExpression) {
                        keyPair.value = parseMethodCall((MethodCallExpression) e.valueExpression)
                    } else {
                        keyPair.value = parseArgument(e.valueExpression)
                    }
                    m.args << keyPair
                }
            } else if (a instanceof ClosureExpression) {
                errorCollector.error(m, Messages.ModelParser_MethodCallWithClosure())
            } else if (a instanceof MethodCallExpression) {
                m.args << parseMethodCall(a)
            } else {
                m.args << parseArgument(a)
            }
        }

        return m
    }

    public ModelASTEnvironmentValue parseInternalFunctionCall(MethodCallExpression expr) {
        ModelASTInternalFunctionCall m = new ModelASTInternalFunctionCall(expr)
        def methodName = ASTParserUtils.matchMethodName(expr);

        // TODO: post JENKINS-41759, switch to checking if it's a valid function name
        if (methodName == null || methodName != "credentials") {
            return parseArgument(expr)
        } else {
            m.name = methodName
            List<Expression> args = ((TupleExpression) expr.arguments).expressions

            args.each { a ->
                if (!(a instanceof ConstantExpression) && !(a instanceof GStringExpression)) {
                    errorCollector.error(m, Messages.ModelParser_InvalidInternalFunctionArg())
                } else {
                    m.args << parseArgument(a)
                }
            }

            return m
        }
    }

    public ModelASTClosureMap parseClosureMap(ClosureExpression expression) {
        ModelASTClosureMap map = new ModelASTClosureMap(expression)

        ASTParserUtils.eachStatement(expression.code) { s ->
            def mc = ASTParserUtils.matchMethodCall(s);
            if (mc == null) {
                // Not sure of a better way to deal with this - it's a full-on parse-time failure.
                errorCollector.error(map, Messages.ModelParser_ExpectedMapMethod());
            } else {

                def k = parseKey(mc.method);

                List<Expression> args = ((TupleExpression) mc.arguments).expressions
                if (args.isEmpty()) {
                    errorCollector.error(k, Messages.ModelParser_NoArgForMapMethodKey(k.key))
                } else if (args.size() > 1) {
                    errorCollector.error(k, Messages.ModelParser_TooManyArgsForMapMethodKey(k.key))
                } else if (args[0] instanceof ClosureExpression) {
                    map.variables[k] = parseClosureMap((ClosureExpression) args[0])
                } else {
                    map.variables[k] = parseArgument(args[0])
                }
            }
        }

        return map
    }

    /**
     * Parses a statement into a {@link ModelASTStep}
     */
    public ModelASTStep parseStep(Statement st) {
        ModelASTStep thisStep = new ModelASTStep(st)
        def mc = ASTParserUtils.matchMethodCall(st);
        if (mc == null) {
            // Not sure of a better way to deal with this - it's a full-on parse-time failure.
             errorCollector.error(thisStep, Messages.ModelParser_ExpectedStep());
            return thisStep
        };

        def stepName = parseMethodName(mc);
        if (stepName == "script") {
            return parseScriptBlock(st)
        } else if (stepName == "expression") {
            return parseWhenExpression(st)
        }

        List<Expression> args = ((TupleExpression) mc.arguments).expressions

        def bs = ASTParserUtils.matchBlockStatement(st);
        if (bs != null) {
            args = args.subList(0, args.size() - 1)    // cut out the closure argument
            thisStep = new ModelASTTreeStep(st)
            thisStep.name = stepName
            thisStep.args = parseArgumentList(args)
            thisStep.children = ASTParserUtils.eachStatement(bs.body.code) { parseStep(it) }
        } else {
            thisStep.name = stepName
            thisStep.args = populateStepArgumentList(thisStep, parseArgumentList(args))
        }

        return thisStep
    }

    /**
     * Parses a statement into a {@link ModelASTWhenContent}
     */
    public ModelASTWhenContent parseWhenContent(Statement st) {
        ModelASTWhenCondition condition = new ModelASTWhenCondition(st)
        def mc = ASTParserUtils.matchMethodCall(st);
        if (mc == null) {
            // Not sure of a better way to deal with this - it's a full-on parse-time failure.
            errorCollector.error(condition, Messages.ModelParser_ExpectedStep());
            return condition
        };

        def stepName = parseMethodName(mc);
        if (stepName == "expression") {
            return parseWhenExpression(st)
        }

        List<Expression> args = ((TupleExpression) mc.arguments).expressions

        def bs = ASTParserUtils.matchBlockStatement(st);
        condition.name = stepName
        if (bs != null) {
            args = args.subList(0, args.size() - 1)    // cut out the closure argument
            if (!args.isEmpty()) {
                condition.args = parseArgumentList(args)
            }
            condition.children = ASTParserUtils.eachStatement(bs.body.code) { parseWhenContent(it) }
        } else {
            condition.args = parseArgumentList(args)
        }

        return condition
    }


    private ModelASTArgumentList populateStepArgumentList(final ModelASTStep step, final ModelASTArgumentList origArgs) {
        if (Jenkins.getInstance() != null && origArgs instanceof ModelASTSingleArgument) {
            ModelASTValue singleArgValue = ((ModelASTSingleArgument)origArgs).value
            ModelASTNamedArgumentList namedArgs = new ModelASTNamedArgumentList(origArgs.sourceLocation)
            Descriptor<? extends Describable> desc = lookup.lookupStepFirstThenFunction(step.name)
            DescribableModel<? extends Describable> model = lookup.modelForStepFirstThenFunction(step.name)

            if (model != null) {
                DescribableParameter p = model.soleRequiredParameter

                if (p != null && !lookup.stepTakesClosure(desc)) {
                    ModelASTKey paramKey = new ModelASTKey(step.sourceLocation)
                    paramKey.key = p.name
                    namedArgs.arguments.put(paramKey, singleArgValue)

                    return namedArgs
                }
            }
        }

        return origArgs
    }

    public ModelASTWhenExpression parseWhenExpression(Statement st) {
        return parseCodeBlockInternal(st, new ModelASTWhenExpression(st), "When")
    }

    /**
     * Parses a statement into a {@link ModelASTScriptBlock}
     */
    public ModelASTScriptBlock parseScriptBlock(Statement st) {
        return parseCodeBlockInternal(st, new ModelASTScriptBlock(st), "Script")
    }

    private <T extends AbstractModelASTCodeBlock> T parseCodeBlockInternal(Statement st, T scriptBlock, String pronoun) {
        // TODO: Probably error out for cases with parameters?
        def bs = ASTParserUtils.matchBlockStatement(st);
        if (bs != null) {
            ModelASTNamedArgumentList groovyBlock = new ModelASTNamedArgumentList(bs.body)
            ModelASTKey key = new ModelASTKey(null)
            key.key = "scriptBlock"
            groovyBlock.arguments.put(key, ModelASTValue.fromConstant(getSourceText(bs.body.code, sourceUnit),
                bs.body.code))
            scriptBlock.args = groovyBlock
        } else {
            errorCollector.error(scriptBlock, Messages.ModelParser_StepWithoutBlock(pronoun))
        }

        return scriptBlock
    }

    /**
     * Parses a statement into a {@link ModelASTAgent}
     */
    public @Nonnull ModelASTAgent parseAgent(Statement st) {
        ModelASTAgent agent = new ModelASTAgent(st)
        def m = ASTParserUtils.matchBlockStatement(st);
        def mc = ASTParserUtils.matchMethodCall(st);
        if (m==null) {
            if (mc == null) {
                // Not sure of a better way to deal with this - it's a full-on parse-time failure.
                errorCollector.error(agent, Messages.ModelParser_ExpectedAgent())
            } else {
                List<Expression> args = ((TupleExpression) mc.arguments).expressions
                if (args.isEmpty()) {
                    errorCollector.error(agent, Messages.ModelParser_NoArgForAgent())
                } else if (args.size() > 1) {
                    errorCollector.error(agent, Messages.ModelParser_InvalidAgent())
                } else {
                    def agentCode = parseKey(args[0])
                    if (!(agentCode.key in DeclarativeAgentDescriptor.zeroArgModels().keySet())) {
                        errorCollector.error(agent, Messages.ModelParser_InvalidAgent())
                    } else {
                        agent.agentType = agentCode
                    }
                }
            }
        } else {
            def block = ASTParserUtils.asBlock(m.body.code)
            if (block.statements.isEmpty()) {
                errorCollector.error(agent, Messages.ModelParser_ExpectedAgent())
            } else if (block.statements.size() > 1) {
                errorCollector.error(agent, Messages.ModelParser_OneAgentMax())
            } else {
                def typeMeth = ASTParserUtils.matchMethodCall(block.statements[0])
                if (typeMeth == null) {
                    errorCollector.error(agent, Messages.ModelParser_ExpectedAgent())
                } else {
                    agent.agentType = parseKey(typeMeth.method)
                    ModelASTClosureMap parsed = parseClosureMap(m.body)
                    agent.variables = parsed.variables.get(agent.agentType)

                    // HACK FOR JENKINS-41118 to switch to "node" rather than "label" when multiple variable are set.
                    if (agent.agentType.key == "label" && agent.variables instanceof ModelASTClosureMap) {
                        agent.agentType.key = "node"
                    }
                }
            }
        }

        return agent
    }

    public @Nonnull ModelASTPostBuild parsePostBuild(Statement stmt) {
        def r = new ModelASTPostBuild(stmt);

        return parseBuildConditionResponder(stmt, r);
    }

    public @Nonnull ModelASTPostStage parsePostStage(Statement stmt) {
        def r = new ModelASTPostStage(stmt);

        return parseBuildConditionResponder(stmt, r);
    }

    @Nonnull
    public <R extends ModelASTBuildConditionsContainer> R parseBuildConditionResponder(Statement stmt, R responder) {
        def m = ASTParserUtils.matchBlockStatement(stmt);

        if (m==null) {
            errorCollector.error(responder, Messages.ModelParser_ExpectedBlock());
        } else {
            ASTParserUtils.eachStatement(m.body.code) {
                ModelASTBuildCondition bc = parseBuildCondition(it)
                if (bc.condition != null && bc.branch != null) {
                    responder.conditions.add(bc);
                }
            }
        }
        return responder;
    }

    public @Nonnull ModelASTBuildCondition parseBuildCondition(Statement st) {
        ModelASTBuildCondition b = new ModelASTBuildCondition(st)
        def m = ASTParserUtils.matchBlockStatement(st);
        if (m == null) {
            errorCollector.error(b, Messages.ModelParser_InvalidBuildCondition(BuildCondition.getOrderedConditionNames()))
        } else {
            b.branch = parseBranch("default", ASTParserUtils.asBlock(m.body.code))

            b.condition = m.methodName
        }

        return b
    }

    private ModelASTKey parseKey(Expression e) {
        ModelASTKey key = new ModelASTKey(e)
        key.setKey(parseStringLiteral(e))

        return key
    }

    private ModelASTArgumentList parseArgumentList(List<Expression> args) {
        switch (args.size()) {
        case 0:
            return new ModelASTNamedArgumentList(null);  // no arguments
        case 1:
            def namedArgs = ASTParserUtils.castOrNull(MapExpression, args[0]);
            // Special casing for legacy meta-step syntax, i.e., "[$class: 'Foo', arg1: 'something', ...]" - need to
            // treat that as a single argument but still handle the more standard "foo(arg1: 'something', ...)" case.
            if (namedArgs!=null && !namedArgs.mapEntryExpressions.any { parseKey(it.keyExpression)?.key?.equals('$class') }) {
                def m = new ModelASTNamedArgumentList(args[0]);
                namedArgs.mapEntryExpressions.each { e ->
                    // Don't need to check key duplication here because Groovy compilation will do it for us.
                    m.arguments[parseKey(e.keyExpression)] = parseArgument(e.valueExpression)
                }
                return m;
            } else {
                ModelASTSingleArgument singleArg = new ModelASTSingleArgument(args[0])
                singleArg.value = parseArgument(args[0])
                return singleArg
            }
        default:
            ModelASTPositionalArgumentList l = new ModelASTPositionalArgumentList(args[0]);
            args.each { e ->
                l.arguments.add(parseArgument(e))
            }
            return l
        }
    }

    /**
     * Parse the given expression as an argument to step, etc.
     */
    protected ModelASTValue parseArgument(Expression e) {
        if (e instanceof ConstantExpression) {
            return ModelASTValue.fromConstant(e.value, e)
        }
        if (e instanceof GStringExpression) {
            String rawSrc = getSourceText(e, sourceUnit)
            return ModelASTValue.fromGString(rawSrc, e)
        }
        if (e instanceof MapExpression) {
            return ModelASTValue.fromGString(getSourceText(e, sourceUnit), e)
        }
        if (e instanceof VariableExpression) {
            if (e.name in DeclarativeAgentDescriptor.zeroArgModels().keySet()) {
                return ModelASTValue.fromConstant(e.name, e)
            }
        }

        // for other composite expressions, treat it as in-place GString
        return ModelASTValue.fromGString("\${"+getSourceText(e, sourceUnit)+"}", e)
    }

    protected String parseStringLiteral(Expression exp) {
        return ASTParserUtils.parseStringLiteral(exp, errorCollector)
    }

    /**
     * Accepts literal, GString, function call etc but not other primitives
     */
    protected String parseString(Expression e) {
        if (e instanceof ConstantExpression) {
            if (e.value instanceof String)
                return (String)e.value
            errorCollector.error(ModelASTValue.fromConstant(e.getValue(), e),
                Messages.ModelParser_ExpectedStringLiteralButGot(e.value))
            return "error";
        }
        if (e instanceof GStringExpression) {
            return e.text
        }
        // for other composite expressions, treat it as in-place GString
        return "\${"+getSourceText(e, sourceUnit)+"}"
    }

    protected String parseMethodName(MethodCallExpression exp) {
        def s = ASTParserUtils.matchMethodName(exp)
        if (s==null) {
            if (exp.objectExpression instanceof VariableExpression &&
                !((VariableExpression)exp.objectExpression).isThisExpression()) {
                errorCollector?.error(ModelASTValue.fromConstant(null, exp), Messages.ModelParser_ObjectMethodCall())
            } else {
                errorCollector?.error(ModelASTValue.fromConstant(null, exp), Messages.ModelParser_ExpectedSymbol())
            }
            s = "error";
        }
        return s;
    }

    /**
     * Attempts to match a statement as {@link ParallelMatch} or return null.
     */
    public @CheckForNull ParallelMatch matchParallel(Statement st) {
        def whole = ASTParserUtils.matchMethodCall(st);
        if (whole!=null) {
            def methodName = ASTParserUtils.matchMethodName(whole);
            if ("parallel".equals(methodName)) {
                // beyond this point, if there's mismatch from the expectation we'll throw a problem, instead of returning null

                def args = (TupleExpression)whole.arguments; // list of arguments. in this case it should be just one
                int sz = args.expressions.size();
                Boolean failFast = null
                Map<String,ClosureExpression> parallelArgs = new LinkedHashMap<>()
                if (sz==1) {
                    def branches = ASTParserUtils.castOrNull(NamedArgumentListExpression, args.getExpression(sz - 1));
                    if (branches!=null) {
                        for (MapEntryExpression e : branches.mapEntryExpressions) {
                            String keyName = ASTParserUtils.matchStringLiteral(e.keyExpression)
                            if (keyName != null && keyName.equals("failFast")) {
                                ConstantExpression exp = ASTParserUtils.castOrNull(ConstantExpression.class, e.valueExpression)
                                if (exp == null || !(exp.value instanceof Boolean)) {
                                    errorCollector.error(new ModelASTKey(e.keyExpression),
                                        Messages.ModelParser_ExpectedFailFast())
                                } else {
                                    failFast = exp.value
                                }
                            } else {
                                ClosureExpression value = ASTParserUtils.castOrNull(ClosureExpression, e.valueExpression);
                                if (value == null) {
                                    errorCollector.error(new ModelASTKey(e.keyExpression),
                                        Messages.ModelParser_ExpectedClosureOrFailFast())
                                } else {
                                    parallelArgs[parseStringLiteral(e.keyExpression)] = value;
                                }
                            }
                        }
                    }
                }
                return new ParallelMatch(whole, parallelArgs, failFast);
            }
        }

        return null;
    }
}
