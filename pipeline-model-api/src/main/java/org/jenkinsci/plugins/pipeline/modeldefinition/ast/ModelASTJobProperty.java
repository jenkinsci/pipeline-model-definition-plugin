package org.jenkinsci.plugins.pipeline.modeldefinition.ast;

/**
 * A single job property, corresponding eventually to {@code JobProperty}
 *
 * @author Andrew Bayer
 */
public class ModelASTJobProperty extends ModelASTMethodCall {
    public ModelASTJobProperty(Object sourceLocation) {
        super(sourceLocation);
    }

    @Override
    public String toString() {
        return "ModelASTJobProperty{"+super.toString()+"}";
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

        return true;

    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }

}
