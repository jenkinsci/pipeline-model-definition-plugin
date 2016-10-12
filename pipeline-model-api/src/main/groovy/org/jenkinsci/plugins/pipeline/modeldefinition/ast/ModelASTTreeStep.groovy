package org.jenkinsci.plugins.pipeline.modeldefinition.ast

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings
import groovy.transform.ToString
import net.sf.json.JSONArray
import net.sf.json.JSONObject
import org.jenkinsci.plugins.pipeline.modeldefinition.validator.ModelValidator

/**
 * Represents the special case of a step that has a sub-block of further steps within it.
 *
 * @author Kohsuke Kawaguchi
 * @author Andrew Bayer
 */
@ToString(includeSuper = true, includeSuperProperties = true)
@SuppressFBWarnings(value="SE_NO_SERIALVERSIONID")
public class ModelASTTreeStep extends ModelASTStep {
    List<ModelASTStep> children = []

    ModelASTTreeStep(Object sourceLocation) {
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
    public void validate(ModelValidator validator) {
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

    @Override
    public void removeSourceLocation() {
        super.removeSourceLocation()
        children.each { c ->
            c.removeSourceLocation()
        }
    }

}
