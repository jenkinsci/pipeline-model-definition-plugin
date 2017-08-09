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
 *
 */

package org.jenkinsci.plugins.pipeline.modeldefinition;

import com.gargoylesoftware.htmlunit.HttpMethod;
import com.gargoylesoftware.htmlunit.WebRequest;
import com.gargoylesoftware.htmlunit.util.NameValuePair;
import hudson.model.Result;
import hudson.model.Slave;
import net.sf.json.JSONObject;
import org.apache.commons.io.FileUtils;
import org.jenkinsci.plugins.pipeline.modeldefinition.endpoints.ModelConverterAction;
import org.jenkinsci.plugins.pipeline.modeldefinition.model.Stage;
import org.junit.BeforeClass;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.util.Collections;

import static org.jenkinsci.plugins.pipeline.modeldefinition.util.IsJsonObjectContaining.hasEntry;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;


/**
 * Tests {@link Stage#when}
 */
public class WhenStageTest extends AbstractModelDefTest {

    private static Slave s;

    @BeforeClass
    public static void setUpAgent() throws Exception {
        s = j.createOnlineSlave();
        s.setLabelString("here");
    }

    @Test
    public void simpleWhen() throws Exception {
        env(s).put("SECOND_STAGE", "NOPE").set();
        ExpectationsBuilder expect = expect("when", "simpleWhen").runFromRepo(false);
        expect.logContains("One", "Hello", "Should I run?", "Two").logNotContains("World").go();
        env(s).put("SECOND_STAGE", "RUN").set();
        expect.resetForNewRun(Result.SUCCESS).logContains("One", "Hello", "Should I run?", "Two", "World").go();
    }

    @Test
    public void whenException() throws Exception {
        env(s).put("SECOND_STAGE", "NOPE").set();
        expect(Result.FAILURE, "when", "whenException").runFromRepo(false)
                .logContains("One", "Hello", "Should I run?", "NullPointerException", "Two")
                .logNotContains("World").go();
    }

    @Test
    public void whenEmpty() throws Exception {
        env(s).put("SECOND_STAGE", "NOPE").set();
        expect(Result.FAILURE, "when", "whenEmpty").runFromRepo(false)
                .logContains(Messages.ModelValidatorImpl_EmptyWhen()).logNotContains("Two", "World").go();
    }

    @Test
    public void toJson() throws IOException {
        final String rawJenkinsfile = fileContentsFromResources("when/simpleWhen.groovy", true);
        JenkinsRule.WebClient wc = j.createWebClient();
        WebRequest req = new WebRequest(new URL(wc.getContextPath() + ModelConverterAction.PIPELINE_CONVERTER_URL + "/toJson"), HttpMethod.POST);

        assertNotNull(rawJenkinsfile);

        NameValuePair pair = new NameValuePair("jenkinsfile", rawJenkinsfile);
        req.setRequestParameters(Collections.singletonList(pair));

        String rawResult = wc.getPage(req).getWebResponse().getContentAsString();
        assertNotNull(rawResult);

        JSONObject result = JSONObject.fromObject(rawResult);
        assertNotNull(result);
        assertThat(result, hasEntry("status", "ok"));
        assertThat(result, hasEntry("data", hasEntry("result", "success")));

        req = new WebRequest(new URL(wc.getContextPath() + ModelConverterAction.PIPELINE_CONVERTER_URL + "/toJenkinsfile"), HttpMethod.POST);
        pair = new NameValuePair("json", result.getJSONObject("data").getJSONObject("json").toString());
        req.setRequestParameters(Collections.singletonList(pair));

        rawResult = wc.getPage(req).getWebResponse().getContentAsString();
        assertNotNull(rawResult);
        result = JSONObject.fromObject(rawResult);
        assertThat(result, hasEntry("status", "ok"));
        assertThat(result, hasEntry("data", hasEntry("result", "success")));
    }

    @Issue("JENKINS-43143")
    @Test
    public void paramsInWhenExpression() throws Exception {
        expect("paramsInWhenExpression")
                .logContains("[Pipeline] { (One)", "[Pipeline] { (Two)", "World")
                .go();
    }

    @Test
    public void whenChangeset() throws Exception {
        //First time build always skips the changelog
        final ExpectationsBuilder builder = expect("when/changelog", "changeset")
                .logContains("Hello", "Stage 'Two' skipped due to when conditional", "Warning, empty changelog. Probably because this is the first build.")
                .logNotContains("JS World");
        builder.go();

        builder.resetForNewRun(Result.SUCCESS);

        sampleRepo.write("webapp/js/somecode.js", "//fake file");
        sampleRepo.git("add", "webapp/js/somecode.js");
        sampleRepo.git("commit", "--message=files");

        builder.logContains("Hello", "JS World")
                .logNotContains("Stage 'Two' skipped due to when conditional", "Warning, empty changelog.")
                .go();
    }

    @Test
    public void whenChangelog() throws Exception {
        //First time build always skips the changelog
        final ExpectationsBuilder builder = expect("when/changelog", "changelog")
                .logContains("Hello", "Stage 'Two' skipped due to when conditional", "Warning, empty changelog. Probably because this is the first build.")
                .logNotContains("Dull World");
        builder.go();

        builder.resetForNewRun(Result.SUCCESS);

        sampleRepo.write("something.txt", "//fake file");
        sampleRepo.git("add", "something.txt");
        sampleRepo.git("commit", "-m", "Some title that we don't care about\n\nSome explanation\n[DEPENDENCY] some-app#45");

        builder.logContains("Hello", "Dull World")
                .logNotContains("Stage 'Two' skipped due to when conditional", "Warning, empty changelog.")
                .go();
    }

}
