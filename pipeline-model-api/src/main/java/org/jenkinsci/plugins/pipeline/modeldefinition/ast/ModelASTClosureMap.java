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

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.jenkinsci.plugins.pipeline.modeldefinition.validator.ModelValidator;

import javax.annotation.Nonnull;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Represents a map of names to possible method arguments, in closure form in Groovy
 *
 * @author Andrew Bayer
 */
public final class ModelASTClosureMap extends ModelASTElement implements ModelASTMethodArg {
    private Map<ModelASTKey, ModelASTMethodArg> variables = new LinkedHashMap<>();

    public ModelASTClosureMap(Object sourceLocation) {
        super(sourceLocation);
    }

    @Override
    public JSONArray toJSON() {
        final JSONArray a = new JSONArray();
        for (Map.Entry<ModelASTKey, ModelASTMethodArg> entry: variables.entrySet()) {
            JSONObject o = new JSONObject();
            o.accumulate("key", entry.getKey().toJSON());
            o.accumulate("value", entry.getValue().toJSON());
            a.add(o);
        }
        return a;

    }

    @Override
    public void validate(@Nonnull final ModelValidator validator) {
        for (Map.Entry<ModelASTKey, ModelASTMethodArg> entry : variables.entrySet()) {
            entry.getKey().validate(validator);
            entry.getValue().validate(validator);
        }
    }

    @Override
    public String toGroovy() {
        StringBuilder result = new StringBuilder("{\n");
        for (Map.Entry<ModelASTKey, ModelASTMethodArg> entry : variables.entrySet()) {
            result.append(entry.getKey().toGroovy()).append(" ").append(entry.getValue().toGroovy()).append('\n');
        }
        result.append("}\n");
        return result.toString();
    }

    @Override
    public void removeSourceLocation() {
        super.removeSourceLocation();
        for (Map.Entry<ModelASTKey, ModelASTMethodArg> entry : variables.entrySet()) {
            entry.getKey().removeSourceLocation();
            entry.getValue().removeSourceLocation();
        }
    }

    public Map<ModelASTKey, ModelASTMethodArg> getVariables() {
        return variables;
    }

    public void setVariables(Map<ModelASTKey, ModelASTMethodArg> variables) {
        this.variables = variables;
    }

    public boolean containsKey(String k) {
        for (ModelASTKey key : variables.keySet()) {
            if (key.getKey().equals(k)) {
                return true;
            }
        }

        return false;
    }

    @Override
    public String toString() {
        return "ModelASTClosureMap{" +
                "variables=" + variables +
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

        ModelASTClosureMap that = (ModelASTClosureMap) o;

        return getVariables() != null ? getVariables().equals(that.getVariables()) : that.getVariables() == null;

    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (getVariables() != null ? getVariables().hashCode() : 0);
        return result;
    }
}
