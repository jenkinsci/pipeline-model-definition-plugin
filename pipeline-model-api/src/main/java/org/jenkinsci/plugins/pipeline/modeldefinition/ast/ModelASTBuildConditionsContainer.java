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

    protected ModelASTBuildConditionsContainer(Object sourceLocation) {
        super(sourceLocation);
    }

    public abstract String getName();

    @Override
    public JSONObject toJSON() {
        final JSONArray a = new JSONArray();
        for (ModelASTBuildCondition condition: conditions) {
            a.add(condition.toJSON());
        }
        return new JSONObject().accumulate("conditions", a);
    }

    @Override
    public void validate(@Nonnull final ModelValidator validator) {
        validator.validateElement(this);
        for (ModelASTBuildCondition condition: conditions) {
            condition.validate(validator);
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
        result.append("}\n");
        return result.toString();
    }

    @Override
    public void removeSourceLocation() {
        super.removeSourceLocation();
        for (ModelASTBuildCondition condition : conditions) {
            condition.removeSourceLocation();
        }
    }

    public List<ModelASTBuildCondition> getConditions() {
        return conditions;
    }

    public void setConditions(List<ModelASTBuildCondition> conditions) {
        this.conditions = conditions;
    }

    @Override
    public String toString() {
        return "ModelASTBuildConditionsContainer{" +
                "conditions=" + conditions +
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

        return getConditions() != null ? getConditions().equals(that.getConditions()) : that.getConditions() == null;

    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (getConditions() != null ? getConditions().hashCode() : 0);
        return result;
    }
}
