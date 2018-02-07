/*
 * The MIT License
 *
 * Copyright (c) 2018, CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package org.jenkinsci.plugins.pipeline.modeldefinition.ast;

import net.sf.json.JSONObject;
import org.jenkinsci.plugins.pipeline.modeldefinition.validator.ModelValidator;

import javax.annotation.Nonnull;

/**
 * Represents a single when condition to be checked and possibly executed in the post sections.
 *
 * @author Andrew Bayer
 */
public final class ModelASTPostWhenCondition extends ModelASTElement implements ModelASTBranchHolder {
    private ModelASTWhen when;
    private ModelASTBranch branch;

    public ModelASTPostWhenCondition(Object sourceLocation) {
        super(sourceLocation);
    }

    @Override
    public JSONObject toJSON() {
        return new JSONObject().accumulate("when", when.toJSON()).accumulate("branch", branch.toJSON());
    }

    @Override
    public void validate(@Nonnull ModelValidator validator) {
        validator.validateElement(this);
        when.validate(validator);
        branch.validate(validator);
    }

    @Override
    public String toGroovy() {
        StringBuilder result = new StringBuilder();
        result.append("condition {\n");
        result.append(when.toGroovy()).append("\n");
        result.append("steps {\n");
        result.append(branch.toGroovy());
        result.append("\n}\n}\n");
        return result.toString();
    }

    @Override
    public void removeSourceLocation() {
        super.removeSourceLocation();
        when.removeSourceLocation();
        branch.removeSourceLocation();
    }

    public ModelASTWhen getWhen() {
        return when;
    }

    public void setWhen(ModelASTWhen when) {
        this.when = when;
    }

    public ModelASTBranch getBranch() {
        return branch;
    }

    public void setBranch(ModelASTBranch branch) {
        this.branch = branch;
    }

    @Override
    public String toString() {
        return "ModelASTPostWhenCondition{" +
                "condition='" + when + '\'' +
                ", branch=" + branch +
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

        ModelASTPostWhenCondition that = (ModelASTPostWhenCondition) o;

        if (getWhen() != null ? !getWhen().equals(that.getWhen()) : that.getWhen() != null) {
            return false;
        }
        return getBranch() != null ? getBranch().equals(that.getBranch()) : that.getBranch() == null;

    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (getWhen() != null ? getWhen().hashCode() : 0);
        result = 31 * result + (getBranch() != null ? getBranch().hashCode() : 0);
        return result;
    }
}
