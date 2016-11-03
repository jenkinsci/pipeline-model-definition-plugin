package org.jenkinsci.plugins.pipeline.modeldefinition.ast;

/**
 * Represents the special step for {@code ScriptStep}, which are executed without validation against the declarative subset.
 *
 * @author Andrew Bayer
 */
public class ModelASTScriptBlock extends AbstractModelASTCodeBlock {
    public ModelASTScriptBlock(Object sourceLocation) {
        super(sourceLocation, "script");
    }
}
