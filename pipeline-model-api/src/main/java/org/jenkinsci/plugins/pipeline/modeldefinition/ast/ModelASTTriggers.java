package org.jenkinsci.plugins.pipeline.modeldefinition.ast;

import java.util.ArrayList;
import java.util.List;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.jenkinsci.plugins.pipeline.modeldefinition.validator.ModelValidator;

import javax.annotation.Nonnull;

/**
 * A container for one or more {@link ModelASTTrigger}s.
 *
 * @author Andrew Bayer
 */
public final class ModelASTTriggers extends ModelASTElement {
    private List<ModelASTTrigger> triggers = new ArrayList<>();

    public ModelASTTriggers(Object sourceLocation) {
        super(sourceLocation);
    }

    @Override
    public JSONObject toJSON() {
        final JSONArray a = new JSONArray();
        for (ModelASTTrigger trigger: triggers) {
            a.add(trigger.toJSON());
        }
        return new JSONObject().accumulate("triggers", a);
    }

    @Override
    public void validate(@Nonnull final ModelValidator validator) {
        validator.validateElement(this);
        for (ModelASTTrigger trigger : triggers) {
            trigger.validate(validator);
        }
    }

    @Override
    public String toGroovy() {
        StringBuilder result = new StringBuilder("triggers {\n");
        for (ModelASTTrigger trigger : triggers) {
            result.append(trigger.toGroovy()).append('\n');
        }
        result.append("}\n");
        return result.toString();
    }

    @Override
    public void removeSourceLocation() {
        super.removeSourceLocation();
        for (ModelASTTrigger trigger : triggers) {
            trigger.removeSourceLocation();
        }
    }

    public List<ModelASTTrigger> getTriggers() {
        return triggers;
    }

    public void setTriggers(List<ModelASTTrigger> triggers) {
        this.triggers = triggers;
    }

    @Override
    public String toString() {
        return "ModelASTTriggers{" +
                "triggers=" + triggers +
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

        ModelASTTriggers that = (ModelASTTriggers) o;

        return getTriggers() != null ? getTriggers().equals(that.getTriggers()) : that.getTriggers() == null;

    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (getTriggers() != null ? getTriggers().hashCode() : 0);
        return result;
    }
}
