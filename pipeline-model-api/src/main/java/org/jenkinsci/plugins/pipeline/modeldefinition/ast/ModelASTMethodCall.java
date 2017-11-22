package org.jenkinsci.plugins.pipeline.modeldefinition.ast;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.jenkinsci.plugins.pipeline.modeldefinition.validator.ModelValidator;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

import javax.annotation.Nonnull;

/**
 * A representation of a method call, including its name and a list of {@link ModelASTMethodArg}s.
 *
 * This is used for things like job properties, triggers and parameter definitions, allowing parsing and validation of
 * the arguments in case they themselves are method calls.
 *
 * @author Andrew Bayer
 */
public class ModelASTMethodCall extends ModelASTElement implements ModelASTMethodArg {

    /**
     * Use {@code org.jenkinsci.plugins.pipeline.modeldefinition.validator.BlockedStepsAndMethodCalls.blockedInMethodCalls()} instead.
     *
     * @deprecated since 1.2-beta-4
     */
    @Deprecated
    @Restricted(NoExternalUse.class)
    public static Map<String, String> getBlockedSteps() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("node", Messages.ModelASTMethodCall_BlockedSteps_Node());
        map.putAll(ModelASTStep.getBlockedSteps());
        return map;
    }

    private String name;
    private List<ModelASTMethodArg> args = new ArrayList<>();

    public ModelASTMethodCall(Object sourceLocation) {
        super(sourceLocation);
    }

    @Override
    public JSONObject toJSON() {
        final JSONArray a = new JSONArray();
        for (ModelASTMethodArg arg: args) {
            a.add(arg.toJSON());
        }
        return new JSONObject().accumulate("name", name).accumulate("arguments", a);
    }

    @Override
    public void validate(@Nonnull final ModelValidator validator) {
        validator.validateElement(this);
        for (ModelASTMethodArg arg : args) {
            arg.validate(validator);
        }
    }

    @Override
    public String toGroovy() {
        StringBuilder result = new StringBuilder(name);
        result.append('(');
        boolean first = true;
        for (ModelASTMethodArg arg : args) {
            if (first) {
                first = false;
            } else {
                result.append(", ");
            }
            result.append(arg.toGroovy());
        }
        result.append(')');
        return result.toString();
    }

    @Override
    public void removeSourceLocation() {
        super.removeSourceLocation();
        for (ModelASTMethodArg arg : args) {
            arg.removeSourceLocation();
        }
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<ModelASTMethodArg> getArgs() {
        return args;
    }

    public void setArgs(List<ModelASTMethodArg> args) {
        this.args = args;
    }

    @Override
    public String toString() {
        return "ModelASTMethodCall{" +
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

        ModelASTMethodCall that = (ModelASTMethodCall) o;

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
