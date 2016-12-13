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
import jenkins.model.Jenkins;
import net.sf.json.JSON;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import net.sf.json.JSONSerializer;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.codehaus.groovy.control.MultipleCompilationErrorsException;
import org.codehaus.groovy.control.messages.SyntaxErrorMessage;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTPipelineDef;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTStep;
import org.jenkinsci.plugins.pipeline.modeldefinition.parser.Converter;
import org.jenkinsci.plugins.pipeline.modeldefinition.parser.JSONParser;
import org.jenkinsci.plugins.pipeline.modeldefinition.validator.ErrorCollector;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.interceptor.RequirePOST;

import java.io.PrintWriter;
import java.io.StringWriter;
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

        String jsonAsString = req.getParameter("json");

        if (jsonAsString != null && !jsonAsString.equals("")) {
            try {
                JSONObject json = JSONObject.fromObject(jsonAsString);

                JSONParser parser = new JSONParser(json);

                ModelASTPipelineDef pipelineDef = parser.parse();

                if (!collectErrors(result, parser.getErrorCollector())) {
                    result.accumulate("result", "success");
                    result.accumulate("jenkinsfile", pipelineDef.toPrettyGroovy());
                }
            } catch (Exception je) {
                reportFailure(result, je);
            }
        } else {
            reportFailure(result, "No content found for 'json' parameter");
        }

        return HttpResponses.okJSON(result);
    }

    @SuppressWarnings("unused")
    @RequirePOST
    public HttpResponse doToJson(StaplerRequest req) {
        Jenkins.getInstance().checkPermission(READ);

        JSONObject result = new JSONObject();

        String groovyAsString = req.getParameter("jenkinsfile");

        if (groovyAsString != null && !groovyAsString.equals("")) {
            try {
                ModelASTPipelineDef pipelineDef = Converter.scriptToPipelineDef(groovyAsString);
                result.accumulate("result", "success");
                result.accumulate("json", pipelineDef.toJSON());
            } catch (Exception e) {
                reportFailure(result, e);
            }
        } else {
            reportFailure(result, "No content found for 'jenkinsfile' parameter");
        }

        return HttpResponses.okJSON(result);
    }

    @SuppressWarnings("unused")
    @RequirePOST
    public HttpResponse doStepsToJson(StaplerRequest req) {
        Jenkins.getInstance().checkPermission(READ);

        JSONObject result = new JSONObject();

        String groovyAsString = req.getParameter("jenkinsfile");

        if (groovyAsString != null && !groovyAsString.equals("")) {
            try {
                List<ModelASTStep> steps = Converter.scriptToPlainSteps(groovyAsString);
                JSONArray array = new JSONArray();
                for (ModelASTStep step : steps) {
                    array.add(step.toJSON());
                }
                result.accumulate("result", "success");
                result.accumulate("json", array);
            } catch (Exception e) {
                reportFailure(result, e);
            }
        } else {
            reportFailure(result, "No content found for 'jenkinsfile' parameter");
        }

        return HttpResponses.okJSON(result);
    }

    @SuppressWarnings("unused")
    @RequirePOST
    public HttpResponse doStepsToJenkinsfile(StaplerRequest req) {
        Jenkins.getInstance().checkPermission(READ);

        JSONObject result = new JSONObject();

        String jsonAsString = req.getParameter("json");
        if (jsonAsString != null && !jsonAsString.equals("")) {
            try {
                JSON json = JSONSerializer.toJSON(jsonAsString);

                JSONArray jsonSteps;
                if (json.isArray()) {
                    jsonSteps = (JSONArray) json;
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
                    ModelASTStep astStep = parser.parseStep((JSONObject) jsonStep);
                    if (astStep != null) {
                        astStep.validate(parser.getValidator());
                        astSteps.add(astStep);
                    }
                }

                boolean collectedSomeErrors = collectErrors(result, parser.getErrorCollector());

                if (!collectedSomeErrors && astSteps.isEmpty()) {
                    reportFailure(result, "No result.");
                } else if (!collectedSomeErrors) {
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
                reportFailure(result, je);
            }
        } else {
            reportFailure(result, "No content found for 'json' parameter");
        }

        return HttpResponses.okJSON(result);
    }

    @SuppressWarnings("unused")
    @RequirePOST
    public HttpResponse doValidateJenkinsfile(StaplerRequest req) {
        Jenkins.getInstance().checkPermission(READ);

        JSONObject result = new JSONObject();

        String groovyAsString = req.getParameter("jenkinsfile");

        if (groovyAsString != null && !groovyAsString.equals("")) {
            try {
                ModelASTPipelineDef pipelineDef = Converter.scriptToPipelineDef(groovyAsString);
                result.accumulate("result", "success");
            } catch (Exception e) {
                reportFailure(result, e);
            }
        } else {
            reportFailure(result, "No content found for 'jenkinsfile' parameter");
        }

        return HttpResponses.okJSON(result);
    }

    @SuppressWarnings("unused")
    @RequirePOST
    public HttpResponse doValidateJson(StaplerRequest req) {
        Jenkins.getInstance().checkPermission(READ);

        JSONObject result = new JSONObject();

        String jsonAsString = req.getParameter("json");
        if (jsonAsString != null && !jsonAsString.equals("")) {
            try {

                JSONObject json = JSONObject.fromObject(jsonAsString);

                JSONParser parser = new JSONParser(json);

                parser.parse();

                if (!collectErrors(result, parser.getErrorCollector())) {
                    result.accumulate("result", "success");
                }
            } catch (Exception je) {
                reportFailure(result, je);
            }
        } else {
            reportFailure(result, "No content found for 'json' parameter");
        }

        return HttpResponses.okJSON(result);

    }

    @SuppressWarnings("unused")
    @RequirePOST
    public HttpResponse doValidate(StaplerRequest req) {
        Jenkins.getInstance().checkPermission(READ);

        List<String> output = new ArrayList<>();

        String groovyAsString = req.getParameter("jenkinsfile");

        if (groovyAsString != null) {
            try {
                Converter.scriptToPipelineDef(groovyAsString);
                output.add("Jenkinsfile successfully validated.");
            } catch (Exception e) {
                output.add("Errors encountered validating Jenkinsfile:");
                output.addAll(ModelConverterAction.errorToStrings(e));
            }
        } else {
            output.add("No Jenkinsfile specified");
        }

        return HttpResponses.plainText(StringUtils.join(output, "\n"));
    }

    public static List<String> errorToStrings(Exception e) {
        List<String> output = new ArrayList<>();
        if (e instanceof MultipleCompilationErrorsException) {
            MultipleCompilationErrorsException ce = (MultipleCompilationErrorsException) e;
            for (Object o : ce.getErrorCollector().getErrors()) {
                if (o instanceof SyntaxErrorMessage) {
                    SyntaxErrorMessage s = (SyntaxErrorMessage) o;
                    StringWriter sw = new StringWriter();
                    PrintWriter pw = new PrintWriter(sw);
                    s.write(pw);
                    pw.close();
                    output.add(sw.toString());
                }
            }
        } else {
            output.add(e.getMessage());
        }

        return output;
    }
    /**
     * Checks the error collector for errors, and if there are any set the result as failure
     * @param result the result to mutate if so
     * @param errorCollector the collector of errors
     * @return {@code true} if any errors where collected.
     */
    private boolean collectErrors(JSONObject result, ErrorCollector errorCollector) {
        if (errorCollector.getErrorCount() > 0) {
            JSONArray errors = new JSONArray();
            for (String jsonError : errorCollector.errorsAsStrings()) {
                errors.add(jsonError);
            }
            reportFailure(result, errors);
            return true;
        }
        return false;
    }

    /**
     * Report result to be a failure message due to the given exception.
     *
     * @param result the result to mutate
     * @param e      the exception to report
     */
    private void reportFailure(JSONObject result, Exception e) {
        JSONArray errors = new JSONArray();
        if (e instanceof MultipleCompilationErrorsException) {
            MultipleCompilationErrorsException ce = (MultipleCompilationErrorsException)e;
            for (Object o : ce.getErrorCollector().getErrors()) {
                if (o instanceof SyntaxErrorMessage) {
                    errors.add(((SyntaxErrorMessage)o).getCause().getMessage());
                }
            }
        } else {
            errors.add(e.getMessage());
        }
        reportFailure(result, errors);
    }

    /**
     * Report result to be a failure message due to the given error message.
     *
     * @param result the result to mutate
     * @param message the error
     */
    private void reportFailure(JSONObject result, String message) {
        JSONArray errors = new JSONArray();
        errors.add(message);
        reportFailure(result, errors);
    }

    /**
     * Report result to be a failure message due to the given error messages.
     *
     * @param result the result to mutate
     * @param errors the errors
     */
    private void reportFailure(JSONObject result, JSONArray errors) {
        result.accumulate("result", "failure");
        result.accumulate("errors", errors);
    }
}
