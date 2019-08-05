package org.jenkinsci.plugins.pipeline.modeldefinition.ast;

import net.sf.json.JSONObject;
import org.jenkinsci.plugins.pipeline.modeldefinition.validator.ModelValidator;

import javax.annotation.Nonnull;
import java.util.Objects;

/**
 * Represents the collection of {@code Stage}s to be executed in the build in parallel. Corresponds to {@code Stages}.
 * Used as a base to hold common functionality between parallel and matrix.
 *
 * @author Liam Newman
 */
public final class ModelASTMatrix extends ModelASTParallel {

    private ModelASTAxisContainer axes;
    private ModelASTExcludes excludes;

    public ModelASTMatrix(Object sourceLocation) {
        super(sourceLocation);
    }

    @Override
    public Object toJSON() {
        return  new JSONObject()
                .elementOpt("axes", toJSON(axes))
                .elementOpt("excludes", toJSON(excludes))
                .elementOpt("stages", nullIfEmpty(toJSONArray(getStages())));
    }

    @Override
    public void validate(final ModelValidator validator, boolean isWithinParallel) {
        super.validate(validator, true);
        validator.validateElement(this);
        validate(validator, axes, excludes);
    }

    @Override
    public String toGroovy() {
        StringBuilder children = new StringBuilder()
                .append(toGroovy(axes))
                .append(toGroovy(excludes))
                .append(toGroovyBlock("stages", getStages()));
        return "matrix {\n" + children.toString() + "}\n";
    }

    @Override
    public void removeSourceLocation() {
        super.removeSourceLocation();
        removeSourceLocationsFrom(axes);
        removeSourceLocationsFrom(excludes);
    }


    @Override
    public String toString() {
        return "ModelASTMatrix{" +
                "axes=" + axes +
                "excludes=" + excludes +
                "stages=" + getStages() +
                "}";
    }

    public ModelASTAxisContainer getAxes() {
        return axes;
    }

    public void setAxes(ModelASTAxisContainer axes) {
        this.axes = axes;
    }

    public ModelASTExcludes getExcludes() {
        return excludes;
    }

    public void setExcludes(ModelASTExcludes excludes) {
        this.excludes = excludes;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ModelASTMatrix)) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        ModelASTMatrix that = (ModelASTMatrix) o;
        return Objects.equals(getAxes(), that.getAxes()) &&
            Objects.equals(getExcludes(), that.getExcludes());
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), getAxes(), getExcludes());
    }
}
