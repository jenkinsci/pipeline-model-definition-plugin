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
        StringBuilder result = new StringBuilder("script {\n");
        if (getArgs() != null
                && getArgs() instanceof ModelASTSingleArgument
                && ((ModelASTSingleArgument) getArgs()).getValue()!=null
                && ((ModelASTSingleArgument) getArgs()).getValue().isLiteral()) {
            result.append(((ModelASTSingleArgument) getArgs()).getValue().getValue());
        } else if (getArgs() != null) {
            result.append(getArgs().toGroovy());
        }
        result.append("\n}\n");
        return result.toString();
    }

    @Override
    public String toString() {
        return "ModelASTScriptBlock{" +
                "name='" + getName() + '\'' +
                ", args=" + getArgs() +
                "}";
    }

}
