package org.jenkinsci.plugins.pipeline.modeldefinition.ast;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.annotation.Nonnull;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.jenkinsci.plugins.pipeline.modeldefinition.validator.ModelValidator;

/**
 * Represents the named parameters for a step in a map of {@link ModelASTKey}s and {@link ModelASTValue}s.
 *
 * @author Kohsuke Kawaguchi
 * @author Andrew Bayer
 */
@SuppressFBWarnings(value = "SE_NO_SERIALVERSIONID")
public final class ModelASTNamedArgumentList extends ModelASTArgumentList {
    private Map<ModelASTKey, ModelASTValue> arguments = new LinkedHashMap<>();

    public ModelASTNamedArgumentList(Object sourceLocation) {
        super(sourceLocation);
    }

    @Override
    public JSONArray toJSON() {
        final JSONArray a = new JSONArray();
        for (Map.Entry<ModelASTKey, ModelASTValue> entry: arguments.entrySet()) {
            JSONObject o = new JSONObject();
            o.accumulate("key", entry.getKey().toJSON());
            o.accumulate("value", entry.getValue().toJSON());
            a.add(o);
        }
        return a;
    }

    /**
     * Checks if a given key name is present.
     *
     * @param keyName The name of a key to check for.
     * @return True if a {@link ModelASTKey} with that name is present in the map.
     */
    public boolean containsKeyName(@Nonnull String keyName) {
        for (ModelASTKey key: arguments.keySet()) {
            if (keyName.equals(key.getKey())) return true;
        }
        return false;
    }

    public ModelASTKey keyForName(@Nonnull String keyName) {
        for (ModelASTKey key : arguments.keySet()) {
            if (keyName.equals(key.getKey())) {
                return key;
            }
        }
        return null;
    }

    public ModelASTValue valueForName(@Nonnull String keyName) {
        if (containsKeyName(keyName)) {
            return arguments.get(keyForName(keyName));
        }
        return null;
    }

    @Override
    public void validate(@Nonnull final ModelValidator validator) {
        for (Map.Entry<ModelASTKey, ModelASTValue> entry : arguments.entrySet()) {
            entry.getKey().validate(validator);
            entry.getValue().validate(validator);
        }
    }

    @Override
    public String toGroovy() {
        StringBuilder result = new StringBuilder();
        boolean first = true;
        for (Map.Entry<ModelASTKey, ModelASTValue> entry : arguments.entrySet()) {
            if (first) {
                first = false;
            } else {
                result.append(", ");
            }
            result.append(entry.getKey().toGroovy()).append(": ").append(entry.getValue().toGroovy());
        }
        return result.toString();
    }

    @Override
    public void removeSourceLocation() {
        super.removeSourceLocation();
        for (Map.Entry<ModelASTKey, ModelASTValue> entry : arguments.entrySet()) {
            entry.getKey().removeSourceLocation();
            entry.getValue().removeSourceLocation();
        }
    }

    public Map<ModelASTKey, ModelASTValue> getArguments() {
        return arguments;
    }

    public void setArguments(Map<ModelASTKey, ModelASTValue> arguments) {
        this.arguments = arguments;
    }

    @Override
    public Map<String,?> argListToMap() {
        Map<String,Object> m = new LinkedHashMap<>();

        for (Map.Entry<ModelASTKey,ModelASTValue> entry : arguments.entrySet()) {
            m.put(entry.getKey().getKey(), entry.getValue().getValue());
        }

        return m;
    }

    @Override
    public String toString() {
        return "ModelASTNamedArgumentList{" +
                "arguments=" + arguments +
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

        ModelASTNamedArgumentList that = (ModelASTNamedArgumentList) o;

        return getArguments() != null ? getArguments().equals(that.getArguments()) : that.getArguments() == null;

    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (getArguments() != null ? getArguments().hashCode() : 0);
        return result;
    }
}
