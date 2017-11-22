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
import java.util.ArrayList;
import java.util.List;

/**
 * An internal function call, most notably for use with {@link ModelASTEnvironment}
 *
 * @author Andrew Bayer
 */
public class ModelASTInternalFunctionCall extends ModelASTElement implements ModelASTEnvironmentValue {
    private String name;
    private List<ModelASTValue> args = new ArrayList<>();


    public ModelASTInternalFunctionCall(Object sourceLocation) {
        super(sourceLocation);
    }

    @Override
    public JSONObject toJSON() {
        final JSONArray a = new JSONArray();
        for (ModelASTValue arg: args) {
            a.add(arg.toJSON());
        }
        return new JSONObject().accumulate("name", name).accumulate("arguments", a);
    }

    @Override
    public void validate(@Nonnull final ModelValidator validator) {
        validator.validateElement(this);
        for (ModelASTMethodArg arg : args) {
            arg.validate(validator);
        }
    }

    @Override
    public String toGroovy() {
        StringBuilder result = new StringBuilder(name);
        result.append('(');
        boolean first = true;
        for (ModelASTValue arg : args) {
            if (first) {
                first = false;
            } else {
                result.append(", ");
            }
            result.append(arg.toGroovy());
        }
        result.append(')');
        return result.toString();
    }

    @Override
    public void removeSourceLocation() {
        super.removeSourceLocation();
        for (ModelASTValue arg : args) {
            arg.removeSourceLocation();
        }
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<ModelASTValue> getArgs() {
        return args;
    }

    public void setArgs(List<ModelASTValue> args) {
        this.args = args;
    }

    @Override
    public String toString() {
        return "ModelASTInternalFunctionCall{" +
                "name='" + name + '\'' +
                ", args=" + args +
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

        ModelASTInternalFunctionCall that = (ModelASTInternalFunctionCall) o;

        if (getName() != null ? !getName().equals(that.getName()) : that.getName() != null) {
            return false;
        }
        return getArgs() != null ? getArgs().equals(that.getArgs()) : that.getArgs() == null;

    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (getName() != null ? getName().hashCode() : 0);
        result = 31 * result + (getArgs() != null ? getArgs().hashCode() : 0);
        return result;
    }
}
