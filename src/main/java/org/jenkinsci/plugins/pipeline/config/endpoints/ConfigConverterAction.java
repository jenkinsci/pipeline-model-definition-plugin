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
package org.jenkinsci.plugins.pipeline.config.endpoints;

import hudson.Extension;
import hudson.model.RootAction;
import hudson.util.HttpResponses;
import hudson.util.TimeUnit2;
import jenkins.model.Jenkins;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.codehaus.groovy.control.MultipleCompilationErrorsException;
import org.codehaus.groovy.control.messages.SyntaxErrorMessage;
import org.jenkinsci.plugins.pipeline.config.ast.ConfigASTPipelineDef;
import org.jenkinsci.plugins.pipeline.config.parser.Converter;
import org.jenkinsci.plugins.pipeline.config.parser.JSONParser;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.interceptor.RequirePOST;

import javax.servlet.ServletException;
import java.io.IOException;

import static hudson.security.Permission.READ;

/**
 * Endpoint for converting to/from JSON/Groovy and validating both.
 *
 * @author Andrew Bayer
 */
@Extension
public class ConfigConverterAction implements RootAction {
    public static final String PIPELINE_CONFIG_URL = "pipeline-config-converter";

    @Override
    public String getUrlName() {
        return PIPELINE_CONFIG_URL;
    }

    @Override
    public String getIconFileName() {
        return null;
    }

    @Override
    public String getDisplayName() {
        return null;
    }

    @SuppressWarnings("unused")
    @RequirePOST
    public HttpResponse doToGroovy(StaplerRequest req) {
        Jenkins.getInstance().checkPermission(READ);

        JSONObject result = new JSONObject();

        try {
            String jsonAsString = req.getParameter("json");

            JSONObject json = JSONObject.fromObject(jsonAsString);

            JSONParser parser = new JSONParser(json);

            ConfigASTPipelineDef pipelineDef = parser.parse();

            if (parser.getErrorCollector().getErrorCount() > 0) {
                result.accumulate("result", "failure");

                JSONArray errors = new JSONArray();
                for (String jsonError : parser.getErrorCollector().errorsAsStrings()) {
                    errors.add(jsonError);
                }

                result.accumulate("errors", errors);
            } else {
                result.accumulate("result", "success");
                result.accumulate("groovy", pipelineDef.toPrettyGroovy());
            }
        } catch (Exception je) {
            result.accumulate("result", "failure");
            JSONArray errors = new JSONArray();
            errors.add(je.getMessage());
            result.accumulate("errors", errors);
        }

        return HttpResponses.okJSON(result);
    }

    @SuppressWarnings("unused")
    @RequirePOST
    public HttpResponse doToJson(StaplerRequest req) {
        Jenkins.getInstance().checkPermission(READ);

        JSONObject result = new JSONObject();

        String groovyAsString = req.getParameter("groovy");

        try {
            ConfigASTPipelineDef pipelineDef = Converter.scriptToPipelineDef(groovyAsString);
            result.accumulate("result", "success");
            result.accumulate("json", pipelineDef.toJSON());
        } catch (MultipleCompilationErrorsException e) {
            result.accumulate("result", "failure");
            JSONArray errors = new JSONArray();
            for (Object o : e.getErrorCollector().getErrors()) {
                if (o instanceof SyntaxErrorMessage) {
                    errors.add(((SyntaxErrorMessage) o).getCause().getMessage());
                }
            }
            result.accumulate("errors", errors);
        } catch (Exception e) {
            result.accumulate("result", "failure");
            JSONArray errors = new JSONArray();
            errors.add(e.getMessage());
            result.accumulate("errors", errors);
        }

        return HttpResponses.okJSON(result);
    }

    @SuppressWarnings("unused")
    @RequirePOST
    public HttpResponse doValidateJenkinsfile(StaplerRequest req) {
        Jenkins.getInstance().checkPermission(READ);

        JSONObject result = new JSONObject();

        String groovyAsString = req.getParameter("jenkinsfile");

        try {
            ConfigASTPipelineDef pipelineDef = Converter.scriptToPipelineDef(groovyAsString);
            result.accumulate("result", "success");
        } catch (MultipleCompilationErrorsException e) {
            result.accumulate("result", "failure");
            JSONArray errors = new JSONArray();
            for (Object o : e.getErrorCollector().getErrors()) {
                if (o instanceof SyntaxErrorMessage) {
                    errors.add(((SyntaxErrorMessage) o).getCause().getMessage());
                }
            }
            result.accumulate("errors", errors);
        } catch (Exception e) {
            result.accumulate("result", "failure");
            JSONArray errors = new JSONArray();
            errors.add(e.getMessage());
            result.accumulate("errors", errors);
        }

        return HttpResponses.okJSON(result);
    }

    @SuppressWarnings("unused")
    @RequirePOST
    public HttpResponse doValidateJson(StaplerRequest req) {
        Jenkins.getInstance().checkPermission(READ);

        JSONObject result = new JSONObject();

        try {
            String jsonAsString = req.getParameter("json");

            JSONObject json = JSONObject.fromObject(jsonAsString);

            JSONParser parser = new JSONParser(json);

            parser.parse();

            if (parser.getErrorCollector().getErrorCount() > 0) {
                result.accumulate("result", "failure");

                JSONArray errors = new JSONArray();
                for (String jsonError : parser.getErrorCollector().errorsAsStrings()) {
                    errors.add(jsonError);
                }

                result.accumulate("errors", errors);
            } else {
                result.accumulate("result", "success");
            }
        } catch (Exception je) {
            result.accumulate("result", "failure");
            JSONArray errors = new JSONArray();
            errors.add(je.getMessage());
            result.accumulate("errors", errors);
        }

        return HttpResponses.okJSON(result);

    }

    @SuppressWarnings("unused")
    public void doSchema(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        rsp.serveFile(req, getClass().getResource("/ast-schema.json"), TimeUnit2.DAYS.toMillis(1));
    }
}
