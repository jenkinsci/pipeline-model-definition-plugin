package org.jenkinsci.plugins.pipeline.modeldefinition.ast;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import groovy.transform.EqualsAndHashCode;
import groovy.transform.ToString;

/**
 * A single parameter definition, eventually corresponding to a {@code ParameterDefinition}
 *
 * @author Andrew Bayer
 */
public class ModelASTBuildParameter extends ModelASTMethodCall {
    public ModelASTBuildParameter(Object sourceLocation) {
        super(sourceLocation);
    }

    @Override
    public String toString() {
        return "ModelASTBuildParameter{" + super.toString() + "}";
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
