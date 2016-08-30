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
package org.jenkinsci.plugins.pipeline.modeldefinition.ast

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import net.sf.json.JSONArray
import net.sf.json.JSONObject
import org.jenkinsci.plugins.pipeline.modeldefinition.model.Tools
import org.jenkinsci.plugins.pipeline.modeldefinition.validator.ModelValidator

/**
 * Represents a map of tool types to tool names (i.e., the name of the configured installation). Corresponds to {@link Tools}.
 * @author Andrew Bayer
 */
@ToString(includeSuper = true, includeSuperProperties = true)
@EqualsAndHashCode(callSuper = true)
@SuppressFBWarnings(value="SE_NO_SERIALVERSIONID")
public final class ModelASTTools extends ModelASTElement {
    Map<ModelASTKey,ModelASTValue> tools = [:]

    public ModelASTTools(Object sourceLocation) {
        super(sourceLocation)
    }

    @Override
    public JSONArray toJSON() {
        JSONArray a = new JSONArray()
        tools.each { k,v ->
            JSONObject o = new JSONObject()
            o.accumulate("key", k.toJSON())
            o.accumulate("value", v.toJSON())
            a.add(o)
        }
        return a

    }

    @Override
    public void validate(ModelValidator validator) {
        validator.validateElement(this)

        tools.each { k, v ->
            k?.validate(validator)
            v?.validate(validator)
        }
    }

    @Override
    public String toGroovy() {
        String keysAndValues = tools.collect { k, v ->
            "${k.toGroovy()} ${v.toGroovy()}"
        }.join("\n")
        return "tools {\n${keysAndValues}\n}\n"
    }
}
