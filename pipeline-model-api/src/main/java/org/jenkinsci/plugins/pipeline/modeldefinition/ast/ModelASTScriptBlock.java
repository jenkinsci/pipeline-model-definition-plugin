package org.jenkinsci.plugins.pipeline.modeldefinition.ast;

/**
 * Represents the special step for {@code ScriptStep}, which are executed without validation against the declarative subset.
 *
 * @author Andrew Bayer
 */
public class ModelASTScriptBlock extends ModelASTStep {
    public ModelASTScriptBlock(Object sourceLocation) {
        super(sourceLocation);
        this.setName("script");
    }

    @Override
    public String toGroovy() {
        return "script {\n" + getArgs().toGroovy() + "\n}\n";
    }

    @Override
    public String toString() {
        return "ModelASTScriptBlock{" +
                "name='" + getName() + '\'' +
                ", args=" + getArgs() +
                "}";
    }

}
