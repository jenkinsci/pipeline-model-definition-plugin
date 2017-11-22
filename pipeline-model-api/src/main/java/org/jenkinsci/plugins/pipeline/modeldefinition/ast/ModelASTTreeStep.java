package org.jenkinsci.plugins.pipeline.modeldefinition.ast;

import java.util.ArrayList;
import java.util.List;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.jenkinsci.plugins.pipeline.modeldefinition.validator.ModelValidator;

import javax.annotation.Nonnull;

/**
 * Represents the special case of a step that has a sub-block of further steps within it.
 *
 * @author Kohsuke Kawaguchi
 * @author Andrew Bayer
 */
public class ModelASTTreeStep extends ModelASTStep {
    private List<ModelASTStep> children = new ArrayList<>();

    public ModelASTTreeStep(Object sourceLocation) {
        super(sourceLocation);
    }

    @Override
    public JSONObject toJSON() {
        final JSONArray a = new JSONArray();
        for (ModelASTStep child:children) {
            a.add(child.toJSON());
        }
        return super.toJSON().accumulate("children", a);
    }

    @Override
    public void validate(@Nonnull final ModelValidator validator) {
        super.validate(validator);
        for (ModelASTStep child : children) {
            child.validate(validator);
        }
    }

    @Override
    public String toGroovy() {
        StringBuilder result = new StringBuilder();
        result.append(super.toGroovy()).append(" {\n");
        for (ModelASTStep child : children) {
            result.append(child.toGroovy()).append("\n");
        }
        result.append("}\n");
        return result.toString();
    }

    @Override
    public void removeSourceLocation() {
        super.removeSourceLocation();
        for (ModelASTStep child : children) {
            child.removeSourceLocation();
        }
    }

    public List<ModelASTStep> getChildren() {
        return children;
    }

    public void setChildren(List<ModelASTStep> children) {
        this.children = children;
    }

    @Override
    public String toString() {
        return "ModelASTTreeStep{" +
                "name='" + getName() + '\'' +
                ", args=" + getArgs() +
                ", children=" + children +
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

        ModelASTTreeStep that = (ModelASTTreeStep) o;

        return getChildren() != null ? getChildren().equals(that.getChildren()) : that.getChildren() == null;

    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (getChildren() != null ? getChildren().hashCode() : 0);
        return result;
    }
}
