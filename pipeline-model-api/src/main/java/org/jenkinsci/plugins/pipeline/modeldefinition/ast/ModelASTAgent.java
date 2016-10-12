package org.jenkinsci.plugins.pipeline.modeldefinition.ast;

import org.jenkinsci.plugins.pipeline.modeldefinition.validator.ModelValidator;

/**
 * Represents what context in which to run the build - i.e., which label to run on, what Docker agent to run in, etc.
 * Corresponds to Agent.
 *
 * @author Andrew Bayer
 */
public final class ModelASTAgent extends ModelASTElement {
    private ModelASTArgumentList args;

    public ModelASTAgent(Object sourceLocation) {
        super(sourceLocation);
    }

    @Override
    public Object toJSON() {
        return args.toJSON();
    }

    @Override
    public void validate(ModelValidator validator) {
        validator.validateElement(this);

        args.validate(validator);
    }

    @Override
    public String toGroovy() {
        String argStr;
        // TODO: Stop special-casing agent none.
        if (args instanceof ModelASTSingleArgument && (
                ((ModelASTSingleArgument) args).getValue().getValue().equals("none") || ((ModelASTSingleArgument) args)
                        .getValue().getValue().equals("any"))) {
            argStr = (String)((ModelASTSingleArgument) args).getValue().getValue();
        } else {
            argStr = args.toGroovy();
        }

        return "agent " + argStr + "\n";
    }

    @Override
    public void removeSourceLocation() {
        super.removeSourceLocation();
        args.removeSourceLocation();
    }

    public ModelASTArgumentList getArgs() {
        return args;
    }

    public void setArgs(ModelASTArgumentList args) {
        this.args = args;
    }

    @Override
    public String toString() {
        return "ModelASTAgent{" +
                "args=" + args +
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

        ModelASTAgent that = (ModelASTAgent) o;

        return getArgs() != null ? getArgs().equals(that.getArgs()) : that.getArgs() == null;

    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (getArgs() != null ? getArgs().hashCode() : 0);
        return result;
    }

}
