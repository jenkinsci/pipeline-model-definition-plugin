package org.jenkinsci.plugins.pipeline.modeldefinition.ast;

import org.jenkinsci.plugins.pipeline.modeldefinition.validator.ModelValidator;
import org.jenkinsci.plugins.structs.describable.UninstantiatedDescribable;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.Map;

/**
 * Represents a single unnamed argument.
 *
 * @author Kohsuke Kawaguchi
 * @author Andrew Bayer
 */
public final class ModelASTSingleArgument extends ModelASTArgumentList {
    private ModelASTValue value;

    public ModelASTSingleArgument(Object sourceLocation) {
        super(sourceLocation);
    }

    @Override
    public Object toJSON() {
        return value.toJSON();
    }

    @Override
    public void validate(@Nonnull ModelValidator validator) {
        // Nothing to immediately validate here
        if (value != null) {
            value.validate(validator);
        }
    }

    @Override
    public String toGroovy() {
        return value.toGroovy();
    }

    @Override
    public void removeSourceLocation() {
        super.removeSourceLocation();
        if (value != null) {
            value.removeSourceLocation();
        }
    }

    public ModelASTValue getValue() {
        return value;
    }

    public void setValue(ModelASTValue value) {
        this.value = value;
    }

    @Override
    public Map<String,?> argListToMap() {
        return Collections.singletonMap(UninstantiatedDescribable.ANONYMOUS_KEY,
                getValue().getValue());
    }

    @Override
    public String toString() {
        return "ModelASTSingleArgument{" +
                "value=" + value +
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

        ModelASTSingleArgument that = (ModelASTSingleArgument) o;

        return getValue() != null ? getValue().equals(that.getValue()) : that.getValue() == null;

    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (getValue() != null ? getValue().hashCode() : 0);
        return result;
    }
}
