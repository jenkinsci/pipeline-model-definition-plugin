package org.jenkinsci.plugins.pipeline.modeldefinition.ast;

import net.sf.json.JSONObject;
import org.jenkinsci.plugins.pipeline.modeldefinition.validator.ModelValidator;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.Objects;

/**
 * Represents the collection of {@code Stage}s to be executed in the build in parallel. Corresponds to {@code Stages}.
 * Used as a base to hold common functionality between parallel and matrix.
 *
 * @author Liam Newman
 */
public final class ModelASTMatrix extends ModelASTStage {

    private ModelASTAxisContainer axes;
    private ModelASTExcludes excludes;

    public ModelASTMatrix(Object sourceLocation) {
        super(sourceLocation);
    }

    @Override
    public JSONObject toJSON() {
        JSONObject o = new JSONObject()
                .elementOpt("axes", toJSON(axes))
                .elementOpt("excludes", toJSON(excludes));
        o.putAll(super.toJSON());
        o.remove("name");
        return o;
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
            .append(childrenToGroovy());

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
                ", axes=" + axes +
                ", excludes=" + excludes +
                ", " + childrenToString() +
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
        if (o == null || getClass() != o.getClass()) {
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
