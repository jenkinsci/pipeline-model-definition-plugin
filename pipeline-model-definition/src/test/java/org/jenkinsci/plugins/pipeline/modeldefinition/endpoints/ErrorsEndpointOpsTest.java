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
package org.jenkinsci.plugins.pipeline.modeldefinition.endpoints;

import org.htmlunit.HttpMethod;
import org.htmlunit.WebRequest;
import org.htmlunit.util.NameValuePair;
import net.sf.json.JSONObject;
import org.jenkinsci.plugins.pipeline.modeldefinition.AbstractModelDefTest;
import org.jenkinsci.plugins.pipeline.modeldefinition.Messages;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.jvnet.hudson.test.JenkinsRule;

import java.net.URL;
import java.util.Collections;

import static org.junit.Assert.*;

@RunWith(Parameterized.class)
public class ErrorsEndpointOpsTest extends AbstractModelDefTest {
    private String configName;
    private String expectedError;

    public ErrorsEndpointOpsTest(String configName, String expectedError) {
        this.configName = configName;
        this.expectedError = expectedError;
    }

    @Parameterized.Parameters(name="Name: {0}")
    public static Iterable<Object[]> generateParameters() {
        return AbstractModelDefTest.runtimeConfigsWithErrors();
    }

    @Test
    public void testFailedValidateJson() throws Exception {
        JenkinsRule.WebClient wc = j.createWebClient();
        WebRequest req = new WebRequest(new URL(wc.getContextPath() + ModelConverterAction.PIPELINE_CONVERTER_URL + "/validateJson"), HttpMethod.POST);
        String simpleJson = fileContentsFromResources("json/errors/" + configName + ".json");

        assertNotNull(simpleJson);

        NameValuePair pair = new NameValuePair("json", simpleJson);
        req.setRequestParameters(Collections.singletonList(pair));

        String rawResult = wc.getPage(req).getWebResponse().getContentAsString();
        assertNotNull(rawResult);

        JSONObject result = JSONObject.fromObject(rawResult);
        // TODO: Change this when we get proper JSON errors causing HTTP error codes
        assertEquals("Full result doesn't include status - " + result.toString(2), "ok", result.getString("status"));
        JSONObject resultData = result.getJSONObject("data");
        assertNotNull(resultData);
        assertEquals("Result wasn't a failure - " + result.toString(2), "failure", resultData.getString("result"));

        /*
         * If the error message contains the list of legal agent types, ensure that agent types contributed by other
         * plugins (for example, the Kubernetes plugin in PCT context) are present. Note that we can't do this from
         * AbstractModelDefTest#configsWithErrors because determining this list requires Jenkins to be started, and
         * Jenkins has not yet been started when we are determining the parameters for the JUnit parameterized test.
         */
        if (expectedError.equals(
                Messages.ModelValidatorImpl_InvalidAgentType("foo", "[any, label, none, otherField]"))) {
            expectedError = Messages.ModelValidatorImpl_InvalidAgentType("foo", legalAgentTypes);
        }

        assertTrue("Errors array (" + resultData.getJSONArray("errors").toString(2) + ") didn't contain expected error '" + expectedError + "'",
                foundExpectedErrorInJSON(resultData.getJSONArray("errors"), expectedError));
    }

    @Test
    public void testFailedValidateJenkinsfile() throws Exception {
        final String rawJenkinsfile = fileContentsFromResources("errors/" + configName + ".groovy", true);

        if (rawJenkinsfile != null) {

            JenkinsRule.WebClient wc = j.createWebClient();
            WebRequest req = new WebRequest(new URL(wc.getContextPath() + ModelConverterAction.PIPELINE_CONVERTER_URL + "/validateJenkinsfile"), HttpMethod.POST);

            assertNotNull(rawJenkinsfile);

            NameValuePair pair = new NameValuePair("jenkinsfile", rawJenkinsfile);
            req.setRequestParameters(Collections.singletonList(pair));

            String rawResult = wc.getPage(req).getWebResponse().getContentAsString();
            assertNotNull(rawResult);

            JSONObject result = JSONObject.fromObject(rawResult);
            // TODO: Change this when we get proper JSON errors causing HTTP error codes
            assertEquals("Full result doesn't include status - " + result.toString(2), "ok", result.getString("status"));
            JSONObject resultData = result.getJSONObject("data");
            assertNotNull(resultData);
            assertEquals("Result wasn't a failure - " + result.toString(2), "failure", resultData.getString("result"));
        }
    }

    @Test
    public void testFailedToJenkinsfile() throws Exception {
        JenkinsRule.WebClient wc = j.createWebClient();
        WebRequest req = new WebRequest(new URL(wc.getContextPath() + ModelConverterAction.PIPELINE_CONVERTER_URL + "/toJenkinsfile"), HttpMethod.POST);
        String simpleJson = fileContentsFromResources("json/errors/" + configName + ".json");

        assertNotNull(simpleJson);

        NameValuePair pair = new NameValuePair("json", simpleJson);
        req.setRequestParameters(Collections.singletonList(pair));

        String rawResult = wc.getPage(req).getWebResponse().getContentAsString();
        assertNotNull(rawResult);

        JSONObject result = JSONObject.fromObject(rawResult);
        // TODO: Change this when we get proper JSON errors causing HTTP error codes
        assertEquals("Full result doesn't include status - " + result.toString(2), "ok", result.getString("status"));
        JSONObject resultData = result.getJSONObject("data");
        assertNotNull(resultData);
        assertEquals("Result wasn't a failure - " + result.toString(2), "failure", resultData.getString("result"));
    }

    @Test
    public void testFailedToJson() throws Exception {
        JenkinsRule.WebClient wc = j.createWebClient();
        WebRequest req = new WebRequest(new URL(wc.getContextPath() + ModelConverterAction.PIPELINE_CONVERTER_URL + "/toJson"), HttpMethod.POST);
        String initialGroovy = fileContentsFromResources("errors/" + configName + ".groovy", true);

        assertNotNull(initialGroovy);

        NameValuePair pair = new NameValuePair("jenkinsfile", initialGroovy);
        req.setRequestParameters(Collections.singletonList(pair));

        String rawResult = wc.getPage(req).getWebResponse().getContentAsString();
        assertNotNull(rawResult);

        JSONObject result = JSONObject.fromObject(rawResult);
        // TODO: Change this when we get proper JSON errors causing HTTP error codes
        assertEquals("Full result doesn't include status - " + result.toString(2), "ok", result.getString("status"));
        JSONObject resultData = result.getJSONObject("data");
        assertNotNull(resultData);
        assertEquals("Result wasn't a failure - " + result.toString(2), "failure", resultData.getString("result"));
    }
}
