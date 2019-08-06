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
public class ModelASTExcludes extends ModelASTElement {

    private List<ModelASTExclude> excludes = new ArrayList<>();

    public ModelASTExcludes(Object sourceLocation) {
        super(sourceLocation);
    }

    @Override
    public JSONArray toJSON() {
        return toJSONArray(excludes);
    }

    @Override
    public void validate(@Nonnull ModelValidator validator) {
        validator.validateElement(this);
        validate(validator, excludes);
    }

    @Override
    public String toGroovy() {
        return toGroovyBlock("excludes", excludes);
    }

    @Override
    public void removeSourceLocation() {
        super.removeSourceLocation();
        removeSourceLocationsFrom(excludes);
    }

    @Override
    public String toString() {
        return "ModelASTExcludes{" +
                "excludes=" + excludes +
                "}";
    }

    public List<ModelASTExclude> getExcludes() {
        return excludes;
    }

    public void setExcludes(List<ModelASTExclude> excludes) {
        this.excludes = excludes;
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ModelASTExcludes)) return false;
        if (!super.equals(o)) return false;
        ModelASTExcludes that = (ModelASTExcludes) o;
        return Objects.equals(getExcludes(), that.getExcludes());
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), getExcludes());
    }
}
