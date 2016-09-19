package org.jenkinsci.plugins.pipeline.modeldefinition.ast

import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import org.jenkinsci.plugins.pipeline.modeldefinition.validator.ModelValidator

/**
 * Represents a single unnamed argument.
 *
 * @author Kohsuke Kawaguchi
 * @author Andrew Bayer
 */
@ToString(includeSuper = true, includeSuperProperties = true)
@EqualsAndHashCode(callSuper = true)
public final class ModelASTSingleArgument extends ModelASTArgumentList {
    ModelASTValue value

    public ModelASTSingleArgument(Object sourceLocation) {
        super(sourceLocation)
    }

    @Override
    public Object toJSON() {
        return value.toJSON()
    }

    @Override
    public void validate(ModelValidator validator) {
        // Nothing to immediately validate here
        value?.validate(validator)
    }

    @Override
    public String toGroovy() {
        return value.toGroovy()
    }

    @Override
    public void removeSourceLocation() {
        super.removeSourceLocation()
        value.removeSourceLocation()
    }

}
