package org.jenkinsci.plugins.pipeline.modeldefinition.ast

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTBuildConditionsContainer
import org.jenkinsci.plugins.pipeline.modeldefinition.validator.ModelValidator

/**
 * Represents a list of {@link org.jenkinsci.plugins.pipeline.modeldefinition.model.BuildCondition} and {@link org.jenkinsci.plugins.pipeline.modeldefinition.model.StepsBlock} pairs to be called, depending on whether the build
 * condition is satisfied, at the end of the stage.
 *
 * @author Robert Sandell &lt;rsandell@cloudbees.com&gt;.
 */
@ToString(includeSuper = true, includeSuperProperties = true)
@EqualsAndHashCode(callSuper = true)
@SuppressFBWarnings(value="SE_NO_SERIALVERSIONID")
public final class ModelASTPostStage extends ModelASTBuildConditionsContainer {
    public ModelASTPostStage(Object sourceLocation) {
        super(sourceLocation)
    }

    @Override
    /*package*/ String getName() {
        return "post"
    }

    @Override
    public void validate(ModelValidator validator) {
        validator.validateElement(this)
        _validate(validator)
    }
}
