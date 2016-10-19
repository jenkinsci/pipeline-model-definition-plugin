package org.jenkinsci.plugins.pipeline.modeldefinition.ast;

/**
 * A single trigger, corresponding eventually to a {@code Trigger}
 *
 * @author Andrew Bayer
 */
public class ModelASTTrigger extends ModelASTMethodCall {
    public ModelASTTrigger(Object sourceLocation) {
        super(sourceLocation);
    }

    @Override
    public String toString() {
        return "ModelASTTrigger{" +
                "name='" + getName() + '\'' +
                ", args=" + getArgs() +
                "}";
    }
}
