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
import groovy.transform.ToString
import net.sf.json.JSONArray
import net.sf.json.JSONObject
import org.jenkinsci.plugins.pipeline.modeldefinition.validator.ModelValidator
import org.jenkinsci.plugins.scriptsecurity.sandbox.whitelists.Whitelisted

/**
 * A representation of a method call, including its name and a list of {@link ModelASTMethodArg}s.
 *
 * This is used for things like job properties, triggers and parameter definitions, allowing parsing and validation of
 * the arguments in case they themselves are method calls.
 *
 * @author Andrew Bayer
 */
@ToString(includeSuper = true, includeSuperProperties = true)
@SuppressFBWarnings(value="SE_NO_SERIALVERSIONID")
public class ModelASTMethodCall extends ModelASTElement implements ModelASTMethodArg {
    String name;
    List<ModelASTMethodArg> args = []

    @Whitelisted
    public static Map<String,String> getBlockedSteps() {
        Map<String,String> blockedSteps = [
            "node": "The node step cannot be called as an argument to a method in Declarative Pipelines"
        ]
        blockedSteps.putAll(ModelASTStep.blockedSteps)
        return blockedSteps
    }

    ModelASTMethodCall(Object sourceLocation) {
        super(sourceLocation)
    }

    @Override
    public JSONObject toJSON() {
        JSONArray a = new JSONArray()
        args.each { arg ->
            a.add(arg.toJSON())
        }
        return new JSONObject()
            .accumulate("name", name)
            .accumulate("arguments", a)
    }

    @Override
    public void validate(ModelValidator validator) {
        validator.validateElement(this)

        args.each { a ->
            a?.validate(validator)
        }
    }

    @Override
    public String toGroovy() {
        List<String> argsGroovy = args.collect { a -> a.toGroovy() }
        return "${name}(${argsGroovy.join(", ")})"
    }

    @Override
    public void removeSourceLocation() {
        super.removeSourceLocation()
        args.each { a ->
            a.removeSourceLocation()
        }
    }
}
