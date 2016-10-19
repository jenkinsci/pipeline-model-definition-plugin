package org.jenkinsci.plugins.pipeline.modeldefinition.ast;

import java.util.ArrayList;
import java.util.List;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
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

    public ModelASTStage(Object sourceLocation) {
        super(sourceLocation);
    }

    @Override
    public JSONObject toJSON() {
        final JSONArray a = new JSONArray();
        for (ModelASTBranch branch : branches) {
            a.add(branch.toJSON());
        }
        JSONObject o = new JSONObject();
        o.accumulate("name", name);
        o.accumulate("branches", a);
        if (agent != null) {
            o.accumulate("agent", agent.toJSON());
        }

        if (post != null) {
            o.accumulate("post", post.toJSON());
        }

        return o;
    }

    @Override
    public void validate(final ModelValidator validator) {
        validator.validateElement(this);
        for (ModelASTBranch branch : branches) {
            branch.validate(validator);
        }
        if (agent != null) {
            agent.validate(validator);
        }
        if (post != null) {
            post.validate(validator);
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

        result.append("steps {\n");
        if (branches.size() > 1) {
            result.append("parallel(");
            boolean first = true;
            for (ModelASTBranch branch: branches) {
                if (first) {
                    first = false;
                } else {
                    result.append(',');
                }
                result.append('\n');
                result.append(branch.getName()).append(": {\n").append(branch.toGroovy()).append("\n}");
            }
            result.append("\n)\n");
        } else if (branches.size() == 1) {
            result.append(branches.get(0).toGroovy());
        }

        result.append("}\n");

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
        if (post != null) {
            post.removeSourceLocation();
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

    @Override
    public String toString() {
        return "ModelASTStage{" +
                "name='" + name + '\'' +
                ", agent=" + agent +
                ", branches=" + branches +
                ", post=" + post +
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
        return getBranches() != null ? getBranches().equals(that.getBranches()) : that.getBranches() == null;

    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (getName() != null ? getName().hashCode() : 0);
        result = 31 * result + (getAgent() != null ? getAgent().hashCode() : 0);
        result = 31 * result + (getBranches() != null ? getBranches().hashCode() : 0);
        return result;
    }
}
