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

import java.util.ArrayList;
import java.util.List;

/**
 * A single library with possible additional information
 *
 * @author Andrew Bayer
 */
public final class ModelASTLibrary extends ModelASTElement {
    private ModelASTValue library;
    private List<ModelASTValue> imports = new ArrayList<>();

    public ModelASTLibrary(Object sourceLocation) {
        super(sourceLocation);
    }

    @Override
    public JSONObject toJSON() {
        JSONObject o = new JSONObject();
        if (library != null) {
            o.accumulate("library", library.toJSON());
        }

        final JSONArray a = new JSONArray();
        for (ModelASTValue v : imports) {
            a.add(v.toJSON());
        }
        o.accumulate("imports", a);
        return o;
    }

    @Override
    public void validate(final ModelValidator validator) {
        validator.validateElement(this);
        if (library != null) {
            library.validate(validator);
        }
        for (ModelASTValue v : imports) {
            v.validate(validator);
        }
    }

    @Override
    public String toGroovy() {
        StringBuilder result = new StringBuilder("lib(");
        if (!imports.isEmpty()) {
            result.append("library: ");
            if (library != null) {
                result.append(library.toGroovy());
            }
            result.append(", imports: ");
            result.append(valueListToString(imports));
        } else {
            result.append(library.toGroovy());
        }
        result.append(")\n");
        return result.toString();
    }

    @Override
    public void removeSourceLocation() {
        super.removeSourceLocation();
        if (library != null) {
            library.removeSourceLocation();
        }
        for (ModelASTValue v : imports) {
            v.removeSourceLocation();
        }
    }

    public ModelASTValue getLibrary() {
        return library;
    }

    public void setLibrary(ModelASTValue library) {
        this.library = library;
    }

    public List<ModelASTValue> getImports() {
        return imports;
    }

    public void setImports(List<ModelASTValue> imports) {
        this.imports = imports;
    }

    @Override
    public String toString() {
        return "ModelASTLibrary{" +
                "library=" + library +
                ", imports=" + imports +
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

        ModelASTLibrary that = (ModelASTLibrary) o;

        if (getLibrary() != null ? !getLibrary().equals(that.getLibrary()) : that.getLibrary() != null) {
            return false;
        }

        return getImports() != null ? getImports().equals(that.getImports()) : that.getImports() == null;

    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (getLibrary() != null ? getLibrary().hashCode() : 0);
        result = 31 * result + (getImports() != null ? getImports().hashCode() : 0);
        return result;
    }
}
