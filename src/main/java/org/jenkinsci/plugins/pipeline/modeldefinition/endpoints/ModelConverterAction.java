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

import hudson.Extension;
import hudson.model.RootAction;
import hudson.util.HttpResponses;
import hudson.util.TimeUnit2;
import jenkins.model.Jenkins;
import net.sf.json.JSON;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import net.sf.json.JSONSerializer;
import net.sf.json.util.JSONUtils;
import org.codehaus.groovy.control.MultipleCompilationErrorsException;
import org.codehaus.groovy.control.messages.SyntaxErrorMessage;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTPipelineDef;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTStep;
import org.jenkinsci.plugins.pipeline.modeldefinition.parser.Converter;
import org.jenkinsci.plugins.pipeline.modeldefinition.parser.JSONParser;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.interceptor.RequirePOST;

import javax.servlet.ServletException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static hudson.security.Permission.READ;

/**
 * Endpoint for converting to/from JSON/Groovy and validating both.
 *
 * @author Andrew Bayer
 */
@Extension
public class ModelConverterAction implements RootAction {
    public static final String PIPELINE_CONVERTER_URL = "pipeline-model-converter";

    @Override
    public String getUrlName() {
        return PIPELINE_CONVERTER_URL;
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
    public HttpResponse doToJenkinsfile(StaplerRequest req) {
        Jenkins.getInstance().checkPermission(READ);

        JSONObject result = new JSONObject();

        try {
            String jsonAsString = req.getParameter("json");

            JSONObject json = JSONObject.fromObject(jsonAsString);

            JSONParser parser = new JSONParser(json);

            ModelASTPipelineDef pipelineDef = parser.parse();

            if (parser.getErrorCollector().getErrorCount() > 0) {
                result.accumulate("result", "failure");

                JSONArray errors = new JSONArray();
                for (String jsonError : parser.getErrorCollector().errorsAsStrings()) {
                    errors.add(jsonError);
                }

                result.accumulate("errors", errors);
            } else {
                result.accumulate("result", "success");
                result.accumulate("jenkinsfile", pipelineDef.toPrettyGroovy());
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

        String groovyAsString = req.getParameter("jenkinsfile");

        try {
            ModelASTPipelineDef pipelineDef = Converter.scriptToPipelineDef(groovyAsString);
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
    public HttpResponse doStepsToJson(StaplerRequest req) {
        Jenkins.getInstance().checkPermission(READ);

        JSONObject result = new JSONObject();

        String groovyAsString = req.getParameter("jenkinsfile");

        try {
            List<ModelASTStep> steps = Converter.scriptToPlainSteps(groovyAsString);
            JSONArray array = new JSONArray();
            for (ModelASTStep step : steps) {
                array.add(step.toJSON());
            }
            result.accumulate("result", "success");
            result.accumulate("json", array);
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
    public HttpResponse doStepsToJenkinsfile(StaplerRequest req) {
        Jenkins.getInstance().checkPermission(READ);

        JSONObject result = new JSONObject();
        try {
            String jsonAsString = req.getParameter("json");
            JSON json = JSONSerializer.toJSON(jsonAsString);

            JSONArray jsonSteps;
            if (json.isArray()) {
                jsonSteps = (JSONArray)json;
            } else {
                jsonSteps = new JSONArray();
                jsonSteps.add(json);
            }
            JSONParser parser = new JSONParser(null);
            List<ModelASTStep> astSteps = new ArrayList<>(jsonSteps.size());
            for (Object jsonStep : jsonSteps) {
                if (!(jsonStep instanceof JSONObject)) {
                    continue;
                }
                ModelASTStep astStep = parser.parseStep((JSONObject)jsonStep);
                astStep.validate(parser.getValidator());
                astSteps.add(astStep);
            }

            if (parser.getErrorCollector().getErrorCount() > 0) {
                result.accumulate("result", "failure");

                JSONArray errors = new JSONArray();
                for (String jsonError : parser.getErrorCollector().errorsAsStrings()) {
                    errors.add(jsonError);
                }

                result.accumulate("errors", errors);
            } else {
                result.accumulate("result", "success");
                StringBuilder jenkinsFile = new StringBuilder();
                for (ModelASTStep step : astSteps) {
                    if (jenkinsFile.length() > 0) {
                        jenkinsFile.append('\n');
                    }
                    jenkinsFile.append(step.toGroovy());
                }
                result.accumulate("jenkinsfile", jenkinsFile.toString());
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
    public HttpResponse doValidateJenkinsfile(StaplerRequest req) {
        Jenkins.getInstance().checkPermission(READ);

        JSONObject result = new JSONObject();

        String groovyAsString = req.getParameter("jenkinsfile");

        try {
            ModelASTPipelineDef pipelineDef = Converter.scriptToPipelineDef(groovyAsString);
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
