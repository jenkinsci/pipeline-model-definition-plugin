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

import com.fasterxml.jackson.databind.JsonNode
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings
import org.jenkinsci.plugins.pipeline.modeldefinition.Messages
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.*
import org.jenkinsci.plugins.pipeline.modeldefinition.ModelStepLoader
import com.github.fge.jsonschema.exceptions.JsonReferenceException
import com.github.fge.jsonschema.exceptions.ProcessingException
import com.github.fge.jsonschema.jsonpointer.JsonPointer
import com.github.fge.jsonschema.report.ProcessingMessage
import com.github.fge.jsonschema.report.ProcessingReport
import com.github.fge.jsonschema.tree.JsonTree
import org.jenkinsci.plugins.pipeline.modeldefinition.validator.ErrorCollector
import org.jenkinsci.plugins.pipeline.modeldefinition.validator.JSONErrorCollector
import org.jenkinsci.plugins.pipeline.modeldefinition.validator.ModelValidator
import org.jenkinsci.plugins.pipeline.modeldefinition.validator.ModelValidatorImpl

import javax.annotation.CheckForNull
import javax.annotation.Nonnull

/**
 * Parses input JSON into a {@link ModelASTPipelineDef}.
 *
 * @author Andrew Bayer
 */
@SuppressFBWarnings(value="SE_NO_SERIALVERSIONID")
class JSONParser implements Parser {
    ErrorCollector errorCollector

    ModelValidator validator

    JsonTree jsonTree

    private GroovyShell testShell

    JSONParser(JsonTree tree) {
        this.jsonTree = tree
        this.errorCollector = new JSONErrorCollector()
        this.validator = new ModelValidatorImpl(this.errorCollector)
        this.testShell = new GroovyShell()
    }

    @CheckForNull ModelASTPipelineDef parse() {
        return parse(jsonTree)
    }

    @CheckForNull ModelASTPipelineDef parse(JsonTree json) {
        ModelASTPipelineDef pipelineDef = new ModelASTPipelineDef(json)

        try {
            ProcessingReport schemaReport = Converter.validateJSONAgainstSchema(json.baseNode)
            if (!schemaReport.isSuccess()) {
                schemaReport.each { pm ->
                    errorCollector.error(new ModelASTPipelineDef(treeFromProcessingMessage(json, pm)),
                        processingMessageToError(pm))
                }
                return pipelineDef
            }
        } catch (ProcessingException e) {
            errorCollector.error(pipelineDef, e.message)
            return pipelineDef
        }

        def sectionsSeen = new HashSet()

        JsonTree pipelineJson = json.append(JsonPointer.of(ModelStepLoader.STEP_NAME))
        if (pipelineJson.node.isObject()) {
            pipelineJson.node.fields().collectEntries { [(it.key): it.value] }.each { sectionName, sectionContent ->
                ModelASTKey placeholderForErrors = new ModelASTKey(pipelineJson.append(JsonPointer.of(sectionName)))

                if (!sectionsSeen.add(sectionName)) {
                    errorCollector.error(placeholderForErrors, Messages.Parser_MultipleOfSection(sectionName))
                }

                switch (sectionName) {
                    case 'stages':
                        pipelineDef.stages = parseStages(pipelineJson.append(JsonPointer.of("stages")))
                        break
                    case 'environment':
                        pipelineDef.environment = parseEnvironment(pipelineJson.append(JsonPointer.of("environment")))
                        break
                    case 'agent':
                        pipelineDef.agent = parseAgent(pipelineJson.append(JsonPointer.of("agent")))
                        break
                    case 'post':
                        pipelineDef.postBuild = parsePostBuild(pipelineJson.append(JsonPointer.of("post")))
                        break
                    case 'tools':
                        pipelineDef.tools = parseTools(pipelineJson.append(JsonPointer.of("tools")))
                        break
                    case 'options':
                        pipelineDef.options = parseOptions(pipelineJson.append(JsonPointer.of("options")))
                        break
                    case 'triggers':
                        pipelineDef.triggers = parseTriggers(pipelineJson.append(JsonPointer.of("triggers")))
                        break
                    case 'parameters':
                        pipelineDef.parameters = parseBuildParameters(pipelineJson.append(JsonPointer.of("parameters")))
                        break
                    case 'libraries':
                        pipelineDef.libraries = parseLibraries(pipelineJson.append(JsonPointer.of("libraries")))
                        break
                    default:
                        errorCollector.error(placeholderForErrors, Messages.Parser_UndefinedSection(sectionName))
                }
            }

            pipelineDef.validate(validator)

        } else {
            errorCollector.error(pipelineDef, Messages.JSONParser_MissingPipelineRoot())
        }


        return pipelineDef
    }

    @CheckForNull ModelASTStages parseStages(JsonTree j) {
        ModelASTStages stages = new ModelASTStages(j)

        j.node.eachWithIndex { JsonNode entry, int i ->
            stages.stages.add(parseStage(j.append(JsonPointer.of(i))))
        }

        return stages
    }

    @CheckForNull ModelASTStage parseStage(JsonTree j) {
        ModelASTStage stage = new ModelASTStage(j)

        stage.name = j.node.get("name").asText()
        if (j.node.has("agent")) {
            stage.agent = parseAgent(j.append(JsonPointer.of("agent")))
        }
        if (j.node.has("parallel")) {
            JsonTree content = j.append(JsonPointer.of("parallel"))
            content?.node?.eachWithIndex{ JsonNode entry, int i ->
                stage.parallelContent.add(parseStage(content.append(JsonPointer.of(i))))
            }
        }

        JsonTree branches = j.append(JsonPointer.of("branches"))
        branches?.node?.eachWithIndex { JsonNode entry, int i ->
            stage.branches.add(parseBranch(branches.append(JsonPointer.of(i))))
        }

        if (j.node.has("stages")) {
            stage.stages = parseStages(j.append(JsonPointer.of("stages")))
        }
        if (j.node.has("failFast") && (stage.branches.size() > 1 || j.node.has("parallel")))  {
            stage.failFast = j.node.get("failFast")?.asBoolean()
        }

        if (j.node.has("options")) {
            stage.options = parseOptions(j.append(JsonPointer.of("options")))
            stage.options.inStage = true
        }

        if (j.node.has("input")) {
            stage.input = parseInput(j.append(JsonPointer.of("input")))
        }

        if (j.node.has("environment")) {
            stage.environment = parseEnvironment(j.append(JsonPointer.of("environment")))
        }

        if (j.node.has("tools")) {
            stage.tools = parseTools(j.append(JsonPointer.of("tools")))
        }

        if (j.node.hasNonNull("post")) {
            stage.post = parsePostStage(j.append(JsonPointer.of("post")))
        }

        if (j.node.hasNonNull("when")) {
            stage.when = parseWhen(j.append(JsonPointer.of("when")))
        }
        return stage

    }

    @CheckForNull ModelASTStageInput parseInput(JsonTree j) {
        ModelASTStageInput input = new ModelASTStageInput(j)

        if (j.node.has("message")) {
            input.message = parseValue(j.append(JsonPointer.of("message")))
        }
        if (j.node.has("id")) {
            input.id = parseValue(j.append(JsonPointer.of("id")))
        }
        if (j.node.has("ok")) {
            input.id = parseValue(j.append(JsonPointer.of("ok")))
        }
        if (j.node.has("submitter")) {
            input.submitter = parseValue(j.append(JsonPointer.of("submitter")))
        }
        if (j.node.has("submitterParameter")) {
            input.submitterParameter = parseValue(j.append(JsonPointer.of("submitterParameter")))
        }
        if (j.node.has("parameters")) {
            ModelASTBuildParameters p = parseBuildParameters(j.append(JsonPointer.of("parameters")))
            if (p != null) {
                input.parameters.addAll(p.parameters)
            }
        }
        return input
    }

    @CheckForNull ModelASTBranch parseBranch(JsonTree j) {
        ModelASTBranch branch = new ModelASTBranch(j)
        branch.name = j.node.get("name").asText()

        JsonTree steps = j.append(JsonPointer.of("steps"))
        steps.node.eachWithIndex { JsonNode entry, int i ->
            branch.steps.add(parseStep(steps.append(JsonPointer.of(i))))
        }

        return branch
    }

    @CheckForNull ModelASTWhen parseWhen(JsonTree j) {
        ModelASTWhen when = new ModelASTWhen(j)

        if (j.node.has("beforeAgent")) {
            when.beforeAgent = j.node.get("beforeAgent")?.asBoolean()
        }

        JsonTree conditionsTree = j.append(JsonPointer.of("conditions"))
        conditionsTree.node.eachWithIndex { JsonNode entry, int i ->
            JsonTree condTree = conditionsTree.append(JsonPointer.of(i))
            when.conditions.add(parseWhenContent(condTree))
        }

        return when
    }

    @CheckForNull ModelASTLibraries parseLibraries(JsonTree j) {
        ModelASTLibraries l = new ModelASTLibraries(j)

        JsonTree libsTree = j.append(JsonPointer.of("libraries"))
        libsTree.node.eachWithIndex { JsonNode entry, int i ->
            JsonTree thisNode = libsTree.append(JsonPointer.of(i))
            l.libs.add(ModelASTValue.fromConstant(thisNode.node.asText(), thisNode))
        }

        return l
    }

    @CheckForNull ModelASTOptions parseOptions(JsonTree j) {
        ModelASTOptions options = new ModelASTOptions(j)

        JsonTree optionsTree = j.append(JsonPointer.of("options"))
        optionsTree.node.eachWithIndex { JsonNode entry, int i ->
            JsonTree optTree = optionsTree.append(JsonPointer.of(i))
            ModelASTOption opt = new ModelASTOption(optTree)
            ModelASTMethodCall m = parseMethodCall(optTree)
            opt.args = m.args
            opt.name = m.name
            options.options.add(opt)
        }

        return options
    }

    @CheckForNull ModelASTTriggers parseTriggers(JsonTree j) {
        ModelASTTriggers triggers = new ModelASTTriggers(j)

        JsonTree triggersTree = j.append(JsonPointer.of("triggers"))
        triggersTree.node.eachWithIndex { JsonNode entry, int i ->
            JsonTree trigTree = triggersTree.append(JsonPointer.of(i))
            ModelASTTrigger t = new ModelASTTrigger(trigTree)
            ModelASTMethodCall m = parseMethodCall(trigTree)
            t.args = m.args
            t.name = m.name
            triggers.triggers.add(t)
        }

        return triggers
    }

    @CheckForNull ModelASTBuildParameters parseBuildParameters(JsonTree j) {
        ModelASTBuildParameters params = new ModelASTBuildParameters(j)

        JsonTree paramsTree = j.append(JsonPointer.of("parameters"))
        paramsTree.node.eachWithIndex { JsonNode entry, int i ->
            JsonTree pTree = paramsTree.append(JsonPointer.of(i))
            ModelASTBuildParameter b = new ModelASTBuildParameter(pTree)
            ModelASTMethodCall m = parseMethodCall(pTree)
            b.args = m.args
            b.name = m.name
            params.parameters.add(b)
        }

        return params
    }

    @CheckForNull parseKeyValueOrMethodCallPair(JsonTree j) {
        ModelASTKeyValueOrMethodCallPair pair = new ModelASTKeyValueOrMethodCallPair(j)

        // Passing the whole thing to parseKey to capture the JSONObject the "key" is in.
        pair.key = parseKey(j.append(JsonPointer.of("key")))

        JsonTree v = j.append(JsonPointer.of("value"))

        if (v.node.has("name") && v.node.has("arguments")) {
            // This is a method call
            pair.value = parseMethodCall(v)
        } else if (v.node.has("isLiteral") && v.node.has("value")) {
            // This is a single argument
            pair.value = parseValue(v)
        } else {
            errorCollector.error(pair, Messages.JSONParser_InvalidValueType())
        }

        return pair
    }

    @CheckForNull ModelASTMethodCall parseMethodCall(JsonTree o) {
        ModelASTMethodCall meth = new ModelASTMethodCall(o)

        if (o.node.isObject()) {
            meth.name = o.node.get("name").asText()
            if (o.node.has("arguments") && o.node.get("arguments").isArray()) {
                JsonTree args = o.append(JsonPointer.of("arguments"))
                args.node.eachWithIndex { JsonNode entry, int i ->
                    if (entry.isObject()) {
                        JsonTree argTree = args.append(JsonPointer.of(i))
                        ModelASTMethodArg arg
                        if (entry.has("key") && entry.has("value")) {
                            // This is a key/value pair
                            arg = parseKeyValueOrMethodCallPair(argTree)
                        } else if (entry.has("name") && entry.has("arguments")) {
                            // This is a method call
                            arg = parseMethodCall(argTree)
                        } else if (entry.has("isLiteral") && entry.has("value")) {
                            // This is a single argument
                            arg = parseValue(argTree)
                        } else {
                            errorCollector.error(meth, Messages.JSONParser_InvalidArgumentSyntax())
                        }
                        if (arg != null) {
                            meth.args << arg
                        }
                    } else {
                        errorCollector.error(meth, Messages.JSONParser_MethArgsMustBeObj())
                    }
                }
            } else {
                errorCollector.error(meth, Messages.JSONParser_MethArgsMissing())
            }
        } else {
            errorCollector.error(meth, Messages.JSONParser_MethCallMustBeObj())
        }

        return meth
    }

    @CheckForNull ModelASTInternalFunctionCall parseInternalFunctionCall(JsonTree o) {
        ModelASTInternalFunctionCall func = new ModelASTInternalFunctionCall(o)

        if (o.node.isObject()) {
            func.name = o.node.get("name").asText()
            if (o.node.has("arguments") && o.node.get("arguments").isArray()) {
                JsonTree args = o.append(JsonPointer.of("arguments"))
                args.node.eachWithIndex { JsonNode entry, int i ->
                    if (entry.isObject()) {
                        JsonTree argTree = args.append(JsonPointer.of(i))
                        ModelASTMethodArg arg
                        if (entry.has("isLiteral") && entry.has("value")) {
                            // This is a single argument
                            arg = parseValue(argTree)
                        } else {
                            errorCollector.error(func, Messages.JSONParser_InvalidArgumentSyntax())
                        }
                        if (arg != null) {
                            func.args << arg
                        }
                    } else {
                        errorCollector.error(func, Messages.JSONParser_MethArgsMustBeObj())
                    }
                }
            } else {
                errorCollector.error(func, Messages.JSONParser_MethArgsMissing())
            }
        } else {
            errorCollector.error(func, Messages.JSONParser_MethCallMustBeObj())
        }

        return func
    }

    @CheckForNull ModelASTStep parseStep(JsonTree j) {
        if (j.node.has("children")) {
            return parseTreeStep(j)
        } else if (j.node.get("name")?.asText() == "script") {
            return parseScriptBlock(j)
        } else {
            ModelASTStep step = new ModelASTStep(j)
            step.name = j.node.get("name").asText()
            step.args = parseArgumentList(j.append(JsonPointer.of("arguments")))

            return step
        }
    }

    @CheckForNull ModelASTWhenContent parseWhenContent(JsonTree j) {
        if (j.node.has("children")) {
            ModelASTWhenCondition condition = new ModelASTWhenCondition(j)
            condition.name = j.node.get("name").asText()
            if (j.node.has("arguments")) {
                condition.args = parseArgumentList(j.append(JsonPointer.of("arguments")))
            }
            JsonTree children = j.append(JsonPointer.of("children"))
            children.node.eachWithIndex { JsonNode entry, int i ->
                condition.children.add(parseWhenContent(children.append(JsonPointer.of(i))))
            }
            return condition
        } else if (j.node.get("name")?.asText() == "expression") {
            return parseWhenExpression(j)
        } else {
            ModelASTWhenCondition condition = new ModelASTWhenCondition(j)
            condition.name = j.node.get("name").asText()
            condition.args = parseArgumentList(j.append(JsonPointer.of("arguments")))

            return condition
        }
    }

    @CheckForNull ModelASTArgumentList parseArgumentList(JsonTree o) {
        ModelASTArgumentList argList
        if (o.node.isArray()) {

            if (o.node.size() == 0) {
                argList = new ModelASTNamedArgumentList(o)
            } else {
                JsonNode firstElem = o.node.get(0)
                // If this is true, then we've got named parameters.
                if (firstElem != null && firstElem.size() == 2 && firstElem.has("key") && firstElem.has("value")) {
                    argList = new ModelASTNamedArgumentList(o)
                    o.node.eachWithIndex { JsonNode entry, int i ->
                        JsonTree entryTree = o.append(JsonPointer.of(i))
                        // Passing the whole thing to parseKey to capture the JSONObject the "key" is in.
                        ModelASTKey key = parseKey(entryTree.append(JsonPointer.of("key")))

                        ModelASTValue value = parseValue(entryTree.append(JsonPointer.of("value")))

                        ((ModelASTNamedArgumentList) argList).arguments.put(key, value)
                    }
                }
                // Otherwise, we've got positional parameters.
                else {
                    argList = new ModelASTPositionalArgumentList(o)
                    o.node.eachWithIndex { JsonNode entry, int i ->
                        ModelASTValue value = parseValue(o.append(JsonPointer.of(i)))
                        ((ModelASTPositionalArgumentList)argList).arguments.add(value)
                    }
                }
            }
        } else if (o.node.isObject()) {
            argList = new ModelASTSingleArgument(o)
            ((ModelASTSingleArgument) argList).value = parseValue(o)
        } else if (o.node.isTextual()) {
            argList = new ModelASTSingleArgument(o)
            ((ModelASTSingleArgument) argList).value = ModelASTValue.fromConstant(o.node.asText(), o)
        } else if (o.node == null) {
            // No arguments.
            argList = new ModelASTNamedArgumentList(null)
        } else {
            argList = new ModelASTNamedArgumentList(o)
            errorCollector.error(argList, Messages.JSONParser_ObjNotJSON(o))

        }

        return argList
    }

    @CheckForNull ModelASTKey parseKey(JsonTree o) {
        ModelASTKey key = new ModelASTKey(o)

        key.key = o.node?.asText()
        return key
    }

    @CheckForNull ModelASTValue parseValue(JsonTree o) {
        ModelASTValue val = null
        if (o.node.get("isLiteral").asBoolean()) {
            if (o.node.get("value").isBoolean()) {
                val = ModelASTValue.fromConstant(o.node.get("value").booleanValue(), o)
            } else if (o.node.get("value").isNumber()) {
                val =  ModelASTValue.fromConstant(o.node.get("value").numberValue(), o)
            } else {
                val = ModelASTValue.fromConstant(o.node.get("value").textValue(), o)
                if (val.getValue() != null) {
                    try {
                        testShell.parse(val.toGroovy())
                    } catch (_) {
                        errorCollector.error(val, Messages.JSONParser_InvalidGroovyString(val.getValue()))
                    }
                }
            }
        } else {
            val = ModelASTValue.fromGString(o.node.get("value").textValue(), o)
            String valToGroovy = val.toGroovy()
            // Make sure we don't allow ${whatever} without being in quotes, since that's actually going to translate as
            // $() { whatever } which is...not what we wanted.
            if (valToGroovy.startsWith('${')) {
                errorCollector.error(val, Messages.ModelParser_BareDollarCurly(valToGroovy))
            }
        }

        return val
    }

    @CheckForNull ModelASTScriptBlock parseScriptBlock(JsonTree j) {
        ModelASTScriptBlock scriptBlock = new ModelASTScriptBlock(j)
        scriptBlock.args = parseArgumentList(j.append(JsonPointer.of("arguments")))

        return scriptBlock
    }

    @CheckForNull ModelASTWhenExpression parseWhenExpression(JsonTree j) {
        ModelASTWhenExpression scriptBlock = new ModelASTWhenExpression(j)
        scriptBlock.args = parseArgumentList(j.append(JsonPointer.of("arguments")))

        return scriptBlock
    }

    @CheckForNull ModelASTTreeStep parseTreeStep(JsonTree j) {
        ModelASTTreeStep step = new ModelASTTreeStep(j)
        step.name = j.node.get("name").asText()
        step.args = parseArgumentList(j.append(JsonPointer.of("arguments")))

        JsonTree children = j.append(JsonPointer.of("children"))
        children.node.eachWithIndex { JsonNode entry, int i ->
            step.children.add(parseStep(children.append(JsonPointer.of(i))))
        }

        return step
    }

    @CheckForNull ModelASTBuildCondition parseBuildCondition(JsonTree j) {
        ModelASTBuildCondition condition = new ModelASTBuildCondition(j)

        condition.condition = j.node.get("condition").asText()
        condition.branch = parseBranch(j.append(JsonPointer.of("branch")))

        return condition
    }

    @CheckForNull ModelASTPostBuild parsePostBuild(JsonTree j) {
        ModelASTPostBuild postBuild = new ModelASTPostBuild(j)
        return parseBuildConditionResponder(j, postBuild)
    }

    @CheckForNull ModelASTPostStage parsePostStage(JsonTree j) {
        ModelASTPostStage post = new ModelASTPostStage(j)
        return parseBuildConditionResponder(j, post)
    }

    @Nonnull
    <R extends ModelASTBuildConditionsContainer> R parseBuildConditionResponder(JsonTree j, R responder) {
        JsonTree conds = j.append(JsonPointer.of("conditions"))
        conds.node.eachWithIndex { JsonNode entry, int i ->
            responder.conditions.add(parseBuildCondition(conds.append(JsonPointer.of(i))))
        }
        return responder
    }

    @CheckForNull ModelASTEnvironment parseEnvironment(JsonTree j) {
        ModelASTEnvironment environment = new ModelASTEnvironment(j)

        j.node.eachWithIndex { JsonNode entry, int i ->
            JsonTree entryTree = j.append(JsonPointer.of(i))
            // Passing the whole thing to parseKey to capture the JSONObject the "key" is in.
            ModelASTKey key = parseKey(entryTree.append(JsonPointer.of("key")))

            JsonTree valTree = entryTree.append(JsonPointer.of("value"))
            if (valTree.node.isObject()) {
                if (valTree.node.has("name") && valTree.node.has("arguments")) {
                    // This is an internal function call
                    environment.variables.put(key, parseInternalFunctionCall(valTree))
                } else if (valTree.node.has("isLiteral") && valTree.node.has("value")) {
                    // This is a single argument
                    environment.variables.put(key, parseValue(valTree))
                } else {
                    errorCollector.error(key, Messages.JSONParser_InvalidValueType())
                }
            }
        }
        return environment
    }

    @CheckForNull ModelASTTools parseTools(JsonTree j) {
        ModelASTTools tools = new ModelASTTools(j)

        j.node.eachWithIndex { JsonNode entry, int i ->
            JsonTree entryTree = j.append(JsonPointer.of(i))
            // Passing the whole thing to parseKey to capture the JSONObject the "key" is in.
            ModelASTKey key = parseKey(entryTree.append(JsonPointer.of("key")))

            ModelASTValue value = parseValue(entryTree.append(JsonPointer.of("value")))

            tools.tools.put(key, value)
        }
        return tools
    }

    @CheckForNull ModelASTClosureMap parseClosureMap(JsonTree j) {
        ModelASTClosureMap map = new ModelASTClosureMap(j)

        j.node.eachWithIndex { JsonNode entry, int i ->
            JsonTree entryTree = j.append(JsonPointer.of(i))
            // Passing the whole thing to parseKey to capture the JSONObject the "key" is in.
            ModelASTKey key = parseKey(entryTree.append(JsonPointer.of("key")))

            JsonNode val = entry.get("value")
            if (val?.isArray()) {
                map.variables[key] = parseClosureMap(entryTree.append(JsonPointer.of("value")))
            } else if (val?.isObject() && val.has("isLiteral") && val.has("value")) {
                // This is a single argument
                map.variables[key] = parseValue(entryTree.append(JsonPointer.of("value")))
            } else {
                errorCollector.error(key, Messages.JSONParser_InvalidArgumentSyntax())
            }
        }

        return map
    }

    @CheckForNull ModelASTAgent parseAgent(JsonTree j) {
        ModelASTAgent agent = new ModelASTAgent(j)

        agent.agentType = new ModelASTKey(j.append(JsonPointer.of("type")))
        agent.agentType.key = j.node.get("type").asText()
        if (j.node.has("arguments") &&
            j.node.get("arguments").isArray() &&
            j.node.get("arguments").size() > 0) {
            agent.variables = parseClosureMap(j.append(JsonPointer.of("arguments")))
            // HACK FOR JENKINS-41118 to switch to "node" rather than "label" when multiple variable are set.
            if (agent.agentType.key == "label") {
                agent.agentType.key = "node"
            }
        } else if (j.node.has("argument") && j.node.get("argument").isObject()) {
            agent.variables = parseValue(j.append(JsonPointer.of("argument")))
        }

        return agent
    }

    private JsonTree treeFromProcessingMessage(JsonTree json, ProcessingMessage pm) {
        JsonNode jsonNode = pm.asJson()

        String location = jsonNode.get("instance").get("pointer").asText()

        try {
            return json.append(new JsonPointer(location))
        } catch (JsonReferenceException e) {
            return json
        }
    }

    private String processingMessageToError(ProcessingMessage pm) {
        JsonNode jsonNode = pm.asJson()

        if (jsonNode.has("keyword")) {
            if (jsonNode.get("keyword").asText() == "required") {
                String missingProps = jsonNode.get('missing').elements().collect { "'${it.asText()}'" }.join(", ")
                return Messages.JSONParser_MissingRequiredProperties(missingProps)
            } else if (jsonNode.get("keyword").asText() == "minItems") {
                return Messages.JSONParser_TooFewItems(jsonNode.get('found').asInt(), jsonNode.get('minItems').asInt())
            }
        }
        return pm.message
    }

}
