package org.jenkinsci.plugins.pipeline.modeldefinition.ast

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import net.sf.json.JSONObject
import org.jenkinsci.plugins.pipeline.modeldefinition.model.Root
import org.jenkinsci.plugins.pipeline.modeldefinition.validator.ModelValidator

/**
 * Represents the parsed pipeline definition for visual pipeline editor. Corresponds to {@link Root}.
 *
 * @author Kohsuke Kawaguchi
 * @author Andrew Bayer
 */
@ToString(includeSuper = true, includeSuperProperties = true)
@EqualsAndHashCode(callSuper = true)
@SuppressFBWarnings(value="SE_NO_SERIALVERSIONID")
public final class ModelASTPipelineDef extends ModelASTElement {
    ModelASTStages stages
    ModelASTNotifications notifications
    ModelASTPostBuild postBuild
    ModelASTEnvironment environment
    ModelASTAgent agent
    ModelASTTools tools

    public ModelASTPipelineDef(Object sourceLocation) {
        super(sourceLocation)
    }

    @Override
    public JSONObject toJSON() {
        JSONObject a = new JSONObject()
        a.put('stages',stages?.toJSON())
        a.put('notifications',notifications?.toJSON())
        a.put('postBuild',postBuild?.toJSON())
        a.put('environment',environment?.toJSON())
        a.put('agent',agent?.toJSON())
        a.put('tools',tools?.toJSON())
        return new JSONObject()
                .accumulate("pipeline", a)
    }

    @Override
    public void validate(ModelValidator validator) {
        validator.validateElement(this)

        stages?.validate(validator)
        notifications?.validate(validator)
        postBuild?.validate(validator)
        environment?.validate(validator)
        agent?.validate(validator)
        tools?.validate(validator)
    }

    @Override
    public String toGroovy() {
        List<String> lines = []

        lines << "pipeline {"

        lines << stages.toGroovy()

        lines << agent.toGroovy()

        if (tools != null) {
            lines << tools.toGroovy()
        }

        if (environment != null) {
            lines << environment.toGroovy()
        }

        if (postBuild != null) {
            lines << postBuild.toGroovy()
        }

        if (notifications != null) {
            lines << notifications.toGroovy()
        }

        lines << "}"

        return lines.join("\n")
    }

    /**
     * Helper method to pretty-print the generated Groovy from this and its children.
     *
     * @return An indented string of Groovy, suitable for use in a Jenkinsfile.
     */
    public String toPrettyGroovy() {
        List<String> prettyLines = []
        List<String> rawLines = toGroovy().split("\n")

        int indentCount = 0

        rawLines.each { r ->
            if (r.startsWith("}") || r.startsWith(")") || r.startsWith("]")) {
                indentCount -= 1
            }
            prettyLines << "${indent(indentCount)}${r}"
            if (r.endsWith("{") || r.endsWith("(") || r.endsWith("[")) {
                indentCount += 1
            }
        }

        return prettyLines.join("\n")
    }

    private String indent(int count) {
        return "  " * count
    }


}
