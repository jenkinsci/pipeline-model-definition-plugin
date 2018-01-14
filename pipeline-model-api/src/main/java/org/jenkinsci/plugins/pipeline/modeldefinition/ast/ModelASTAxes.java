package org.jenkinsci.plugins.pipeline.modeldefinition.ast;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.jenkinsci.plugins.pipeline.modeldefinition.validator.ModelValidator;

import javax.annotation.Nonnull;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;

/**
 * Represents a map of axis types to axis names (i.e., the name of the configured installation). Corresponds to
 * {@code axes}.
 *
 * @author Andrew Bayer
 */
public final class ModelASTAxes extends ModelASTElement {
    private Map<ModelASTKey, List<ModelASTValue>> axes = new LinkedHashMap<>();

    public ModelASTAxes(Object sourceLocation) {
        super(sourceLocation);
    }

    @Override
    public JSONArray toJSON() {
        final JSONArray a = new JSONArray();
        for (Map.Entry<ModelASTKey, List<ModelASTValue>> entry: axes.entrySet()) {
            JSONObject o = new JSONObject();
            o.accumulate("key", entry.getKey().toJSON());

            for(ModelASTValue value: entry.getValue()) {
                o.accumulate("value", value.toJSON());
            }

            a.add(o);
        }
        return a;
    }

    @Override
    public void validate(@Nonnull final ModelValidator validator) {
        validator.validateElement(this);
        for (Map.Entry<ModelASTKey, List<ModelASTValue>> entry : axes.entrySet()) {
            entry.getKey().validate(validator);

            for(ModelASTValue value: entry.getValue()) {
                value.validate(validator);
            }
        }
    }

    @Override
    public String toGroovy() {
        StringBuilder result = new StringBuilder("axes {\n");
        for (Map.Entry<ModelASTKey, List<ModelASTValue>> entry : axes.entrySet()) {
            result.append(entry.getKey().toGroovy()).append(" (");
            String comma = " ";

            for(ModelASTValue value: entry.getValue()) {
                result.append(comma).append(value.toGroovy());
                comma = ", ";
            }
            result.append(") \n");
        }
        result.append("}\n");
        return result.toString();
    }

    @Override
    public void removeSourceLocation() {
        super.removeSourceLocation();
        for (Map.Entry<ModelASTKey, List<ModelASTValue>> entry : axes.entrySet()) {
            entry.getKey().removeSourceLocation();

            for(ModelASTValue value: entry.getValue()) {
                value.removeSourceLocation();
            }
        }
    }

    public Map<ModelASTKey, List<ModelASTValue>> getaxes() {
        return axes;
    }

    public void setaxes(Map<ModelASTKey, List<ModelASTValue>> axes) {
        this.axes = axes;
    }

    @Override
    public String toString() {
        return "ModelASTaxes{" +
                "axes=" + axes +
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

        ModelASTAxes that = (ModelASTAxes) o;

        return getaxes() != null ? getaxes().equals(that.getaxes()) : that.getaxes() == null;

    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (getaxes() != null ? getaxes().hashCode() : 0);
        return result;
    }
}
