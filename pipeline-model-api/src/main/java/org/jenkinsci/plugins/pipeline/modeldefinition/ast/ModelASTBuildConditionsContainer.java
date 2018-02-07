package org.jenkinsci.plugins.pipeline.modeldefinition.ast;

import java.util.ArrayList;
import java.util.List;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.jenkinsci.plugins.pipeline.modeldefinition.validator.ModelValidator;

import javax.annotation.Nonnull;

/**
 * Represents a list of {@code BuildCondition} and {@code StepsBlock} pairs to be called, depending on whether
 * the build condition is satisfied, at the end of the build or a stage.
 * Corresponds to {@code Notifications} or  {@code PostBuild}
 *
 * @author Robert Sandell &lt;rsandell@cloudbees.com&gt;.
 * @see ModelASTPostStage
 * @see ModelASTPostBuild
 */
public abstract class ModelASTBuildConditionsContainer extends ModelASTElement {
    private List<ModelASTBuildCondition> conditions = new ArrayList<>();
    private List<ModelASTPostWhenCondition> whenConditions = new ArrayList<>();

    protected ModelASTBuildConditionsContainer(Object sourceLocation) {
        super(sourceLocation);
    }

    public abstract String getName();

    @Override
    public JSONObject toJSON() {
        JSONObject o = new JSONObject();
        final JSONArray a = new JSONArray();
        for (ModelASTBuildCondition condition: conditions) {
            a.add(condition.toJSON());
        }
        o.accumulate("conditions", a);
        final JSONArray w = new JSONArray();
        for (ModelASTPostWhenCondition whenCondition : whenConditions) {
            w.add(whenCondition.toJSON());
        }
        o.accumulate("whenConditions", w);
        return o;
    }

    @Override
    public void validate(@Nonnull final ModelValidator validator) {
        validator.validateElement(this);
        for (ModelASTBuildCondition condition: conditions) {
            condition.validate(validator);
        }
        for (ModelASTPostWhenCondition w : whenConditions) {
            w.validate(validator);
        }
        super.validate(validator);
    }

    @Override
    public String toGroovy() {
        StringBuilder result = new StringBuilder(getName());
        result.append(" {\n");
        for (ModelASTBuildCondition condition : conditions) {
            result.append(condition.toGroovy()).append('\n');
        }
        for (ModelASTPostWhenCondition w : whenConditions) {
            result.append(w.toGroovy()).append("\n");
        }
        result.append("}\n");
        return result.toString();
    }

    @Override
    public void removeSourceLocation() {
        super.removeSourceLocation();
        for (ModelASTBuildCondition condition : conditions) {
            condition.removeSourceLocation();
        }
        for (ModelASTPostWhenCondition w : whenConditions) {
            w.removeSourceLocation();
        }
    }

    public List<ModelASTBuildCondition> getConditions() {
        return conditions;
    }

    public void setConditions(List<ModelASTBuildCondition> conditions) {
        this.conditions = conditions;
    }

    public List<ModelASTPostWhenCondition> getWhenConditions() {
        return whenConditions;
    }

    public void setWhenConditions(List<ModelASTPostWhenCondition> whenConditions) {
        this.whenConditions = whenConditions;
    }

    @Override
    public String toString() {
        return "ModelASTBuildConditionsContainer{" +
                "conditions=" + conditions +
                ", whenConditions=" + whenConditions +
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

        ModelASTBuildConditionsContainer that = (ModelASTBuildConditionsContainer) o;

        if (getWhenConditions() != null ? !getWhenConditions().equals(that.getWhenConditions()) : that.getWhenConditions() != null) {
            return false;
        }
        return getConditions() != null ? getConditions().equals(that.getConditions()) : that.getConditions() == null;

    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (getConditions() != null ? getConditions().hashCode() : 0);
        result = 31 * result + (getWhenConditions() != null ? getWhenConditions().hashCode() : 0);
        return result;
    }
}
