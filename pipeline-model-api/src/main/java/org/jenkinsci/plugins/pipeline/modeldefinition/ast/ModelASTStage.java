package org.jenkinsci.plugins.pipeline.modeldefinition.ast;

import java.util.ArrayList;
import java.util.List;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringEscapeUtils;
import org.jenkinsci.plugins.pipeline.modeldefinition.validator.ModelValidator;

/**
 * Represents an individual Stage and the {@link ModelASTBranch}s it may contain.
 *
 * @author Kohsuke Kawaguchi
 * @author Andrew Bayer
 * @see ModelASTPipelineDef
 */
public final class ModelASTStage extends ModelASTElement {
    private String name;
    private ModelASTAgent agent;
    private List<ModelASTBranch> branches = new ArrayList<ModelASTBranch>();
    private ModelASTPostStage post;
    private ModelASTWhen when;
    private ModelASTTools tools;
    private ModelASTEnvironment environment;
    private Boolean failFast;
    private ModelASTStages parallelStages;

    public ModelASTStage(Object sourceLocation) {
        super(sourceLocation);
    }

    @Override
    public JSONObject toJSON() {
        JSONObject o = new JSONObject();
        o.accumulate("name", name);

        if (branches.isEmpty() && parallelStages != null) {
            o.accumulate("parallelStages", parallelStages.toJSON());
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

        return o;
    }

    @Override
    public void validate(final ModelValidator validator) {
        validate(validator, false);
    }

    public void validate(final ModelValidator validator, boolean isNested) {
        validator.validateElement(this, isNested);
        for (ModelASTBranch branch : branches) {
            branch.validate(validator);
        }
        if (parallelStages != null) {
            parallelStages.validate(validator, true);
        }
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
        if (branches.isEmpty() && parallelStages != null) {
            result.append("parallelStages {\n");
            result.append(parallelStages.toGroovy());
            result.append("}\n");
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

        if (post != null) {
            result.append(post.toGroovy());
        }

        result.append("}\n");

        return result.toString();
    }

    @Override
    public void removeSourceLocation() {
        super.removeSourceLocation();
        for (ModelASTBranch branch: branches) {
            branch.removeSourceLocation();
        }
        if (agent != null) {
            agent.removeSourceLocation();
        }
        if (parallelStages != null) {
            parallelStages.removeSourceLocation();
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

    public List<ModelASTBranch> getBranches() {
        return branches;
    }

    public void setBranches(List<ModelASTBranch> branches) {
        this.branches = branches;
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

    public Boolean getFailFast() {
        return failFast;
    }

    public void setFailFast(Boolean f) {
        this.failFast = f;
    }

    public ModelASTStages getParallelStages() {
        return parallelStages;
    }

    public void setParallelStages(ModelASTStages s) {
        this.parallelStages = s;
    }

    @Override
    public String toString() {
        return "ModelASTStage{" +
                "name='" + name + '\'' +
                ", agent=" + agent +
                ", when=" + when +
                ", branches=" + branches +
                ", post=" + post +
                ", tools=" + tools +
                ", environment=" + environment +
                ", failFast=" + failFast +
                ", parallelStages=" + parallelStages +
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

        if (getFailFast() != null ? !getFailFast().equals(that.getFailFast()) : that.getFailFast() != null) {
            return false;
        }
        if (getParallelStages() != null ? !getParallelStages().equals(that.getParallelStages()) : that.getParallelStages() != null) {
            return false;
        }
        if (getTools() != null ? !getTools().equals(that.getTools()) : that.getTools() != null) {
            return false;
        }
        if (getEnvironment() != null ? !getEnvironment().equals(that.getEnvironment()) : that.getEnvironment() != null) {
            return false;
        }
        return getBranches() != null ? getBranches().equals(that.getBranches()) : that.getBranches() == null;

    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (getName() != null ? getName().hashCode() : 0);
        result = 31 * result + (getAgent() != null ? getAgent().hashCode() : 0);
        result = 31 * result + (getBranches() != null ? getBranches().hashCode() : 0);
        result = 31 * result + (getWhen() != null ? getWhen().hashCode() : 0);
        result = 31 * result + (getPost() != null ? getPost().hashCode() : 0);
        result = 31 * result + (getTools() != null ? getTools().hashCode() : 0);
        result = 31 * result + (getEnvironment() != null ? getEnvironment().hashCode() : 0);
        result = 31 * result + (getFailFast() != null ? getFailFast().hashCode() : 0);
        result = 31 * result + (getParallelStages() != null ? getParallelStages().hashCode() : 0);
        return result;
    }
}
