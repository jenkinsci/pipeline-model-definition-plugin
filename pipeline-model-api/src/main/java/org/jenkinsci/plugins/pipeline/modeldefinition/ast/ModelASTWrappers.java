/*
 * The MIT License
 *
 * Copyright (c) 2016, CloudBees, Inc.
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

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.jenkinsci.plugins.pipeline.modeldefinition.validator.ModelValidator;

import java.util.ArrayList;
import java.util.List;

/**
 * A container for one or more {@link ModelASTTrigger}s.
 *
 * @author Andrew Bayer
 */
public final class ModelASTWrappers extends ModelASTElement {
    private List<ModelASTWrapper> wrappers = new ArrayList<ModelASTWrapper>();

    public ModelASTWrappers(Object sourceLocation) {
        super(sourceLocation);
    }

    @Override
    public JSONObject toJSON() {
        final JSONArray a = new JSONArray();
        for (ModelASTWrapper wrapper: wrappers) {
            a.add(wrapper.toJSON());
        }
        return new JSONObject().accumulate("wrappers", a);
    }

    @Override
    public void validate(final ModelValidator validator) {
        validator.validateElement(this);
        for (ModelASTWrapper wrapper : wrappers) {
            wrapper.validate(validator);
        }
    }

    @Override
    public String toGroovy() {
        StringBuilder result = new StringBuilder("wrappers {\n");
        for (ModelASTWrapper wrapper : wrappers) {
            result.append(wrapper.toGroovy()).append('\n');
        }
        result.append("}\n");
        return result.toString();
    }

    @Override
    public void removeSourceLocation() {
        super.removeSourceLocation();
        for (ModelASTWrapper wrapper: wrappers) {
            wrapper.removeSourceLocation();
        }
    }

    public List<ModelASTWrapper> getWrappers() {
        return wrappers;
    }

    public void setWrappers(List<ModelASTWrapper> wrappers) {
        this.wrappers = wrappers;
    }

    @Override
    public String toString() {
        return "ModelASTWrappers{" +
                "wrappers=" + wrappers +
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

        ModelASTWrappers that = (ModelASTWrappers) o;

        return getWrappers() != null ? getWrappers().equals(that.getWrappers()) : that.getWrappers() == null;

    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (getWrappers() != null ? getWrappers().hashCode() : 0);
        return result;
    }
}
