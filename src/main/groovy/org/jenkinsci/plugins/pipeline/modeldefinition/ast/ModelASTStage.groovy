package org.jenkinsci.plugins.pipeline.modeldefinition.ast

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import net.sf.json.JSONArray
import net.sf.json.JSONObject
import org.jenkinsci.plugins.pipeline.modeldefinition.model.Stage
import org.jenkinsci.plugins.pipeline.modeldefinition.validator.ModelValidator

/**
 * Represents an individual {@link Stage} and the {@link ModelASTBranch}s it may contain.
 *
 * @author Kohsuke Kawaguchi
 * @author Andrew Bayer
 * @see ModelASTPipelineDef
 */
@ToString(includeSuper = true, includeSuperProperties = true)
@EqualsAndHashCode(callSuper = true)
@SuppressFBWarnings(value="SE_NO_SERIALVERSIONID")
public final class ModelASTStage extends ModelASTElement {
    String name
    List<ModelASTBranch> branches = []

    public ModelASTStage(Object sourceLocation) {
        super(sourceLocation)
    }

    @Override
    public JSONObject toJSON() {
        JSONArray a = new JSONArray()
        branches.each { br ->
            a.add(br.toJSON())
        }
        return new JSONObject()
                .accumulate("name",name)
                .accumulate("branches",a)
    }

    @Override
    public void validate(ModelValidator validator) {
        validator.validateElement(this)
        branches.each { b ->
            b?.validate(validator)
        }
    }

    @Override
    public String toGroovy() {
        StringBuilder retString = new StringBuilder()
        retString.append("stage(\"${name}\") {\n")

        if (branches.size() > 1) {
            retString.append("parallel(\n")
            List<String> branchStrings = branches.collect { b ->
                "${b.name}: {\n${b.toGroovy()}\n}"
            }
            retString.append(branchStrings.join(",\n"))
            retString.append(")\n")
        } else if (branches.size() == 1) {
            retString.append(branches.get(0).toGroovy())
        }

        retString.append("}\n")

        return retString.toString()
    }

    @Override
    public void removeSourceLocation() {
        super.removeSourceLocation()
        branches.each { b ->
            b.removeSourceLocation()
        }
    }
}
