package org.jenkinsci.plugins.pipeline.modeldefinition.ast;

import java.util.ArrayList;
import java.util.List;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.jenkinsci.plugins.pipeline.modeldefinition.validator.ModelValidator;

/**
 * A container for one or more {@link ModelASTJobProperty}s
 *
 * @author Andrew Bayer
 */
public final class ModelASTJobProperties extends ModelASTElement {
    private List<ModelASTJobProperty> properties = new ArrayList<ModelASTJobProperty>();

    public ModelASTJobProperties(Object sourceLocation) {
        super(sourceLocation);
    }

    @Override
    public JSONObject toJSON() {
        final JSONArray a = new JSONArray();
        for (ModelASTJobProperty property : properties) {
            a.add(property.toJSON());
        }
        return new JSONObject().accumulate("properties", a);
    }

    @Override
    public void validate(final ModelValidator validator) {
        validator.validateElement(this);
        for (ModelASTJobProperty property : properties) {
            property.validate(validator);
        }
    }

    @Override
    public String toGroovy() {
        StringBuilder result = new StringBuilder("properties {\n");
        for (ModelASTJobProperty property : properties) {
            result.append(property.toGroovy()).append("\n");
        }
        result.append("}\n");
        return result.toString();
    }

    @Override
    public void removeSourceLocation() {
        super.removeSourceLocation();
        for (ModelASTJobProperty property : properties) {
            property.removeSourceLocation();
        }
    }

    public List<ModelASTJobProperty> getProperties() {
        return properties;
    }

    public void setProperties(List<ModelASTJobProperty> properties) {
        this.properties = properties;
    }

    @Override
    public String toString() {
        return "ModelASTJobProperties{" +
                "properties=" + properties +
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

        ModelASTJobProperties that = (ModelASTJobProperties) o;

        return getProperties() != null ? getProperties().equals(that.getProperties()) : that.getProperties() == null;

    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (getProperties() != null ? getProperties().hashCode() : 0);
        return result;
    }
}
