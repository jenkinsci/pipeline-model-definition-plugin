package org.jenkinsci.plugins.pipeline.config.ast

import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import org.jenkinsci.plugins.pipeline.config.validator.ConfigValidator

/**
 * Represents a single unnamed argument.
 *
 * @author Kohsuke Kawaguchi
 * @author Andrew Bayer
 */
@ToString(includeSuper = true, includeSuperProperties = true)
@EqualsAndHashCode(callSuper = true)
public final class ConfigASTSingleArgument extends ConfigASTArgumentList {
    ConfigASTValue value

    public ConfigASTSingleArgument(Object sourceLocation) {
        super(sourceLocation)
    }

    @Override
    public Object toJSON() {
        return value.toJSON()
    }

    @Override
    public void validate(ConfigValidator validator) {
        // Nothing to immediately validate here
        value?.validate(validator)
    }

    @Override
    public String toGroovy() {
        return value.toGroovy()
    }

}
