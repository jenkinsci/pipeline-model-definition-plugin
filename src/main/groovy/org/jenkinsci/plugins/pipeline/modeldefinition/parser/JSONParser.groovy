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
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTArgumentList
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTBranch
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTEnvironment
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTKey
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTNamedArgumentList
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTPositionalArgumentList
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTSingleArgument
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTStage
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTStages
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTStep
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTValue
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTBuildCondition
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTAgent
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTNotifications
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTPipelineDef
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTPostBuild
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTScriptBlock
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTTools
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTTreeStep
import org.jenkinsci.plugins.pipeline.modeldefinition.steps.ModelInterpreterStep
import org.jenkinsci.plugins.pipeline.modeldefinition.validator.ErrorCollector
import org.jenkinsci.plugins.pipeline.modeldefinition.validator.JSONErrorCollector
import org.jenkinsci.plugins.pipeline.modeldefinition.validator.ModelValidator
import org.jenkinsci.plugins.workflow.steps.StepDescriptor

import javax.annotation.CheckForNull

/**
 * Parses input JSON into a {@link ModelASTPipelineDef}.
 *
 * @author Andrew Bayer
 */
@SuppressFBWarnings(value="SE_NO_SERIALVERSIONID")
class JSONParser {
    ErrorCollector errorCollector

    ModelValidator validator

    JSONObject jsonObject

    public JSONParser(JSONObject j) {
        this.jsonObject = j
        this.errorCollector = new JSONErrorCollector()
        this.validator = new ModelValidator(this.errorCollector)
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

        String stepName = StepDescriptor.all().find { it instanceof ModelInterpreterStep.DescriptorImpl }?.functionName

        JSONObject pipelineJson = jsonObject.getJSONObject(stepName)
        pipelineJson.keySet().each { sectionName ->
            if (!sectionsSeen.add(sectionName)) {
                errorCollector.error(pipelineDef, "Multiple occurences of the ${sectionName} section")
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
                case 'postBuild':
                    pipelineDef.postBuild = parsePostBuild(pipelineJson.getJSONObject("postBuild"))
                    break
                case 'notifications':
                    pipelineDef.notifications = parseNotifications(pipelineJson.getJSONObject("notifications"))
                    break
                case 'tools':
                    pipelineDef.tools = parseTools(pipelineJson.getJSONArray("tools"))
                    break
                default:
                    errorCollector.error(pipelineDef, "Undefined section '${sectionName}'")
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

        j.getJSONArray("branches").each { b ->
            JSONObject o = (JSONObject)b
            stage.branches.add(parseBranch(o))
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

    public @CheckForNull ModelASTStep parseStep(JSONObject j) {
        if (j.containsKey("children")) {
            return parseTreeStep(j)
        } else if (j.getString("name").equals("script")) {
            return parseScriptBlock(j)
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
                errorCollector.error(argList, "Object ${o} is neither a JSONArray nor a JSONObject")

        }

        return argList
    }

    public @CheckForNull ModelASTKey parseKey(JSONObject o) {
        ModelASTKey key = new ModelASTKey(o)

        key.key = o.getString("key")
        return key
    }

    public @CheckForNull ModelASTValue parseValue(JSONObject o) {
        if (o.getBoolean("isConstant")) {
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

    public @CheckForNull ModelASTNotifications parseNotifications(JSONObject j) {
        ModelASTNotifications notifications = new ModelASTNotifications(j)

        j.getJSONArray("conditions").each { o ->
            JSONObject conditionBlock = (JSONObject) o
            notifications.conditions.add(parseBuildCondition(conditionBlock))
        }
        return notifications
    }

    public @CheckForNull ModelASTPostBuild parsePostBuild(JSONObject j) {
        ModelASTPostBuild postBuild = new ModelASTPostBuild(j)

        j.getJSONArray("conditions").each { o ->
            JSONObject conditionBlock = (JSONObject) o
            postBuild.conditions.add(parseBuildCondition(conditionBlock))
        }
        return postBuild
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
                return "At ${location}: Missing one or more required properties: ${missingProps}"
            } else if (jsonNode.get("keyword").asText().equals("minItems")) {
                return "At ${location}: Array has ${jsonNode.get('found').asInt()} entries, requires minimum of ${jsonNode.get('minItems').asInt()}"
            }
        }
        return "At ${location}: ${pm.message}"
    }

}
