package org.jenkinsci.plugins.pipeline.modeldefinition.ast;

import org.jenkinsci.plugins.pipeline.modeldefinition.validator.ModelValidator;

import javax.annotation.Nonnull;

/**
 * Represents a list of {@code BuildCondition} and {@code StepsBlock} pairs to be called, depending on whether the build
 * condition is satisfied, at the end of the build, but before the {@code Notifications}. Corresponds to {@code PostBuild}.
 *
 * @author Andrew Bayer
 */
public final class ModelASTPostBuild extends ModelASTBuildConditionsContainer {
    public ModelASTPostBuild(Object sourceLocation) {
        super(sourceLocation);
    }

    @Override
    public java.lang.String getName() {
        return "post";
    }

    @Override
    public String toString() {
        return "ModelASTPostBuild{" +
                "conditions=" + getConditions() +
                "}";
    }

    @Override
    public void validate(@Nonnull ModelValidator validator) {
        validator.validateElement(this);
        super.validate(validator);
    }
}
