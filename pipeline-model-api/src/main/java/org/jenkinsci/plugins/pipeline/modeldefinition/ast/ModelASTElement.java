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

import javax.annotation.Nonnull;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.codehaus.groovy.ast.ASTNode;
import org.jenkinsci.plugins.pipeline.modeldefinition.validator.ModelValidator;

public abstract class ModelASTElement {
    /**
     * The sourceLocation is a reference to whatever section of the original source we're parsed from corresponds to this
     * element. When parsed from Pipeline Script, it's an {@link ASTNode}, and when parsed from JSON, it's a {@link JSONObject}.
     */
    private Object sourceLocation;

    ModelASTElement(Object sourceLocation) {
        this.sourceLocation = sourceLocation;
    }

    public Object getSourceLocation() {
        return sourceLocation;
    }

    public void setSourceLocation(Object sourceLocation) {
        this.sourceLocation = sourceLocation;
    }

    /**
     * Translates this element and any children it may have into JSON conforming to the schema.
     *
     * @return Generally a {@link JSONObject} or {@link JSONArray} but for some leaf nodes, may be a {@link String} or
     *     other simple class.
     */
    public abstract Object toJSON();

    /**
     * Translates this element and any children it may have into Pipeline Config-formatted Groovy, without any indentations.
     *
     * @return A simple {@link String} of Groovy code for this element and its children.
     */
    public abstract String toGroovy();

    /**
     * Called to do whatever validation is necessary for this element. Overridden in most cases.
     *
     * @param validator A {@link ModelValidator} to use for more complicated validation.
     */
    public void validate(@Nonnull ModelValidator validator) {
        // No-op
    }

    /**
     * Removes the source location value from this element.
     */
    public void removeSourceLocation() {
        sourceLocation = null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return "ModelASTElement{}";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return ModelASTElement.class.hashCode();
    }
}

