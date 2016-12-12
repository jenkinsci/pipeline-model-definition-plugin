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

import net.sf.json.JSONObject;
import org.jenkinsci.plugins.pipeline.modeldefinition.validator.ModelValidator;

/**
 * Represents a map of possible top-level options.
 *
 * @author Andrew Bayer
 */
public final class ModelASTOptions extends ModelASTElement {
    private Boolean skipCheckout;

    public ModelASTOptions(Object sourceLocation) {
        super(sourceLocation);
    }

    @Override
    public JSONObject toJSON() {
        final JSONObject o = new JSONObject();
        o.accumulate("skipCheckout", skipCheckout);

        return o;
    }

    @Override
    public void validate(final ModelValidator validator) {
        validator.validateElement(this);
    }

    @Override
    public String toGroovy() {
        StringBuilder result = new StringBuilder("options {\n");
        if (skipCheckout != null) {
            result.append("skipCheckout " + skipCheckout);
        }
        result.append("}\n");
        return result.toString();
    }

    public Boolean getSkipCheckout() {
        return skipCheckout;
    }

    public void setSkipCheckout(Boolean b) {
        this.skipCheckout = b;
    }

    @Override
    public String toString() {
        return "ModelASTOptions{" +
                "skipCheckout=" + skipCheckout +
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

        ModelASTOptions that = (ModelASTOptions) o;

        return getSkipCheckout() != null ? getSkipCheckout().equals(that.getSkipCheckout()) : that.getSkipCheckout() == null;

    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (getSkipCheckout() != null ? getSkipCheckout().hashCode() : 0);
        return result;
    }
}
