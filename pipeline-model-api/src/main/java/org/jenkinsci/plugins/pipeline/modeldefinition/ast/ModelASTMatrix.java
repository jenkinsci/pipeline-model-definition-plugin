package org.jenkinsci.plugins.pipeline.modeldefinition.ast;

import org.jenkinsci.plugins.pipeline.modeldefinition.validator.ModelValidator;

import javax.annotation.Nonnull;

/**
 * Represents the collection of {@code Stage}s to be executed in the build in parallel. Corresponds to {@code Stages}.
 * Used as a base to hold common functionality between parallel and matrix.
 *
 * @author Liam Newman
 */
public final class ModelASTMatrix extends ModelASTParallel {

    public ModelASTMatrix(Object sourceLocation) {
        super(sourceLocation);
    }

    @Override
    public void validate(final ModelValidator validator, boolean isWithinParallel) {
        super.validate(validator, true);
        validator.validateElement(this);
    }

    @Override
    public String toString() {
        return "ModelASTMatrix{" +
                "stages=" + getStages() +
                "}";
    }
}
