package org.jenkinsci.plugins.pipeline.modeldefinition.ast;

import org.jenkinsci.plugins.pipeline.modeldefinition.validator.ModelValidator;

/**
 * Represents a list of {@code BuildCondition} and {@code StepsBlock} pairs to be called, depending on whether the build
 * condition is satisfied, at the end of the build. Corresponds to {@code Notifications}
 *
 * @author Andrew Bayer
 */
public final class ModelASTNotifications extends ModelASTBuildConditionsContainer {
    public ModelASTNotifications(Object sourceLocation) {
        super(sourceLocation);
    }

    @Override
    public String getName() {
        return "notifications";
    }

    @Override
    public String toString() {
        return "ModelASTNotifications{" +
                "conditions=" + getConditions() +
                "}";
    }

    @Override
    public void validate(ModelValidator validator) {
        validator.validateElement(this);
        super.validate(validator);
    }
}
