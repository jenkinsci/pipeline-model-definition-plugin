package org.jenkinsci.plugins.pipeline.modeldefinition.ast;

import net.sf.json.JSONObject;
import org.apache.commons.lang.StringEscapeUtils;
import org.jenkinsci.plugins.pipeline.modeldefinition.validator.ModelValidator;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents an individual Stage and the {@link ModelASTBranch}s it may contain.
 *
 * @author Kohsuke Kawaguchi
 * @author Andrew Bayer
 * @see ModelASTPipelineDef
 */
public class ModelASTStage extends ModelASTElement {
    protected String name;
    protected ModelASTAgent agent;
    protected ModelASTPostStage post;
    protected ModelASTWhen when;
    protected ModelASTTools tools;
    protected ModelASTEnvironment environment;
    private ModelASTStages stages;
    private List<ModelASTBranch> branches = new ArrayList<>();
    private Boolean failFast;
    private ModelASTOptions options;
    private ModelASTStageInput input;
    private ModelASTParallel parallel;
    private ModelASTMatrix matrix;

    @Deprecated
    private transient List<ModelASTStage> parallelContent = new ArrayList<>();

    public ModelASTStage(Object sourceLocation) {
        super(sourceLocation);
    }

    protected Object readResolve() throws IOException {
        if ((this.parallelContent != null && this.parallelContent.size() > 0)) {
            if (this.parallel == null) {
                this.parallel = new ModelASTParallel(new ArrayList<>());
            }
            this.parallel.getStages().addAll(this.parallelContent);
            this.parallelContent = new ArrayList<>();
        }

        return this;
    }

    @Override
    public JSONObject toJSON() {
        JSONObject o = new JSONObject()
            .accumulate("name", name)
            .elementOpt("agent", toJSON(agent))
            .elementOpt("when", toJSON(when))
            .elementOpt("post", toJSON(post))
            .elementOpt("tools", toJSON(tools))
            .elementOpt("environment", toJSON(environment))
            .elementOpt("options", toJSON(options))
            .elementOpt("input", toJSON(input))
            .elementOpt("stages", toJSON(stages))
            .elementOpt("parallel", toJSON(parallel))
            .elementOpt("matrix", toJSON(matrix))
            .elementOpt("branches", nullIfEmpty(toJSONArray(branches)))
            .elementOpt("failFast", failFast);

        return o;
    }

    @Override
    public void validate(@Nonnull final ModelValidator validator) {
        validate(validator, false);
    }

    public void validate(final ModelValidator validator, boolean isWithinParallel) {
        validator.validateElement(this, isWithinParallel);
        validate(validator, branches, agent, when, post, tools, environment, options, input, parallel, matrix);
        if (stages != null) {
            stages.validate(validator, isWithinParallel);
        }
    }

    @Override
    public String toGroovy() {
        StringBuilder result = new StringBuilder()
            // TODO decide if we need to support multiline names
            .append("stage(\'").append(name.replace("'", "\\'")).append("\') {\n")
            .append(childrenToGroovy())
            .append("}\n");

        return result.toString();
    }

    protected String childrenToGroovy() {
        StringBuilder result = new StringBuilder()
            // TODO decide if we need to support multiline names
            .append(toGroovy(agent))
            .append(toGroovy(when))
            .append(toGroovy(tools))
            .append(toGroovy(environment))
            .append(toGroovy(options))
            .append(toGroovy(input))
            .append(toGroovy(post))
            .append(toGroovy(stages));

        if (parallel != null || matrix != null) {
            if (failFast != null && failFast) {
                result.append("failFast true\n");
            }
        }
        result.append(toGroovy(parallel))
            .append(toGroovy(matrix));

        if (!branches.isEmpty()) {
            result.append("steps {\n");
            if (branches.size() > 1) {
                result.append("parallel(");
                boolean first = true;
                for (ModelASTBranch branch : branches) {
                    if (first) {
                        first = false;
                    } else {
                        result.append(',');
                    }
                    result.append('\n');
                    result.append('"' + StringEscapeUtils.escapeJava(branch.getName()) + '"')
                        .append(": {\n")
                        .append(branch.toGroovy())
                        .append("\n}");
                }
                if (failFast != null && failFast) {
                    result.append(",\nfailFast: true");
                }
                result.append("\n)\n");
            } else if (branches.size() == 1) {
                result.append(branches.get(0).toGroovy());
            }

            result.append("}\n");
        }

        return result.toString();
    }

    @Override
    public void removeSourceLocation() {
        super.removeSourceLocation();
        removeSourceLocationsFrom(branches, agent, when, post, tools, environment, options, input, stages, parallel, matrix);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public ModelASTAgent getAgent() {
        return agent;
    }

    public void setAgent(ModelASTAgent agent) {
        this.agent = agent;
    }

    public ModelASTPostStage getPost() {
        return post;
    }

    public void setPost(ModelASTPostStage post) {
        this.post = post;
    }

    public ModelASTWhen getWhen() {
        return when;
    }

    public void setWhen(ModelASTWhen when) {
        this.when = when;
    }

    public ModelASTTools getTools() {
        return tools;
    }

    public void setTools(ModelASTTools tools) {
        this.tools = tools;
    }

    public ModelASTEnvironment getEnvironment() {
        return environment;
    }

    public void setEnvironment(ModelASTEnvironment environment) {
        this.environment = environment;
    }

    public ModelASTStages getStages() {
        return stages;
    }

    public void setStages(ModelASTStages stages) {
        this.stages = stages;
    }

    public List<ModelASTBranch> getBranches() {
        return branches;
    }

    public void setBranches(List<ModelASTBranch> branches) {
        this.branches = branches;
    }

    public Boolean getFailFast() {
        return failFast;
    }

    public void setFailFast(Boolean f) {
        this.failFast = f;
    }

    public ModelASTParallel getParallel() {
        return parallel;
    }

    public void setParallel(ModelASTParallel s) {
        this.parallel = s;
    }

    public ModelASTMatrix getMatrix() {
        return matrix;
    }

    public void setMatrix(ModelASTMatrix s) {
        this.matrix = s;
    }

    public ModelASTOptions getOptions() {
        return options;
    }

    public void setOptions(ModelASTOptions options) {
        this.options = options;
    }

    public ModelASTStageInput getInput() {
        return input;
    }

    public void setInput(ModelASTStageInput input) {
        this.input = input;
    }

    @Deprecated
    public List<ModelASTStage> getParallelContent() {
        return parallelContent;
    }

    @Deprecated
    public void setParallelContent(List<ModelASTStage> parallelContent) {
        this.parallelContent = parallelContent;
    }

    @Override
    public String toString() {
        return "ModelASTStage{" +
                "name='" + name + '\'' +
                ", " + childrenToString() +
                "}";
    }

    protected String childrenToString() {
        return
            "agent=" + agent +
            ", when=" + when +
            ", post=" + post +
            ", tools=" + tools +
            ", environment=" + environment +
            ", stages=" + stages +
            ", branches=" + branches +
            ", failFast=" + failFast +
            ", parallel=" + parallel +
            ", matrix=" + matrix +
            ", options=" + options +
            ", input=" + input;
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

        ModelASTStage that = (ModelASTStage) o;

        if (getName() != null ? !getName().equals(that.getName()) : that.getName() != null) {
            return false;
        }
        if (getAgent() != null ? !getAgent().equals(that.getAgent()) : that.getAgent() != null) {
            return false;
        }
        if (getPost() != null ? !getPost().equals(that.getPost()) : that.getPost() != null) {
            return false;
        }
        if (getWhen() != null ? !getWhen().equals(that.getWhen()) : that.getWhen() != null) {
            return false;
        }
        if (getTools() != null ? !getTools().equals(that.getTools()) : that.getTools() != null) {
            return false;
        }
        if (getEnvironment() != null ? !getEnvironment().equals(that.getEnvironment()) : that.getEnvironment() != null) {
            return false;
        }
        if (getOptions() != null ? !getOptions().equals(that.getOptions()) : that.getOptions() != null) {
            return false;
        }
        if (getInput() != null ? !getInput().equals(that.getInput()) : that.getInput() != null) {
            return false;
        }
        if (getStages() != null ? !getStages().equals(that.getStages()) : that.getStages() != null) {
            return false;
        }
        if (getFailFast() != null ? !getFailFast().equals(that.getFailFast()) : that.getFailFast() != null) {
            return false;
        }
        if (getParallel() != null ? !getParallel().equals(that.getParallel()) : that.getParallel() != null) {
            return false;
        }
        if (getMatrix() != null ? !getMatrix().equals(that.getMatrix()) : that.getMatrix() != null) {
            return false;
        }
        if (getParallelContent() != null ? !getParallelContent().equals(that.getParallelContent())
                : that.getParallelContent() != null) {
            return false;
        }
        return getBranches() != null ? getBranches().equals(that.getBranches()) : that.getBranches() == null;

    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (getName() != null ? getName().hashCode() : 0);
        result = 31 * result + (getAgent() != null ? getAgent().hashCode() : 0);
        result = 31 * result + (getWhen() != null ? getWhen().hashCode() : 0);
        result = 31 * result + (getPost() != null ? getPost().hashCode() : 0);
        result = 31 * result + (getTools() != null ? getTools().hashCode() : 0);
        result = 31 * result + (getEnvironment() != null ? getEnvironment().hashCode() : 0);
        result = 31 * result + (getStages() != null ? getStages().hashCode() : 0);
        result = 31 * result + (getBranches() != null ? getBranches().hashCode() : 0);
        result = 31 * result + (getFailFast() != null ? getFailFast().hashCode() : 0);
        result = 31 * result + (getParallel() != null ? getParallel().hashCode() : 0);
        result = 31 * result + (getMatrix() != null ? getMatrix().hashCode() : 0);
        result = 31 * result + (getOptions() != null ? getOptions().hashCode() : 0);
        result = 31 * result + (getInput() != null ? getInput().hashCode() : 0);
        result = 31 * result + (getParallelContent() != null ? getParallelContent().hashCode() : 0);
        return result;
    }
}
