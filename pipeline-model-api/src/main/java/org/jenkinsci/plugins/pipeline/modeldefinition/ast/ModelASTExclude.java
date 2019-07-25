package org.jenkinsci.plugins.pipeline.modeldefinition.ast;

import net.sf.json.JSONArray;
import org.jenkinsci.plugins.pipeline.modeldefinition.validator.ModelValidator;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 *
 *
 * @author Liam Newman
 */
public class ModelASTExclude extends ModelASTElement {

    private List<ModelASTExcludeAxis> axes = new ArrayList<>();

    public ModelASTExclude(Object sourceLocation) {
        super(sourceLocation);
    }

    @Override
    public JSONArray toJSON() {
        return toJSONArray(axes);
    }

    @Override
    public void validate(@Nonnull ModelValidator validator) {
        // nothing to validate on this element
        validate(validator, axes);
    }

    @Override
    public String toGroovy() {
        return toGroovyBlock("exclude", axes);
    }

    @Override
    public void removeSourceLocation() {
        super.removeSourceLocation();
        removeSourceLocationsFrom(axes);
    }

    @Override
    public String toString() {
        return "ModelASTExclude{" +
            "axes=" + axes +
            "}";
    }

    public List<ModelASTExcludeAxis> getExcludeAxes() {
        return axes;
    }

    public void setExcludeAxes(List<ModelASTExcludeAxis> axes) {
        this.axes = axes;
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ModelASTExclude)) return false;
        if (!super.equals(o)) return false;
        ModelASTExclude that = (ModelASTExclude) o;
        return Objects.equals(getExcludeAxes(), that.getExcludeAxes());
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), getExcludeAxes());
    }
}
