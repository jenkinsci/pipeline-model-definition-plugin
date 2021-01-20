package org.jenkinsci.plugins.pipeline.modeldefinition.ast;

import org.jenkinsci.plugins.pipeline.modeldefinition.validator.ModelValidator;

import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Represents the collection of {@code Stage}s to be executed in the build in parallel. Corresponds to {@code Stages}.
 * Used as a base to hold common functionality between parallel and matrix.
 *
 * @author Liam Newman
 */
public class ModelASTParallel extends ModelASTStages {

    public ModelASTParallel(Object sourceLocation) {
        super(sourceLocation);
    }

    @Override
    public void validate(@NonNull final ModelValidator validator) {
        validate(validator, true);
    }

    @Override
    public void validate(final ModelValidator validator, boolean isWithinParallel) {
        super.validate(validator, true);
        validator.validateElement(this);
    }

    @Override
    @NonNull
    public String toGroovy() {
        return toGroovyBlock("parallel", getStages());
    }

    @Override
    public String toString() {
        return "ModelASTParallel{" +
                "stages=" + getStages() +
                "}";
    }
}
