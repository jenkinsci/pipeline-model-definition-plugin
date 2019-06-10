package org.jenkinsci.plugins.pipeline.modeldefinition.ast;

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

    public ModelASTMatrix(Object sourceLocation) {
        super(sourceLocation);
    }

    @Override
    public void validate(final ModelValidator validator, boolean isWithinParallel) {
        super.validate(validator, true);
        validator.validateElement(this);
    }

    @Override
    public String toGroovy() {
        return toGroovyBlock("matrix", getStages());
    }

    @Override
    public String toString() {
        return "ModelASTMatrix{" +
                "stages=" + getStages() +
                "}";
    }

    public ModelASTAxisContainer getAxes() {
        return axes;
    }

    public void setAxes(ModelASTAxisContainer axes) {
        this.axes = axes;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ModelASTMatrix)) return false;
        if (!super.equals(o)) return false;
        ModelASTMatrix that = (ModelASTMatrix) o;
        return Objects.equals(getAxes(), that.getAxes());
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), getAxes());
    }
}
