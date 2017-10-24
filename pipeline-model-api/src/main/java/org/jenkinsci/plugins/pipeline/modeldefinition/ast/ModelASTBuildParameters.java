package org.jenkinsci.plugins.pipeline.modeldefinition.ast;

import java.util.ArrayList;
import java.util.List;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.jenkinsci.plugins.pipeline.modeldefinition.validator.ModelValidator;

import javax.annotation.Nonnull;

/**
 * A container for one or more {@link ModelASTBuildParameter}s.
 *
 * @author Andrew Bayer
 */
public final class ModelASTBuildParameters extends ModelASTElement {
    private List<ModelASTBuildParameter> parameters = new ArrayList<>();

    public ModelASTBuildParameters(Object sourceLocation) {
        super(sourceLocation);
    }

    @Override
    public JSONObject toJSON() {
        final JSONArray a = new JSONArray();
        for (ModelASTBuildParameter parameter : parameters) {
            a.add(parameter.toJSON());
        }
        return new JSONObject().accumulate("parameters", a);
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
        StringBuilder result = new StringBuilder("parameters {\n");
        for (ModelASTBuildParameter parameter : parameters) {
            result.append(parameter.toGroovy()).append("\n");
        }
        result.append("}\n");
        return result.toString();
    }

    @Override
    public void removeSourceLocation() {
        super.removeSourceLocation();
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

    @Override
    public String toString() {
        return "ModelASTBuildParameters{" +
                "parameters=" + parameters +
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

        ModelASTBuildParameters that = (ModelASTBuildParameters) o;

        return getParameters() != null ? getParameters().equals(that.getParameters()) : that.getParameters() == null;

    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (getParameters() != null ? getParameters().hashCode() : 0);
        return result;
    }
}
