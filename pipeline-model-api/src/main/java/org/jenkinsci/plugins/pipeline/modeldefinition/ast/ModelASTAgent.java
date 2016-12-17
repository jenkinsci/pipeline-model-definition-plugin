package org.jenkinsci.plugins.pipeline.modeldefinition.ast;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.jenkinsci.plugins.pipeline.modeldefinition.validator.ModelValidator;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents what context in which to run the build - i.e., which label to run on, what Docker agent to run in, etc.
 * Corresponds to Agent.
 *
 * @author Andrew Bayer
 */
public final class ModelASTAgent extends ModelASTElement {
    private Map<ModelASTKey, ModelASTValue> variables = new LinkedHashMap<ModelASTKey, ModelASTValue>();

    public ModelASTAgent(Object sourceLocation) {
        super(sourceLocation);
    }

    @Override
    public JSONArray toJSON() {
        final JSONArray a = new JSONArray();

        for (Map.Entry<ModelASTKey, ModelASTValue> entry: variables.entrySet()) {
            JSONObject o = new JSONObject();
            o.accumulate("key", entry.getKey().toJSON());
            o.accumulate("value", entry.getValue().toJSON());
            a.add(o);
        }
        return a;

    }

    @Override
    public void validate(ModelValidator validator) {
        validator.validateElement(this);

        for (Map.Entry<ModelASTKey, ModelASTValue> entry : variables.entrySet()) {
            entry.getKey().validate(validator);
            entry.getValue().validate(validator);
        }
    }

    public List<String> getKeyNames() {
        List<String> names = new ArrayList<>();
        for (ModelASTKey k : variables.keySet()) {
            names.add(k.getKey());
        }
        return names;
    }

    public ModelASTKey keyFromName(String name) {
        for (ModelASTKey k : variables.keySet()) {
            if (name.equals(k.getKey())) {
                return k;
            }
        }
        return null;
    }

    @Override
    public String toGroovy() {
        StringBuilder argStr = new StringBuilder();
        // TODO: Stop special-casing agent none.
        List<String> keys = getKeyNames();
        if (keys.size() == 1 && (keys.contains("none") || keys.contains("any"))) {
            argStr.append(keys.get(0));
        } else {
            argStr.append("{\n");
            for (Map.Entry<ModelASTKey, ModelASTValue> entry: variables.entrySet()) {
                argStr.append(entry.getKey().toGroovy()).append(" ").append(entry.getValue().toGroovy()).append("\n");
            }
            argStr.append("}");
        }

        return "agent " + argStr.toString() + "\n";
    }

    @Override
    public void removeSourceLocation() {
        super.removeSourceLocation();
        for (Map.Entry<ModelASTKey, ModelASTValue> entry : variables.entrySet()) {
            entry.getKey().removeSourceLocation();
            entry.getValue().removeSourceLocation();
        }
    }

    public Map<ModelASTKey, ModelASTValue> getVariables() {
        return variables;
    }

    public void setVariables(Map<ModelASTKey, ModelASTValue> variables) {
        this.variables = variables;
    }

    @Override
    public String toString() {
        return "ModelASTAgent{" +
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

        ModelASTAgent that = (ModelASTAgent) o;

        return getVariables() != null ? getVariables().equals(that.getVariables()) : that.getVariables() == null;

    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (getVariables() != null ? getVariables().hashCode() : 0);
        return result;
    }

}
