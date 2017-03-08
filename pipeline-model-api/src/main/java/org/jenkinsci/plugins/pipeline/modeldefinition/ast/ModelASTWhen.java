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
 *
 */

package org.jenkinsci.plugins.pipeline.modeldefinition.ast;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.jenkinsci.plugins.pipeline.modeldefinition.validator.ModelValidator;

import java.util.ArrayList;
import java.util.List;

/**
 * If {@link ModelASTStage} will be executed or not.
 */
public class ModelASTWhen extends ModelASTElement {

    private ModelASTWhenContent condition;

    public ModelASTWhen(Object sourceLocation) {
        super(sourceLocation);
    }

    public ModelASTWhenContent getCondition() {
        return condition;
    }

    public void setCondition(ModelASTWhenContent condition) {
        this.condition = condition;
    }

    @Override
    public Object toJSON() {
        return new JSONObject().accumulate("condition", condition.toJSON());
    }

    @Override
    public String toGroovy() {
        StringBuilder result = new StringBuilder("when {\n");
        result.append(condition.toGroovy()).append("\n");
        result.append("}\n");
        return result.toString();
    }

    @Override
    public void removeSourceLocation() {
        super.removeSourceLocation();
        condition.removeSourceLocation();
    }

    @Override
    public String toString() {
        return "ModelASTWhen{" +
                "condition=" + condition +
                "}";
    }

    @Override
    public void validate(final ModelValidator validator) {
        validator.validateElement(this);
        condition.validate(validator);
    }


}
