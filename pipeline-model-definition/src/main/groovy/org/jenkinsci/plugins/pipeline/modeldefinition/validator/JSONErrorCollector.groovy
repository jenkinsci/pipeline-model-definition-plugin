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
package org.jenkinsci.plugins.pipeline.modeldefinition.validator

import com.github.fge.jsonschema.jsonpointer.JsonPointer
import com.github.fge.jsonschema.tree.JsonTree
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings
import net.sf.json.JSONArray
import net.sf.json.JSONObject
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTElement

/**
 * Error collector for JSON parsing.
 *
 * @author Andrew Bayer
 */
@SuppressFBWarnings(value="SE_NO_SERIALVERSIONID")
class JSONErrorCollector extends ErrorCollector {
    List<JSONErrorPair> errors

    JSONErrorCollector() {
        errors = []
    }

    @Override
    void error(ModelASTElement src, String message) {
        JsonTree json = null
        if (src.sourceLocation instanceof JsonTree) {
            json = (JsonTree)src.sourceLocation
        }
        this.errors.add(new JSONErrorPair(json?.getPointer(), message))
    }

    @Override
    int getErrorCount() {
        return errors.size()
    }

    @Override
    List<String> errorsAsStrings() {
        return errors.collect { it.message }
    }

    @Override
    JSONArray asJson() {
        JSONArray a = new JSONArray()
        errors.each { a.add(it.jsonError) }

        return a
    }

    static final class JSONErrorPair {
        final String message
        final JsonPointer location

        JSONErrorPair(JsonPointer location, String message) {
            this.location = location
            this.message = message
        }

        JSONObject getJsonError() {
            JSONObject o = new JSONObject()
            JSONArray a = new JSONArray()

            location.iterator().each { t ->
                a.add(t.toString())
            }

            o.accumulate("location", a)
            o.accumulate("error", message)

            return o
        }
    }
}
