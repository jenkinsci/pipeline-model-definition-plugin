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
package org.jenkinsci.plugins.pipeline.config.parser

import com.fasterxml.jackson.databind.JsonNode
import com.github.fge.jsonschema.exceptions.ProcessingException
import com.github.fge.jsonschema.report.ProcessingMessage
import com.github.fge.jsonschema.report.ProcessingReport
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings
import net.sf.json.JSONArray
import net.sf.json.JSONObject
import org.jenkinsci.plugins.pipeline.config.ConfigStepLoader
import org.jenkinsci.plugins.pipeline.config.ast.ConfigASTArgumentList
import org.jenkinsci.plugins.pipeline.config.ast.ConfigASTBranch
import org.jenkinsci.plugins.pipeline.config.ast.ConfigASTEnvironment
import org.jenkinsci.plugins.pipeline.config.ast.ConfigASTKey
import org.jenkinsci.plugins.pipeline.config.ast.ConfigASTNamedArgumentList
import org.jenkinsci.plugins.pipeline.config.ast.ConfigASTSingleArgument
import org.jenkinsci.plugins.pipeline.config.ast.ConfigASTStage
import org.jenkinsci.plugins.pipeline.config.ast.ConfigASTStages
import org.jenkinsci.plugins.pipeline.config.ast.ConfigASTStep
import org.jenkinsci.plugins.pipeline.config.ast.ConfigASTValue
import org.jenkinsci.plugins.pipeline.config.ast.ConfigASTBuildCondition
import org.jenkinsci.plugins.pipeline.config.ast.ConfigASTAgent
import org.jenkinsci.plugins.pipeline.config.ast.ConfigASTNotifications
import org.jenkinsci.plugins.pipeline.config.ast.ConfigASTPipelineDef
import org.jenkinsci.plugins.pipeline.config.ast.ConfigASTPostBuild
import org.jenkinsci.plugins.pipeline.config.ast.ConfigASTScriptBlock
import org.jenkinsci.plugins.pipeline.config.ast.ConfigASTTools
import org.jenkinsci.plugins.pipeline.config.ast.ConfigASTTreeStep
import org.jenkinsci.plugins.pipeline.config.validator.ErrorCollector
import org.jenkinsci.plugins.pipeline.config.validator.JSONErrorCollector
import org.jenkinsci.plugins.pipeline.config.validator.ConfigValidator

import javax.annotation.CheckForNull

/**
 * Parses input JSON into a {@link ConfigASTPipelineDef}.
 *
 * @author Andrew Bayer
 */
@SuppressFBWarnings(value="SE_NO_SERIALVERSIONID")
class JSONParser {
    ErrorCollector errorCollector

    ConfigValidator validator

    JSONObject jsonObject

    public JSONParser(JSONObject j) {
        this.jsonObject = j
        this.errorCollector = new JSONErrorCollector()
        this.validator = new ConfigValidator(this.errorCollector)
    }

    public @CheckForNull ConfigASTPipelineDef parse() {
        return parse(jsonObject);
    }

    public @CheckForNull ConfigASTPipelineDef parse(JSONObject json) {
        ConfigASTPipelineDef pipelineDef = new ConfigASTPipelineDef(json)

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

        JSONObject pipelineJson = jsonObject.getJSONObject(ConfigStepLoader.STEP_NAME)
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


    public @CheckForNull ConfigASTStages parseStages(JSONArray j) {
        ConfigASTStages stages = new ConfigASTStages(j)

        j.each { s ->
            JSONObject o = (JSONObject)s
            stages.stages.add(parseStage(o))
        }

        return stages
    }

    public @CheckForNull ConfigASTStage parseStage(JSONObject j) {
        ConfigASTStage stage = new ConfigASTStage(j)

        stage.name = j.getString("name")

        j.getJSONArray("branches").each { b ->
            JSONObject o = (JSONObject)b
            stage.branches.add(parseBranch(o))
        }
        return stage

    }

    public @CheckForNull ConfigASTBranch parseBranch(JSONObject j) {
        ConfigASTBranch branch = new ConfigASTBranch(j)
        branch.name = j.getString("name")

        j.getJSONArray("steps").each { o ->
            JSONObject s = (JSONObject)o

            branch.steps.add(parseStep(s))
        }

        return branch
    }

    public @CheckForNull ConfigASTStep parseStep(JSONObject j) {
        if (j.containsKey("children")) {
            return parseTreeStep(j)
        } else if (j.getString("name").equals("script")) {
            return parseScriptBlock(j)
        } else {
            ConfigASTStep step = new ConfigASTStep(j)
            step.name = j.getString("name")
            step.args = parseArgumentList(j.get("arguments"))

            return step
        }
    }

    public @CheckForNull ConfigASTArgumentList parseArgumentList(Object o) {
        ConfigASTArgumentList argList
        if (o instanceof JSONArray) {
            argList = new ConfigASTNamedArgumentList(o)

            o.each { rawEntry ->
                JSONObject entry = (JSONObject) rawEntry
                // Passing the whole thing to parseKey to capture the JSONObject the "key" is in.
                ConfigASTKey key = parseKey(entry)

                ConfigASTValue value = parseValue(entry.getJSONObject("value"))

                ((ConfigASTNamedArgumentList)argList).arguments.put(key, value)
            }
        } else if (o instanceof JSONObject) {
            argList = new ConfigASTSingleArgument(o)
            ((ConfigASTSingleArgument) argList).value = parseValue(o)
        } else if (o instanceof String) {
            argList = new ConfigASTSingleArgument(o)
            ((ConfigASTSingleArgument) argList).value = ConfigASTValue.fromConstant(o, o)
        } else if (o == null) {
            // No arguments.
            argList = new ConfigASTNamedArgumentList(null)
        } else {
                argList = new ConfigASTNamedArgumentList(o)
                errorCollector.error(argList, "Object ${o} is neither a JSONArray nor a JSONObject")

        }

        return argList
    }

    public @CheckForNull ConfigASTKey parseKey(JSONObject o) {
        ConfigASTKey key = new ConfigASTKey(o)

        key.key = o.getString("key")
        return key
    }

    public @CheckForNull ConfigASTValue parseValue(JSONObject o) {
        if (o.getBoolean("isConstant")) {
            return ConfigASTValue.fromConstant(o.get("value"), o)
        } else {
            return ConfigASTValue.fromGString(o.getString("value"), o)
        }
    }

    public @CheckForNull ConfigASTScriptBlock parseScriptBlock(JSONObject j) {
        ConfigASTScriptBlock scriptBlock = new ConfigASTScriptBlock(j)
        scriptBlock.args = parseArgumentList(j.getJSONObject("arguments"))

        return scriptBlock
    }

    public @CheckForNull ConfigASTTreeStep parseTreeStep(JSONObject j) {
        ConfigASTTreeStep step = new ConfigASTTreeStep(j)
        step.name = j.getString("name")
        step.args = parseArgumentList(j.get("arguments"))

        j.getJSONArray("children").each { o ->
            JSONObject c = (JSONObject)o
            step.children.add(parseStep(c))
        }

        return step
    }

    public @CheckForNull ConfigASTBuildCondition parseBuildCondition(JSONObject j) {
        ConfigASTBuildCondition condition = new ConfigASTBuildCondition(j)

        condition.condition = j.getString("condition")
        condition.branch = parseBranch(j.getJSONObject("branch"))

        return condition
    }

    public @CheckForNull ConfigASTNotifications parseNotifications(JSONObject j) {
        ConfigASTNotifications notifications = new ConfigASTNotifications(j)

        j.getJSONArray("conditions").each { o ->
            JSONObject conditionBlock = (JSONObject) o
            notifications.conditions.add(parseBuildCondition(conditionBlock))
        }
        return notifications
    }

    public @CheckForNull ConfigASTPostBuild parsePostBuild(JSONObject j) {
        ConfigASTPostBuild postBuild = new ConfigASTPostBuild(j)

        j.getJSONArray("conditions").each { o ->
            JSONObject conditionBlock = (JSONObject) o
            postBuild.conditions.add(parseBuildCondition(conditionBlock))
        }
        return postBuild
    }

    public @CheckForNull ConfigASTEnvironment parseEnvironment(JSONArray j) {
        ConfigASTEnvironment environment = new ConfigASTEnvironment(j)

        j.each { rawEntry ->
            JSONObject entry = (JSONObject) rawEntry
            // Passing the whole thing to parseKey to capture the JSONObject the "key" is in.
            ConfigASTKey key = parseKey(entry)

            ConfigASTValue value = parseValue(entry.getJSONObject("value"))

            environment.variables.put(key, value)
        }
        return environment
    }

    public @CheckForNull ConfigASTTools parseTools(JSONArray j) {
        ConfigASTTools tools = new ConfigASTTools(j)

        j.each { rawEntry ->
            JSONObject entry = (JSONObject) rawEntry
            // Passing the whole thing to parseKey to capture the JSONObject the "key" is in.
            ConfigASTKey key = parseKey(entry)

            ConfigASTValue value = parseValue(entry.getJSONObject("value"))

            tools.tools.put(key, value)
        }
        return tools
    }

    public @CheckForNull ConfigASTAgent parseAgent(Object j) {
        ConfigASTAgent agent = new ConfigASTAgent(j)

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
