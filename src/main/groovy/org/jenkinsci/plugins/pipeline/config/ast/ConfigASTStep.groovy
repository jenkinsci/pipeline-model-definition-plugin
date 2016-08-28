package org.jenkinsci.plugins.pipeline.config.ast

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings
import groovy.transform.ToString
import net.sf.json.JSONObject
import org.jenkinsci.plugins.pipeline.config.validator.ConfigValidator

/**
 * Represents an individual step within any of the various blocks that can contain steps.
 *
 * @author Kohsuke Kawaguchi
 * @author Andrew Bayer
 */
@ToString(includeSuper = true, includeSuperProperties = true)
@SuppressFBWarnings(value="SE_NO_SERIALVERSIONID")
public class ConfigASTStep extends ConfigASTElement {
    /**
     * A list of step names which are banned from being executed within a step block.
     */
    public final List<String> blockedSteps = ["stage", "properties"]

    String name;
    ConfigASTArgumentList args;

    ConfigASTStep(Object sourceLocation) {
        super(sourceLocation)
    }

    @Override
    public JSONObject toJSON() {
        return new JSONObject()
            .accumulate("name", name)
            .accumulate("arguments", args.toJSON())
    }

    @Override
    public void validate(ConfigValidator validator) {
        validator.validateElement(this)

        args?.validate(validator)
    }

    @Override
    public String toGroovy() {
        return "${name}(${args.toGroovy()})"
    }
}
