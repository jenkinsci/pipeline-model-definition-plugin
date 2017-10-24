package org.jenkinsci.plugins.pipeline.modeldefinition.ast;

import java.util.LinkedHashMap;
import java.util.Map;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.jenkinsci.plugins.pipeline.modeldefinition.validator.ModelValidator;

import javax.annotation.Nonnull;

/**
 * Represents a block of "foo = 'bar'" assignments to environment variables, corresponding to {@code Environment}.
 *
 * @author Andrew Bayer
 */
public final class ModelASTEnvironment extends ModelASTElement {
    private Map<ModelASTKey, ModelASTEnvironmentValue> variables = new LinkedHashMap<>();

    public ModelASTEnvironment(Object sourceLocation) {
        super(sourceLocation);
    }

    @Override
    public JSONArray toJSON() {
        final JSONArray a = new JSONArray();
        for (Map.Entry<ModelASTKey, ModelASTEnvironmentValue> entry: variables.entrySet()) {
            JSONObject o = new JSONObject();
            o.accumulate("key", entry.getKey().toJSON());
            o.accumulate("value", entry.getValue().toJSON());
            a.add(o);
        }
        return a;

    }

    @Override
    public void validate(@Nonnull final ModelValidator validator) {
        validator.validateElement(this);
        for (Map.Entry<ModelASTKey, ModelASTEnvironmentValue> entry : variables.entrySet()) {
            entry.getKey().validate(validator);
            entry.getValue().validate(validator);
        }
    }

    @Override
    public String toGroovy() {
        StringBuilder result = new StringBuilder("environment {\n");
        for (Map.Entry<ModelASTKey, ModelASTEnvironmentValue> entry : variables.entrySet()) {
            result.append(entry.getKey().toGroovy()).append(" = ").append(entry.getValue().toGroovy()).append('\n');
        }
        result.append("}\n");
        return result.toString();
    }

    @Override
    public void removeSourceLocation() {
        super.removeSourceLocation();
        for (Map.Entry<ModelASTKey, ModelASTEnvironmentValue> entry : variables.entrySet()) {
            entry.getKey().removeSourceLocation();
            entry.getValue().removeSourceLocation();
        }
    }

    public Map<ModelASTKey, ModelASTEnvironmentValue> getVariables() {
        return variables;
    }

    public void setVariables(Map<ModelASTKey, ModelASTEnvironmentValue> variables) {
        this.variables = variables;
    }

    @Override
    public String toString() {
        return "ModelASTEnvironment{" +
                "variables=" + variables +
                "}";
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

        ModelASTEnvironment that = (ModelASTEnvironment) o;

        return getVariables() != null ? getVariables().equals(that.getVariables()) : that.getVariables() == null;

    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (getVariables() != null ? getVariables().hashCode() : 0);
        return result;
    }
}
