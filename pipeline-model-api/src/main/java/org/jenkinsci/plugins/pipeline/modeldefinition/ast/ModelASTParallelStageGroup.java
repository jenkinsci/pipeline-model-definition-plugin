/*
 * The MIT License
 *
 * Copyright (c) 2017, CloudBees, Inc.
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

/**
 * Represents a group of stages for {@link ModelASTStage#parallel}.
 *
 * @author Andrew Bayer
 */
public final class ModelASTParallelStageGroup extends ModelASTElement implements ModelASTParallelContent {
    public final static String ELEMENT_NAME = "group";

    private ModelASTStages stages;
    private String name;

    public ModelASTParallelStageGroup(Object sourceLocation) {
        super(sourceLocation);
    }

    @Override
    public JSONObject toJSON() {
        JSONObject o = new JSONObject();
        o.put("name", name);
        o.put("stages", stages != null ? stages.toJSON() : null);
        return o;
    }

    @Override
    public void validate(final ModelValidator validator) {
        validator.validateElement(this);
        if (stages != null) {
            stages.validate(validator, true);
        }
    }

    @Override
    public String toGroovy() {
        StringBuilder result = new StringBuilder();
        result.append(ELEMENT_NAME + "(\'").append(name.replace("'", "\\'")).append("\') {\n");
        result.append(stages.toGroovy());
        result.append("}\n");
        return result.toString();
    }

    @Override
    public void removeSourceLocation() {
        super.removeSourceLocation();
        if (stages != null) {
            stages.removeSourceLocation();
        }
    }

    public ModelASTStages getStages() {
        return stages;
    }

    public void setStages(ModelASTStages stages) {
        this.stages = stages;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return "ModelASTParallelStageGroup{" +
                "name=" + name +
                "stages=" + stages +
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

        ModelASTParallelStageGroup that = (ModelASTParallelStageGroup) o;

        if (getName() != null ? !getName().equals(that.getName()) : that.getName() != null) {
            return false;
        }

        return getStages() != null ? getStages().equals(that.getStages()) : that.getStages() == null;

    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (getName() != null ? getName().hashCode() : 0);
        result = 31 * result + (getStages() != null ? getStages().hashCode() : 0);
        return result;
    }
}
