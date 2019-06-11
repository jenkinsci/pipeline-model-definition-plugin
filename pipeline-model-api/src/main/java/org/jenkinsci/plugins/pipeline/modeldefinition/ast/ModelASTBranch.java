package org.jenkinsci.plugins.pipeline.modeldefinition.ast;

import net.sf.json.JSONObject;
import org.jenkinsci.plugins.pipeline.modeldefinition.validator.ModelValidator;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a branch of Pipeline steps to execute, either as part of a parallel block, or on its own.
 *
 * @author Kohsuke Kawaguchi
 * @author Andrew Bayer
 * @see ModelASTStage#branches
 */
public final class ModelASTBranch extends ModelASTElement {
    private String name;
    private List<ModelASTStep> steps = new ArrayList<>();

    public ModelASTBranch(Object sourceLocation) {
        super(sourceLocation);
    }

    @Override
    public JSONObject toJSON() {
        return new JSONObject()
                .accumulate("name", name)
                .accumulate("steps", toJSONArray(steps));
    }

    @Override
    public void validate(@Nonnull final ModelValidator validator) {
        validator.validateElement(this);
        validate(validator, steps);
    }

    @Override
    public String toGroovy() {
        return toGroovy(steps);
    }

    @Override
    public void removeSourceLocation() {
        super.removeSourceLocation();
        removeSourceLocationsFrom(steps);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<ModelASTStep> getSteps() {
        return steps;
    }

    public void setSteps(List<ModelASTStep> steps) {
        this.steps = steps;
    }

    @Override
    public String toString() {
        return "ModelASTBranch{" +
                "name='" + name + '\'' +
                ", steps=" + steps +
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

        ModelASTBranch that = (ModelASTBranch) o;

        if (getName() != null ? !getName().equals(that.getName()) : that.getName() != null) {
            return false;
        }
        return getSteps() != null ? getSteps().equals(that.getSteps()) : that.getSteps() == null;

    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (getName() != null ? getName().hashCode() : 0);
        result = 31 * result + (getSteps() != null ? getSteps().hashCode() : 0);
        return result;
    }
}
