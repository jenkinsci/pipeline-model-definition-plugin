package org.jenkinsci.plugins.pipeline.modeldefinition.ast;

import net.sf.json.JSONObject;
import org.jenkinsci.plugins.pipeline.modeldefinition.validator.ModelValidator;

import javax.annotation.Nonnull;

/**
 * An individual pair of a {@link ModelASTKey} and a {@link ModelASTMethodArg}
 *
 * @author Andrew Bayer
 */
public final class ModelASTKeyValueOrMethodCallPair extends ModelASTElement implements ModelASTMethodArg {
    private ModelASTKey key;
    private ModelASTMethodArg value;

    public ModelASTKeyValueOrMethodCallPair(Object sourceLocation) {
        super(sourceLocation);
    }

    @Override
    public JSONObject toJSON() {
        return new JSONObject().accumulate("key", key.toJSON()).accumulate("value", value.toJSON());
    }

    @Override
    public void validate(@Nonnull ModelValidator validator) {
        key.validate(validator);
        value.validate(validator);
    }

    @Override
    public String toGroovy() {
        return key.toGroovy() + ": " + value.toGroovy();
    }

    @Override
    public void removeSourceLocation() {
        super.removeSourceLocation();
        key.removeSourceLocation();
        value.removeSourceLocation();
    }

    public ModelASTKey getKey() {
        return key;
    }

    public void setKey(ModelASTKey key) {
        this.key = key;
    }

    public ModelASTMethodArg getValue() {
        return value;
    }

    public void setValue(ModelASTMethodArg value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return "ModelASTKeyValueOrMethodCallPair{" +
                "key=" + key +
                ", value=" + value +
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

        ModelASTKeyValueOrMethodCallPair that = (ModelASTKeyValueOrMethodCallPair) o;

        if (getKey() != null ? !getKey().equals(that.getKey()) : that.getKey() != null) {
            return false;
        }
        return getValue() != null ? getValue().equals(that.getValue()) : that.getValue() == null;

    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (getKey() != null ? getKey().hashCode() : 0);
        result = 31 * result + (getValue() != null ? getValue().hashCode() : 0);
        return result;
    }
}
