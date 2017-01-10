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
import org.jenkinsci.plugins.pipeline.modeldefinition.AbstractModelDefTest;
import org.jenkinsci.plugins.pipeline.modeldefinition.BaseParserLoaderTest;
import org.jenkinsci.plugins.pipeline.modeldefinition.parser.JSONParser;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(Parameterized.class)
public class ErrorsJSONParserTest extends BaseParserLoaderTest {
    private String configName;
    private String expectedError;

    public ErrorsJSONParserTest(String configName, String expectedError) {
        this.configName = configName;
        this.expectedError = expectedError;
    }

    @Parameterized.Parameters(name="Name: {0}")
    public static Iterable<Object[]> generateParameters() {
        return AbstractModelDefTest.configsWithErrors();
    }

    @Test
    public void parseAndValidateJSONWithError() throws Exception {
        try {
            JsonNode json = JsonLoader.fromString(fileContentsFromResources("json/errors/" + configName + ".json"));

            assertNotNull("Couldn't parse JSON for " + configName, json);
            assertFalse("Couldn't parse JSON for " + configName, json.size() == 0);
            assertFalse("Couldn't parse JSON for " + configName, json.isNull());

            JSONParser jp = new JSONParser(new SimpleJsonTree(json));
            jp.parse();

            assertTrue(jp.getErrorCollector().getErrorCount() > 0);

            assertTrue("Didn't find expected error in " + getJSONErrorReport(jp, configName),
                    foundExpectedErrorInJSON(jp.getErrorCollector().asJson(), expectedError));
        } catch (Exception e) {
            // If there's a straight-up parsing error, make sure it's what we expect.
            assertTrue(e.getMessage(), e.getMessage().contains(expectedError));
        }
    }
}
