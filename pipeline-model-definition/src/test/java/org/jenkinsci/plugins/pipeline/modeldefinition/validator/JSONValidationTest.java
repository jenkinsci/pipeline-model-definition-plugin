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
package org.jenkinsci.plugins.pipeline.modeldefinition.validator;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.fge.jsonschema.tree.SimpleJsonTree;
import com.github.fge.jsonschema.util.JsonLoader;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.jenkinsci.plugins.pipeline.modeldefinition.BaseParserLoaderTest;
import org.jenkinsci.plugins.pipeline.modeldefinition.Messages;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTPipelineDef;
import org.jenkinsci.plugins.pipeline.modeldefinition.parser.JSONParser;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class JSONValidationTest extends BaseParserLoaderTest {

    @Test
    public void scriptBlockIsAString() throws Exception {
        ModelASTPipelineDef root = parse(getClass().getResource("/simpleScript.groovy"));

        assertNotNull(root);

        JSONObject origJson = root.toJSON();
        assertNotNull(origJson);

        JSONArray stages = origJson.getJSONObject("pipeline").getJSONArray("stages");
        assertNotNull(stages);

        JSONArray branches = stages.getJSONObject(0).getJSONArray("branches");
        assertNotNull(branches);

        JSONArray steps = branches.getJSONObject(0).getJSONArray("steps");
        assertNotNull(steps);

        JSONObject scriptStep  = steps.getJSONObject(0);
        assertNotNull(scriptStep);
        assertEquals("script", scriptStep.getString("name"));

        JSONObject arg = scriptStep.getJSONObject("arguments");
        assertNotNull(arg);
        assertTrue(arg.getBoolean("isLiteral"));
        assertEquals("echo \"In a script step\"", arg.getString("value"));

    }

    @Test
    public void parallelPipelineDuplicateNames() throws Exception {
        String expectedError = Messages.ModelValidatorImpl_DuplicateParallelName("first");
        try {
            JsonNode json = JsonLoader.fromString(fileContentsFromResources("json/errors/parallelPipelineDuplicateNames.json"));

            assertNotNull("Couldn't parse JSON for parallelPipelineDuplicateNames", json);
            assertFalse("Couldn't parse JSON for parallelPipelineDuplicateNames", json.size() == 0);
            assertFalse("Couldn't parse JSON for parallelPipelineDuplicateNames", json.isNull());

            JSONParser jp = new JSONParser(new SimpleJsonTree(json));
            jp.parse();

            assertTrue(jp.getErrorCollector().getErrorCount() > 0);

            assertTrue("Didn't find expected error in " + getJSONErrorReport(jp, "parallelPipelineDuplicateNames"),
                    foundExpectedErrorInJSON(jp.getErrorCollector().asJson(), expectedError));
        } catch (Exception e) {
            // If there's a straight-up parsing error, make sure it's what we expect.
            assertTrue(e.getMessage(), e.getMessage().contains(expectedError));
        }
    }

}
