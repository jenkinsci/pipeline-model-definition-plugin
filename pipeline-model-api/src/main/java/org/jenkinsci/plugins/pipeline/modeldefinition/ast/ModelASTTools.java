package org.jenkinsci.plugins.pipeline.modeldefinition.ast;

import java.util.LinkedHashMap;
import java.util.Map;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.jenkinsci.plugins.pipeline.modeldefinition.validator.ModelValidator;

import javax.annotation.Nonnull;

/**
 * Represents a map of tool types to tool names (i.e., the name of the configured installation). Corresponds to
 * {@code Tools}.
 *
 * @author Andrew Bayer
 */
public final class ModelASTTools extends ModelASTElement {
    private Map<ModelASTKey, ModelASTValue> tools = new LinkedHashMap<>();

    public ModelASTTools(Object sourceLocation) {
        super(sourceLocation);
    }

    @Override
    public JSONArray toJSON() {
        final JSONArray a = new JSONArray();
        for (Map.Entry<ModelASTKey, ModelASTValue> entry: tools.entrySet()) {
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
        for (Map.Entry<ModelASTKey, ModelASTValue> entry : tools.entrySet()) {
            entry.getKey().validate(validator);
            entry.getValue().validate(validator);
        }
    }

    @Override
    public String toGroovy() {
        StringBuilder result = new StringBuilder("tools {\n");
        for (Map.Entry<ModelASTKey, ModelASTValue> entry : tools.entrySet()) {
            result.append(entry.getKey().toGroovy()).append(' ').append(entry.getValue().toGroovy()).append('\n');
        }
        result.append("}\n");
        return result.toString();
    }

    @Override
    public void removeSourceLocation() {
        super.removeSourceLocation();
        for (Map.Entry<ModelASTKey, ModelASTValue> entry : tools.entrySet()) {
            entry.getKey().removeSourceLocation();
            entry.getValue().removeSourceLocation();
        }
    }

    public Map<ModelASTKey, ModelASTValue> getTools() {
        return tools;
    }

    public void setTools(Map<ModelASTKey, ModelASTValue> tools) {
        this.tools = tools;
    }

    @Override
    public String toString() {
        return "ModelASTTools{" +
                "tools=" + tools +
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

        ModelASTTools that = (ModelASTTools) o;

        return getTools() != null ? getTools().equals(that.getTools()) : that.getTools() == null;

    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (getTools() != null ? getTools().hashCode() : 0);
        return result;
    }
}
