package org.jenkinsci.plugins.pipeline.modeldefinition.ast;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringEscapeUtils;
import org.jenkinsci.plugins.pipeline.modeldefinition.validator.ModelValidator;

import javax.annotation.Nonnull;

/**
 * Represents an individual Stage and the {@link ModelASTBranch}s it may contain.
 *
 * @author Kohsuke Kawaguchi
 * @author Andrew Bayer
 * @see ModelASTPipelineDef
 */
public final class ModelASTStage extends ModelASTElement {
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

    @Deprecated
    private transient ModelASTStages parallel;
    private List<ModelASTStage> parallelContent = new ArrayList<>();

    public ModelASTStage(Object sourceLocation) {
        super(sourceLocation);
    }

    protected Object readResolve() throws IOException {
        // If there's already a set of parallel stages defined, add that to the parallel content instead.
        if (this.parallel != null) {
            if (this.parallelContent == null) {
                this.parallelContent = new ArrayList<>();
            }
            this.parallelContent.addAll(this.parallel.getStages());
            this.parallel = null;
        }
        return this;
    }

    @Override
    public JSONObject toJSON() {
        JSONObject o = new JSONObject();
        o.accumulate("name", name);

        if (agent != null) {
            o.accumulate("agent", agent.toJSON());
        }
        if (when != null) {
            o.accumulate("when", when.toJSON());
        }

        if (post != null) {
            o.accumulate("post", post.toJSON());
        }

        if (tools != null) {
            o.accumulate("tools", tools.toJSON());
        }

        if (environment != null) {
            o.accumulate("environment", environment.toJSON());
        }
        if (options != null) {
            o.accumulate("options", options.toJSON());
        }
        if (input != null) {
            o.accumulate("input", input.toJSON());
        }
        if (stages != null) {
            o.accumulate("stages", stages.toJSON());
        }
        if (branches.isEmpty()) {
            if (!parallelContent.isEmpty()) {
                final JSONArray a = new JSONArray();
                for (ModelASTStage content : parallelContent) {
                    a.add(content.toJSON());
                }
                o.accumulate("parallel", a);
            }
        } else {
            final JSONArray a = new JSONArray();
            for (ModelASTBranch branch : branches) {
                a.add(branch.toJSON());
            }
            o.accumulate("branches", a);
        }

        if (failFast != null) {
            o.accumulate("failFast", failFast);
        }

        return o;
    }

    @Override
    public void validate(@Nonnull final ModelValidator validator) {
        validate(validator, false);
    }

    public void validate(final ModelValidator validator, boolean isNested) {
        validator.validateElement(this, isNested);

        if (agent != null) {
            agent.validate(validator);
        }
        if (when != null) {
            when.validate(validator);
        }
        if (post != null) {
            post.validate(validator);
        }
        if (tools != null) {
            tools.validate(validator);
        }
        if (environment != null) {
            environment.validate(validator);
        }
        if (options != null) {
            options.validate(validator);
        }
        if (input != null) {
            input.validate(validator);
        }
        if (stages != null) {
            stages.validate(validator, true);
        }
        for (ModelASTBranch branch : branches) {
            branch.validate(validator);
        }
        for (ModelASTStage content : parallelContent) {
            content.validate(validator, true);
        }
    }

    @Override
    public String toGroovy() {
        StringBuilder result = new StringBuilder();
        // TODO decide if we need to support multiline names
        result.append("stage(\'").append(name.replace("'", "\\'")).append("\') {\n");
        if (agent != null) {
            result.append(agent.toGroovy());
        }
        if (when != null) {
            result.append(when.toGroovy());
        }
        if (tools != null) {
            result.append(tools.toGroovy());
        }
        if (environment != null) {
            result.append(environment.toGroovy());
        }
        if (options != null) {
            result.append(options.toGroovy());
        }
        if (input != null) {
            result.append(input.toGroovy());
        }
        if (post != null) {
            result.append(post.toGroovy());
        }
        if (stages != null) {
            result.append("stages {\n");
            result.append(stages.toGroovy());
            result.append("}\n");
        }
        if (branches.isEmpty()) {
            if (failFast != null && failFast) {
                result.append("failFast true\n");
            }
            if (!parallelContent.isEmpty()) {
                result.append("parallel {\n");
                for (ModelASTStage content : parallelContent) {
                    result.append(content.toGroovy());
                }
                result.append("}\n");
            }
        } else {
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

        result.append("}\n");

        return result.toString();
    }

    @Override
    public void removeSourceLocation() {
        super.removeSourceLocation();
        if (agent != null) {
            agent.removeSourceLocation();
        }
        if (when != null) {
            when.removeSourceLocation();
        }
        if (post != null) {
            post.removeSourceLocation();
        }
        if (tools != null) {
            tools.removeSourceLocation();
        }
        if (environment != null) {
            environment.removeSourceLocation();
        }
        if (options != null) {
            options.removeSourceLocation();
        }
        if (input != null) {
            input.removeSourceLocation();
        }
        if (stages != null) {
            stages.removeSourceLocation();
        }
        for (ModelASTBranch branch: branches) {
            branch.removeSourceLocation();
        }
        if (parallel != null) {
            parallel.removeSourceLocation();
        }
        for (ModelASTStage content : parallelContent) {
            content.removeSourceLocation();
        }
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

    @Deprecated
    public ModelASTStages getParallel() {
        return parallel;
    }

    @Deprecated
    public void setParallel(ModelASTStages s) {
        this.parallel = s;
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

    public List<ModelASTStage> getParallelContent() {
        return parallelContent;
    }

    public void setParallelContent(List<ModelASTStage> parallelContent) {
        this.parallelContent = parallelContent;
    }

    @Override
    public String toString() {
        return "ModelASTStage{" +
                "name='" + name + '\'' +
                ", agent=" + agent +
                ", when=" + when +
                ", post=" + post +
                ", tools=" + tools +
                ", environment=" + environment +
                ", stages=" + stages +
                ", branches=" + branches +
                ", failFast=" + failFast +
                ", parallel=" + parallel +
                ", options=" + options +
                ", input=" + input +
                ", parallelContent=" + parallelContent +
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
        result = 31 * result + (getOptions() != null ? getOptions().hashCode() : 0);
        result = 31 * result + (getInput() != null ? getInput().hashCode() : 0);
        result = 31 * result + (getParallelContent() != null ? getParallelContent().hashCode() : 0);
        return result;
    }
}
