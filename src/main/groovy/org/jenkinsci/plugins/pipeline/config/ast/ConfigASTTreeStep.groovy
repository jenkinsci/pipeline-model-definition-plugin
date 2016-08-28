package org.jenkinsci.plugins.pipeline.config.ast

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings
import groovy.transform.ToString
import net.sf.json.JSONArray
import net.sf.json.JSONObject
import org.jenkinsci.plugins.pipeline.config.validator.ConfigValidator

/**
 * Represents the special case of a step that has a sub-block of further steps within it.
 *
 * @author Kohsuke Kawaguchi
 * @author Andrew Bayer
 */
@ToString(includeSuper = true, includeSuperProperties = true)
@SuppressFBWarnings(value="SE_NO_SERIALVERSIONID")
public class ConfigASTTreeStep extends ConfigASTStep {
    List<ConfigASTStep> children = []

    ConfigASTTreeStep(Object sourceLocation) {
        super(sourceLocation);
    }

    @Override
    public JSONObject toJSON() {
        JSONArray a = new JSONArray()
        children.each { s ->
            a.add(s.toJSON())
        }
        return super.toJSON().accumulate("children", a)
    }

    @Override
    public void validate(ConfigValidator validator) {
        super.validate(validator)

        children.each { c ->
            c?.validate(validator)
        }
    }

    @Override
    public String toGroovy() {
        String retString = "${name}(${args.toGroovy()}) {\n"
        children.each { c ->
            retString += "${c.toGroovy()}\n"
        }
        retString += "}\n"

        return retString
    }

}
