package org.jenkinsci.plugins.pipeline.modeldefinition.ast;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import groovy.transform.ToString;
import java.util.LinkedHashMap;
import java.util.Map;
import net.sf.json.JSONObject;
import org.jenkinsci.plugins.pipeline.modeldefinition.validator.ModelValidator;

/**
 * Represents an individual step within any of the various blocks that can contain steps.
 *
 * @author Kohsuke Kawaguchi
 * @author Andrew Bayer
 */
@ToString(includeSuper = true, includeSuperProperties = true)
@SuppressFBWarnings(value = "SE_NO_SERIALVERSIONID")
public class ModelASTStep extends ModelASTElement {
    private String name;
    private ModelASTArgumentList args;

    public static Map<String, String> blockedStepsBase() {
        LinkedHashMap<String, String> map = new LinkedHashMap<String, String>();
        map.put("stage", "The stage step cannot be used in Declarative Pipelines");
        map.put("properties", "The properties step cannot be used in Declarative Pipelines");
        map.put("parallel", "The parallel step can only be used as the only top-level step in a stage's step block");
        return map;
    }

    /**
     * A map of step names which are banned from being executed within a step block.
     *
     * @return the map of steps that are banned from being executed within a step block, keyed by step name.
     */
    public static Map<String, String> getBlockedSteps() {
        return blockedStepsBase();
    }

    public ModelASTStep(Object sourceLocation) {
        super(sourceLocation);
    }

    @Override
    public JSONObject toJSON() {
        JSONObject o = new JSONObject();
        o.accumulate("name", name);
        if (args != null) {
            o.accumulate("arguments", args.toJSON());
        }
        return o;
    }

    @Override
    public void validate(ModelValidator validator) {
        validator.validateElement(this);
        if (args != null) {
            args.validate(validator);
        }
    }

    @Override
    public String toGroovy() {
        return name + "(" + (args != null ? args.toGroovy() : "") + ")";
    }

    @Override
    public void removeSourceLocation() {
        super.removeSourceLocation();
        if (args != null) {
            args.removeSourceLocation();
        }
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public ModelASTArgumentList getArgs() {
        return args;
    }

    public void setArgs(ModelASTArgumentList args) {
        this.args = args;
    }

    @Override
    public String toString() {
        return "ModelASTStep{" +
                "name='" + name + '\'' +
                ", args=" + args +
                "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }

        ModelASTStep that = (ModelASTStep) o;

        if (getName() != null ? !getName().equals(that.getName()) : that.getName() != null) {
            return false;
        }
        return getArgs() != null ? getArgs().equals(that.getArgs()) : that.getArgs() == null;

    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (getName() != null ? getName().hashCode() : 0);
        result = 31 * result + (getArgs() != null ? getArgs().hashCode() : 0);
        return result;
    }
}
