package org.jenkinsci.plugins.pipeline.modeldefinition.ast;

import java.util.ArrayList;
import java.util.List;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.jenkinsci.plugins.pipeline.modeldefinition.validator.ModelValidator;

import javax.annotation.Nonnull;

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
        final JSONArray a = new JSONArray();
        for (ModelASTStep step: steps) {
            a.add(step.toJSON());
        }

        return new JSONObject().accumulate("name", name).accumulate("steps", a);
    }

    @Override
    public void validate(@Nonnull final ModelValidator validator) {
        validator.validateElement(this);
        for (ModelASTStep step: steps) {
            step.validate(validator);
        }
    }

    @Override
    public String toGroovy() {
        StringBuilder result = new StringBuilder();
        for (ModelASTStep step: steps) {
            result.append(step.toGroovy()).append("\n");
        }
        return result.toString();
    }

    @Override
    public void removeSourceLocation() {
        super.removeSourceLocation();
        for (ModelASTStep step: steps) {
            step.removeSourceLocation();
        }
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
