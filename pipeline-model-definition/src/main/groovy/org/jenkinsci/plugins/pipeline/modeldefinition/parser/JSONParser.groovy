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
import com.github.fge.jsonschema.exceptions.ProcessingException
import com.github.fge.jsonschema.report.ProcessingMessage
import com.github.fge.jsonschema.report.ProcessingReport
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings
import net.sf.json.JSONArray
import net.sf.json.JSONObject
import org.jenkinsci.plugins.pipeline.modeldefinition.Messages
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.*
import org.jenkinsci.plugins.pipeline.modeldefinition.ModelStepLoader
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

    JSONObject jsonObject

    public JSONParser(JSONObject j) {
        this.jsonObject = j
        this.errorCollector = new JSONErrorCollector()
        this.validator = new ModelValidatorImpl(this.errorCollector)
    }

    public @CheckForNull ModelASTPipelineDef parse() {
        return parse(jsonObject);
    }

    public @CheckForNull ModelASTPipelineDef parse(JSONObject json) {
        ModelASTPipelineDef pipelineDef = new ModelASTPipelineDef(json)

        try {
            ProcessingReport schemaReport = Converter.validateJSONAgainstSchema(json)
            if (!schemaReport.isSuccess()) {
                schemaReport.each { pm ->
                    errorCollector.error(pipelineDef, processingMessageToError(pm))
                }
                return pipelineDef
            }
        } catch (ProcessingException e) {
            errorCollector.error(pipelineDef, e.message)
            return pipelineDef
        }

        def sectionsSeen = new HashSet()

        JSONObject pipelineJson = jsonObject.getJSONObject(ModelStepLoader.STEP_NAME)
        pipelineJson.keySet().each { sectionName ->
            if (!sectionsSeen.add(sectionName)) {
                errorCollector.error(pipelineDef, Messages.Parser_MultipleOfSection(sectionName))
            }

            switch (sectionName) {
                case 'stages':
                    pipelineDef.stages = parseStages(pipelineJson.getJSONArray("stages"))
                    break
                case 'environment':
                    pipelineDef.environment = parseEnvironment(pipelineJson.getJSONArray("environment"))
                    break
                case 'agent':
                    pipelineDef.agent = parseAgent(pipelineJson.get("agent"))
                    break
                case 'post':
                    pipelineDef.postBuild = parsePostBuild(pipelineJson.getJSONObject("post"))
                    break
                case 'tools':
                    pipelineDef.tools = parseTools(pipelineJson.getJSONArray("tools"))
                    break
                case 'options':
                    pipelineDef.options = parseOptions(pipelineJson.getJSONObject("options"))
                    break
                case 'triggers':
                    pipelineDef.triggers = parseTriggers(pipelineJson.getJSONObject("triggers"))
                    break
                case 'parameters':
                    pipelineDef.parameters = parseBuildParameters(pipelineJson.getJSONObject("parameters"))
                    break
                default:
                    errorCollector.error(pipelineDef, Messages.Parser_UndefinedSection(sectionName))
            }
        }

        pipelineDef.validate(validator)

        return pipelineDef
    }


    public @CheckForNull ModelASTStages parseStages(JSONArray j) {
        ModelASTStages stages = new ModelASTStages(j)

        j.each { s ->
            JSONObject o = (JSONObject)s
            stages.stages.add(parseStage(o))
        }

        return stages
    }

    public @CheckForNull ModelASTStage parseStage(JSONObject j) {
        ModelASTStage stage = new ModelASTStage(j)

        stage.name = j.getString("name")

        if (j.has("agent")) {
            stage.agent = parseAgent(j.get("agent"))
        }

        j.getJSONArray("branches").each { b ->
            JSONObject o = (JSONObject)b
            stage.branches.add(parseBranch(o))
        }

        if (j.has("failFast") && stage.branches.size() > 1) {
            stage.failFast = j.getBoolean("failFast")
        }

        if (j.has("environment")) {
            stage.environment = parseEnvironment(j.getJSONArray("environment"))
        }

        if (j.has("tools")) {
            stage.tools = parseTools(j.getJSONArray("tools"))
        }

        if (j.has("post")) {
            def object = j.getJSONObject("post")
            if (!object.isNullObject()) {
                stage.post = parsePostStage(object)
            }
        }

        if (j.has("when")) {
            def object = j.getJSONObject("when")
            if (!object.isNullObject()) {
                stage.when = parseWhen(object)
            }
        }
        return stage

    }

    public @CheckForNull ModelASTBranch parseBranch(JSONObject j) {
        ModelASTBranch branch = new ModelASTBranch(j)
        branch.name = j.getString("name")

        j.getJSONArray("steps").each { o ->
            JSONObject s = (JSONObject)o

            branch.steps.add(parseStep(s))
        }

        return branch
    }

    public @CheckForNull ModelASTWhen parseWhen(JSONObject j) {
        ModelASTWhen when = new ModelASTWhen(j)
        j.getJSONArray("conditions").each { o ->
            JSONObject s = (JSONObject)o
            when.conditions.add(parseStep(s))
        }
        return when
    }

    public @CheckForNull ModelASTOptions parseOptions(JSONObject j) {
        ModelASTOptions options = new ModelASTOptions(j)

        j.getJSONArray("options").each { p ->
            ModelASTOption opt = new ModelASTOption(p)
            ModelASTMethodCall m = parseMethodCall(p)
            opt.args = m.args
            opt.name = m.name
            options.options.add(opt)
        }

        return options
    }

    public @CheckForNull ModelASTTriggers parseTriggers(JSONObject j) {
        ModelASTTriggers triggers = new ModelASTTriggers(j)

        j.getJSONArray("triggers").each { p ->
            ModelASTTrigger t = new ModelASTTrigger(p)
            ModelASTMethodCall m = parseMethodCall(p)
            t.args = m.args
            t.name = m.name
            triggers.triggers.add(t)
        }

        return triggers
    }

    public @CheckForNull ModelASTBuildParameters parseBuildParameters(JSONObject j) {
        ModelASTBuildParameters params = new ModelASTBuildParameters(j)

        j.getJSONArray("parameters").each { p ->
            ModelASTBuildParameter b = new ModelASTBuildParameter(p)
            ModelASTMethodCall m = parseMethodCall(p)
            b.args = m.args
            b.name = m.name
            params.parameters.add(b)
        }

        return params
    }

    public @CheckForNull parseKeyValueOrMethodCallPair(JSONObject j) {
        ModelASTKeyValueOrMethodCallPair pair = new ModelASTKeyValueOrMethodCallPair(j)

        // Passing the whole thing to parseKey to capture the JSONObject the "key" is in.
        pair.key = parseKey(j)

        JSONObject v = j.getJSONObject("value")

        if (v.has("name") && v.has("arguments")) {
            // This is a method call
            pair.value = parseMethodCall(v)
        } else if (v.has("isLiteral") && v.has("value")) {
            // This is a single argument
            pair.value = parseValue(v)
        } else {
            errorCollector.error(pair, Messages.JSONParser_InvalidValueType())
        }

        return pair
    }

    public @CheckForNull ModelASTMethodCall parseMethodCall(Object o) {
        ModelASTMethodCall meth = new ModelASTMethodCall(o)
        if (o instanceof JSONObject) {
            meth.name = o.getString("name")
            if (o.has("arguments") && o.get("arguments") instanceof JSONArray) {
                JSONArray args = o.getJSONArray("arguments")
                args.each { rawEntry ->
                    if (rawEntry instanceof JSONObject) {
                        JSONObject entry = (JSONObject) rawEntry
                        ModelASTMethodArg arg
                        if (entry.has("key") && entry.has("value")) {
                            // This is a key/value pair
                            arg = parseKeyValueOrMethodCallPair(entry)
                        } else if (entry.has("name") && entry.has("arguments")) {
                            // This is a method call
                            arg = parseMethodCall(entry)
                        } else if (entry.has("isLiteral") && entry.has("value")) {
                            // This is a single argument
                            arg = parseValue(entry)
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

    public @CheckForNull ModelASTStep parseStep(JSONObject j) {
        if (j.containsKey("children")) {
            return parseTreeStep(j)
        } else if (j.getString("name") == "script") {
            return parseScriptBlock(j)
        } else if (j.getString("name") == "expression") {
            return parseWhenExpression(j)
        } else {
            ModelASTStep step = new ModelASTStep(j)
            step.name = j.getString("name")
            step.args = parseArgumentList(j.get("arguments"))

            return step
        }
    }

    public @CheckForNull ModelASTArgumentList parseArgumentList(Object o) {
        ModelASTArgumentList argList
        if (o instanceof JSONArray) {

            if (o.isEmpty()) {
                argList = new ModelASTNamedArgumentList(o)
            } else {
                JSONObject firstElem = o.getJSONObject(0)
                // If this is true, then we've got named parameters.
                if (firstElem != null && firstElem.size() == 2 && firstElem.has("key") && firstElem.has("value")) {
                    argList = new ModelASTNamedArgumentList(o)
                    o.each { rawEntry ->
                        JSONObject entry = (JSONObject) rawEntry
                        // Passing the whole thing to parseKey to capture the JSONObject the "key" is in.
                        ModelASTKey key = parseKey(entry)

                        ModelASTValue value = parseValue(entry.getJSONObject("value"))

                        ((ModelASTNamedArgumentList) argList).arguments.put(key, value)
                    }
                }
                // Otherwise, we've got positional parameters.
                else {
                    argList = new ModelASTPositionalArgumentList(o)
                    o.each { rawValue ->
                        ModelASTValue value = parseValue((JSONObject) rawValue)
                        ((ModelASTPositionalArgumentList)argList).arguments.add(value)
                    }
                }
            }
        } else if (o instanceof JSONObject) {
            argList = new ModelASTSingleArgument(o)
            ((ModelASTSingleArgument) argList).value = parseValue(o)
        } else if (o instanceof String) {
            argList = new ModelASTSingleArgument(o)
            ((ModelASTSingleArgument) argList).value = ModelASTValue.fromConstant(o, o)
        } else if (o == null) {
            // No arguments.
            argList = new ModelASTNamedArgumentList(null)
        } else {
            argList = new ModelASTNamedArgumentList(o)
            errorCollector.error(argList, Messages.JSONParser_ObjNotJSON(o))

        }

        return argList
    }

    public @CheckForNull ModelASTKey parseKey(JSONObject o) {
        ModelASTKey key = new ModelASTKey(o)

        key.key = o.getString("key")
        return key
    }

    public @CheckForNull ModelASTValue parseValue(JSONObject o) {
        if (o.getBoolean("isLiteral")) {
            return ModelASTValue.fromConstant(o.get("value"), o)
        } else {
            return ModelASTValue.fromGString(o.getString("value"), o)
        }
    }

    public @CheckForNull ModelASTScriptBlock parseScriptBlock(JSONObject j) {
        ModelASTScriptBlock scriptBlock = new ModelASTScriptBlock(j)
        scriptBlock.args = parseArgumentList(j.getJSONObject("arguments"))

        return scriptBlock
    }

    public @CheckForNull ModelASTWhenExpression parseWhenExpression(JSONObject j) {
        ModelASTWhenExpression scriptBlock = new ModelASTWhenExpression(j)
        scriptBlock.args = parseArgumentList(j.getJSONObject("arguments"))

        return scriptBlock
    }

    public @CheckForNull ModelASTTreeStep parseTreeStep(JSONObject j) {
        ModelASTTreeStep step = new ModelASTTreeStep(j)
        step.name = j.getString("name")
        step.args = parseArgumentList(j.get("arguments"))

        j.getJSONArray("children").each { o ->
            JSONObject c = (JSONObject)o
            step.children.add(parseStep(c))
        }

        return step
    }

    public @CheckForNull ModelASTBuildCondition parseBuildCondition(JSONObject j) {
        ModelASTBuildCondition condition = new ModelASTBuildCondition(j)

        condition.condition = j.getString("condition")
        condition.branch = parseBranch(j.getJSONObject("branch"))

        return condition
    }

    public @CheckForNull ModelASTPostBuild parsePostBuild(JSONObject j) {
        ModelASTPostBuild postBuild = new ModelASTPostBuild(j)
        return parseBuildConditionResponder(j, postBuild)
    }

    public @CheckForNull ModelASTPostStage parsePostStage(JSONObject j) {
        ModelASTPostStage post = new ModelASTPostStage(j)
        return parseBuildConditionResponder(j, post)
    }

    @Nonnull
    public <R extends ModelASTBuildConditionsContainer> R parseBuildConditionResponder(JSONObject j, R responder) {
        j.getJSONArray("conditions").each { o ->
            JSONObject conditionBlock = (JSONObject) o
            responder.conditions.add(parseBuildCondition(conditionBlock))
        }
        return responder
    }

    public @CheckForNull ModelASTEnvironment parseEnvironment(JSONArray j) {
        ModelASTEnvironment environment = new ModelASTEnvironment(j)

        j.each { rawEntry ->
            JSONObject entry = (JSONObject) rawEntry
            // Passing the whole thing to parseKey to capture the JSONObject the "key" is in.
            ModelASTKey key = parseKey(entry)

            ModelASTValue value = parseValue(entry.getJSONObject("value"))

            environment.variables.put(key, value)
        }
        return environment
    }

    public @CheckForNull ModelASTTools parseTools(JSONArray j) {
        ModelASTTools tools = new ModelASTTools(j)

        j.each { rawEntry ->
            JSONObject entry = (JSONObject) rawEntry
            // Passing the whole thing to parseKey to capture the JSONObject the "key" is in.
            ModelASTKey key = parseKey(entry)

            ModelASTValue value = parseValue(entry.getJSONObject("value"))

            tools.tools.put(key, value)
        }
        return tools
    }

    public @CheckForNull ModelASTAgent parseAgent(Object j) {
        ModelASTAgent agent = new ModelASTAgent(j)

        agent.args = parseArgumentList(j)

        return agent
    }

    private String processingMessageToError(ProcessingMessage pm) {
        JsonNode jsonNode = pm.asJson()

        String location = jsonNode.get("instance").get("pointer").asText()
        if (jsonNode.has("keyword")) {
            if (jsonNode.get("keyword").asText().equals("required")) {
                String missingProps = jsonNode.get('missing').elements().collect { "'${it.asText()}'" }.join(", ")
                return Messages.JSONParser_MissingRequiredProperties(location, missingProps)
            } else if (jsonNode.get("keyword").asText().equals("minItems")) {
                return Messages.JSONParser_TooFewItems(location, jsonNode.get('found').asInt(), jsonNode.get('minItems').asInt())
            }
        }
        return Messages.JSONParser_ProcessingError(location, pm.message)
    }

}
