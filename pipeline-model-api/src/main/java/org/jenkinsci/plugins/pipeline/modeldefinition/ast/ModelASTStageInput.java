/*
 * The MIT License
 *
 * Copyright (c) 2017, CloudBees, Inc.
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

package org.jenkinsci.plugins.pipeline.modeldefinition.ast;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.jenkinsci.plugins.pipeline.modeldefinition.validator.ModelValidator;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

/**
 * An input step for a single stage.
 *
 * @author Andrew Bayer
 */
public final class ModelASTStageInput extends ModelASTElement {
    private List<ModelASTBuildParameter> parameters = new ArrayList<>();
    private ModelASTValue message;
    private ModelASTValue id;
    private ModelASTValue ok;
    private ModelASTValue submitter;
    private ModelASTValue submitterParameter;

    public ModelASTStageInput(Object sourceLocation) {
        super(sourceLocation);
    }

    @Override
    public JSONObject toJSON() {
        final JSONObject o = new JSONObject();
        o.accumulate("message", message.toJSON());

        if (id != null) {
            o.accumulate("id", id.toJSON());
        }
        if (ok != null) {
            o.accumulate("ok", ok.toJSON());
        }
        if (submitter != null) {
            o.accumulate("submitter", submitter.toJSON());
        }
        if (submitterParameter != null) {
            o.accumulate("submitterParameter", submitterParameter.toJSON());
        }
        if (!parameters.isEmpty()) {
            final JSONObject p = new JSONObject();
            final JSONArray a = new JSONArray();
            for (ModelASTBuildParameter parameter : parameters) {
                a.add(parameter.toJSON());
            }
            // Redundancy due to how we parse parameters in JSON. This makes top-level and input parameters JSON consistent.
            p.accumulate("parameters", a);
            o.accumulate("parameters", p);
        }
        return o;
    }

    @Override
    public void validate(@Nonnull final ModelValidator validator) {
        validator.validateElement(this);
        for (ModelASTBuildParameter parameter: parameters) {
            parameter.validate(validator);
        }
    }

    @Override
    public String toGroovy() {
        StringBuilder result = new StringBuilder("input {\n");
        result.append("message ").append(message.toGroovy()).append("\n");
        if (id != null) {
            result.append("id ").append(id.toGroovy()).append("\n");
        }
        if (ok != null) {
            result.append("ok ").append(ok.toGroovy()).append("\n");
        }
        if (submitter != null) {
            result.append("submitter ").append(submitter.toGroovy()).append("\n");
        }
        if (submitterParameter != null) {
            result.append("submitterParameter ").append(submitterParameter.toGroovy()).append("\n");
        }
        if (!parameters.isEmpty()) {
            result.append("parameters {\n");
            for (ModelASTBuildParameter parameter : parameters) {
                result.append(parameter.toGroovy()).append("\n");
            }
            result.append("}\n");
        }
        result.append("}\n");
        return result.toString();
    }

    @Override
    public void removeSourceLocation() {
        super.removeSourceLocation();
        message.removeSourceLocation();
        if (id != null) {
            id.removeSourceLocation();
        }
        if (ok != null) {
            ok.removeSourceLocation();
        }
        if (submitter != null) {
            submitter.removeSourceLocation();
        }
        if (submitterParameter != null) {
            submitterParameter.removeSourceLocation();
        }
        for (ModelASTBuildParameter parameter: parameters) {
            parameter.removeSourceLocation();
        }
    }

    public List<ModelASTBuildParameter> getParameters() {
        return parameters;
    }

    public void setParameters(List<ModelASTBuildParameter> parameters) {
        this.parameters = parameters;
    }

    public ModelASTValue getMessage() {
        return message;
    }

    public void setMessage(ModelASTValue message) {
        this.message = message;
    }

    public ModelASTValue getId() {
        return id;
    }

    public void setId(ModelASTValue id) {
        this.id = id;
    }

    public ModelASTValue getOk() {
        return ok;
    }

    public void setOk(ModelASTValue ok) {
        this.ok = ok;
    }

    public ModelASTValue getSubmitter() {
        return submitter;
    }

    public void setSubmitter(ModelASTValue submitter) {
        this.submitter = submitter;
    }

    public ModelASTValue getSubmitterParameter() {
        return submitterParameter;
    }

    public void setSubmitterParameter(ModelASTValue submitterParameter) {
        this.submitterParameter = submitterParameter;
    }

    @Override
    public String toString() {
        return "ModelASTStageInput{" +
                "message=" + message +
                ",id=" + id +
                ",ok=" + ok +
                ",submitter=" + submitter +
                ",submitterParameter=" + submitterParameter +
                ",parameters=" + parameters +
                ", " + super.toString() + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }

        ModelASTStageInput that = (ModelASTStageInput) o;

        if (getMessage() != null ? !getMessage().equals(that.getMessage()) : that.getMessage() != null) {
            return false;
        }
        if (getId() != null ? !getId().equals(that.getId()) : that.getId() != null) {
            return false;
        }
        if (getOk() != null ? !getOk().equals(that.getOk()) : that.getOk() != null) {
            return false;
        }
        if (getSubmitter() != null ? !getSubmitter().equals(that.getSubmitter()) : that.getSubmitter() != null) {
            return false;
        }
        if (getSubmitterParameter() != null ? !getSubmitterParameter().equals(that.getSubmitterParameter()) : that.getSubmitterParameter() != null) {
            return false;
        }

        return getParameters() != null ? getParameters().equals(that.getParameters()) : that.getParameters() == null;

    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (getMessage() != null ? getMessage().hashCode() : 0);
        result = 31 * result + (getId() != null ? getId().hashCode() : 0);
        result = 31 * result + (getOk() != null ? getOk().hashCode() : 0);
        result = 31 * result + (getSubmitter() != null ? getSubmitter().hashCode() : 0);
        result = 31 * result + (getSubmitterParameter() != null ? getSubmitterParameter().hashCode() : 0);
        result = 31 * result + (getParameters() != null ? getParameters().hashCode() : 0);
        return result;
    }
}
