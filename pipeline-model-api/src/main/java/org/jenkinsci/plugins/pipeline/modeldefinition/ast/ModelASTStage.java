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
public final class ModelASTStage extends AbstractModelASTParallelContent {
    private List<ModelASTBranch> branches = new ArrayList<>();
    private Boolean failFast;
    @Deprecated
    private transient ModelASTStages parallel;
    private List<AbstractModelASTParallelContent> parallelContent = new ArrayList<>();

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
        JSONObject o = super.toJSON();
        if (branches.isEmpty()) {
            if (!parallelContent.isEmpty()) {
                final JSONArray a = new JSONArray();
                for (AbstractModelASTParallelContent content : parallelContent) {
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

    @Override
    public void validate(final ModelValidator validator, boolean isNested) {
        validator.validateElement(this, isNested);

        super.validate(validator);

        for (ModelASTBranch branch : branches) {
            branch.validate(validator);
        }
        for (AbstractModelASTParallelContent content: parallelContent) {
            content.validate(validator, true);
        }
    }

    @Override
    public String toGroovy() {
        StringBuilder result = new StringBuilder();
        // TODO decide if we need to support multiline names
        result.append("stage(\'").append(name.replace("'", "\\'")).append("\') {\n");
        result.append(super.toGroovy());
        if (branches.isEmpty()) {
            if (failFast != null && failFast) {
                result.append("failFast true\n");
            }
            if (!parallelContent.isEmpty()) {
                result.append("parallel {\n");
                for (AbstractModelASTParallelContent content : parallelContent) {
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
        for (ModelASTBranch branch: branches) {
            branch.removeSourceLocation();
        }
        if (parallel != null) {
            parallel.removeSourceLocation();
        }
        for (AbstractModelASTParallelContent content : parallelContent) {
            content.removeSourceLocation();
        }
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

    public List<AbstractModelASTParallelContent> getParallelContent() {
        return parallelContent;
    }

    public void setParallelContent(List<AbstractModelASTParallelContent> parallelContent) {
        this.parallelContent = parallelContent;
    }

    @Override
    public String toString() {
        return "ModelASTStage{" +
                super.toString() +
                ", branches=" + branches +
                ", failFast=" + failFast +
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
        result = 31 * result + (getBranches() != null ? getBranches().hashCode() : 0);
        result = 31 * result + (getFailFast() != null ? getFailFast().hashCode() : 0);
        result = 31 * result + (getParallel() != null ? getParallel().hashCode() : 0);
        result = 31 * result + (getParallelContent() != null ? getParallelContent().hashCode() : 0);
        return result;
    }
}
