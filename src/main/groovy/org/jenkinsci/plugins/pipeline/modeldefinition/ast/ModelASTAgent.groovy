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

import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import org.jenkinsci.plugins.pipeline.modeldefinition.model.Agent
import org.jenkinsci.plugins.pipeline.modeldefinition.validator.ModelValidator

/**
 * Represents what context in which to run the build - i.e., which label to run on, what Docker agent to run in, etc.
 * Corresponds to {@link Agent}.
 *
 * @author Andrew Bayer
 */
@ToString(includeSuper = true, includeSuperProperties = true)
@EqualsAndHashCode(callSuper = true)
public final class ModelASTAgent extends ModelASTElement {
    ModelASTArgumentList args

    public ModelASTAgent(Object sourceLocation) {
        super(sourceLocation)
    }

    @Override
    public Object toJSON() {
        return args.toJSON()
    }

    @Override
    public void validate(ModelValidator validator) {
        validator.validateElement(this)

        args?.validate(validator)
    }

    @Override
    public String toGroovy() {
        String argStr
        // TODO: Stop special-casing agent none.
        if (args instanceof ModelASTSingleArgument &&
            (args.value.value.equals("none") || args.value.value.equals("any"))) {
            argStr = args.value.value
        } else {
            argStr = args.toGroovy()
        }
        return "agent ${argStr}\n"
    }

    @Override
    public void removeSourceLocation() {
        super.removeSourceLocation()
        args.removeSourceLocation()
    }
}
