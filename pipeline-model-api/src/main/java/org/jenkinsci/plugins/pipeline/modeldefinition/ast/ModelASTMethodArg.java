package org.jenkinsci.plugins.pipeline.modeldefinition.ast;

import org.jenkinsci.plugins.pipeline.modeldefinition.validator.ModelValidator;

/**
 * A marker for classes that can serve as the argument for a method, either as part of a {@link ModelASTKeyValueOrMethodCallPair} or
 * on its own in a list.
 *
 * @author Andrew Bayer
 */
public interface ModelASTMethodArg {
    String toGroovy();

    Object toJSON();

    void validate(ModelValidator validator);

    void removeSourceLocation();
}
