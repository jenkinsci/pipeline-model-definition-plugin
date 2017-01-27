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

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.jenkinsci.plugins.pipeline.modeldefinition.BaseParserLoaderTest;
import org.jenkinsci.plugins.pipeline.modeldefinition.Messages;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTPipelineDef;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
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

        JSONArray args = scriptStep.getJSONArray("arguments");
        assertNotNull(args);
        JSONObject arg = args.getJSONObject(0);
        assertNotNull(arg);
        JSONObject val = arg.getJSONObject("value");
        assertNotNull(val);
        assertTrue(val.getBoolean("isLiteral"));
        assertEquals("echo \"In a script step\"", val.getString("value"));

    }

    @Test
    public void parallelPipelineDuplicateNames() throws Exception {
        findErrorInJSON(Messages.ModelValidatorImpl_DuplicateParallelName("first"), "parallelPipelineDuplicateNames");
    }

    @Test
    public void invalidIdentifierInEnv() throws Exception {
        findErrorInJSON(Messages.ModelValidatorImpl_InvalidIdentifierInEnv("F OO"), "invalidIdentifierInEnv");
        findErrorInJSON(Messages.ModelValidatorImpl_InvalidIdentifierInEnv("F$OO"), "invalidIdentifierInEnv");
    }

}
