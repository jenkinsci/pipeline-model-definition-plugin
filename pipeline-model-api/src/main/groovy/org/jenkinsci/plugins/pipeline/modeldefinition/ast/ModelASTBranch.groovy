package org.jenkinsci.plugins.pipeline.modeldefinition.ast

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import net.sf.json.JSONArray
import net.sf.json.JSONObject
import org.jenkinsci.plugins.pipeline.modeldefinition.validator.ModelValidator

/**
 * Represents a branch of Pipeline steps to execute, either as part of a parallel block, or on its own.
 *
 * @author Kohsuke Kawaguchi
 * @author Andrew Bayer
 * @see ModelASTStage#branches
 */
@ToString(includeSuper = true, includeSuperProperties = true)
@EqualsAndHashCode(callSuper = true)
@SuppressFBWarnings(value="SE_NO_SERIALVERSIONID")
public final class ModelASTBranch extends ModelASTElement {
    String name
    List<ModelASTStep> steps = []

    public ModelASTBranch(Object sourceLocation) {
        super(sourceLocation)
    }

    @Override
    public JSONObject toJSON() {
        JSONArray a = new JSONArray()
        steps.each { s ->
            a.add(s.toJSON())
        }

        return new JSONObject()
                .accumulate("name",name)
                .accumulate("steps",a)
    }

    @Override
    public void validate(ModelValidator validator) {
        validator.validateElement(this)
        steps.each { s ->
            s?.validate(validator)
        }
    }

    @Override
    public String toGroovy() {
        return steps.collect { it.toGroovy() }.join("\n") + "\n"
    }

    @Override
    public void removeSourceLocation() {
        super.removeSourceLocation()
        steps.each { it.removeSourceLocation() }
    }
}
