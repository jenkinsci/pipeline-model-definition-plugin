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

import com.gargoylesoftware.htmlunit.HttpMethod;
import com.gargoylesoftware.htmlunit.WebRequest;
import com.gargoylesoftware.htmlunit.util.NameValuePair;
import net.sf.json.JSONObject;
import org.jenkinsci.plugins.pipeline.modeldefinition.AbstractModelDefTest;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.kohsuke.stapler.StaplerRequest;

import java.io.IOException;
import java.util.Collections;

import static org.hamcrest.core.IsEqual.equalTo;
import static org.hamcrest.core.IsNot.not;
import static org.hamcrest.core.StringContains.containsString;
import static org.jenkinsci.plugins.pipeline.modeldefinition.util.IsJsonObjectContaining.hasEntry;
import static org.jenkinsci.plugins.pipeline.modeldefinition.util.IsJsonObjectContaining.hasKey;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;

/**
 * Tests steps conversion in {@link ModelConverterAction}
 *
 * @author Robert Sandell &lt;rsandell@cloudbees.com&gt;.
 * @see ModelConverterAction#doStepToJenkinsfile(StaplerRequest)
 */
public class ModelConverterActionStepsTest extends AbstractModelDefTest {

    @Test
    public void simpleEchoToJenkinsfile() throws IOException {
        JSONObject result = callStepToJenkinsFile("json/steps/simpleEcho.json");
        assertThat(result, hasEntry("status", "ok"));
        assertThat(result, hasKey("data"));
        JSONObject data = result.getJSONObject("data");
        assertThat(data, hasEntry("result", "success"));
        assertThat(data, hasEntry("jenkinsfile", "echo('hello')"));
    }

    @Test
    public void arrayEchoToJenkinsfile() throws IOException {
        JSONObject result = callStepToJenkinsFile("json/steps/arrayEcho.json");
        assertThat(result, hasEntry("status", "ok"));
        assertThat(result, hasKey("data"));
        JSONObject data = result.getJSONObject("data");
        assertThat(data, hasEntry("result", "success"));
        assertThat(data, hasEntry("jenkinsfile", "echo('Hello')\necho('World')"));
    }

    @Test
    public void simpleScriptToJenkinsfile() throws IOException {
        JSONObject result = callStepToJenkinsFile("json/steps/simpleScript.json");
        assertThat(result, hasEntry("status", "ok"));
        assertThat(result, hasKey("data"));
        JSONObject data = result.getJSONObject("data");
        assertThat(data, hasEntry("result", "success"));
        assertThat(data, hasEntry("jenkinsfile", containsString("script {")));
        assertThat(data, hasEntry("jenkinsfile", containsString("echo \"In a script step\"")));
    }

    //TODO Something more complex than echo "Hello"

    private JSONObject callStepToJenkinsFile(String jsonFileName) throws IOException {
        JenkinsRule.WebClient wc = j.createWebClient();
        WebRequest req = new WebRequest(wc.createCrumbedUrl(ModelConverterAction.PIPELINE_CONVERTER_URL + "/stepToJenkinsfile"), HttpMethod.POST);
        String simpleJson = fileContentsFromResources(jsonFileName);

        assertNotNull(simpleJson);

        NameValuePair pair = new NameValuePair("json", simpleJson);
        req.setRequestParameters(Collections.singletonList(pair));

        String rawResult = wc.getPage(req).getWebResponse().getContentAsString();
        assertNotNull(rawResult);

        return JSONObject.fromObject(rawResult);
    }
}
