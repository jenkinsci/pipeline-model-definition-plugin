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

/**
 * Represents the special step which are executed without validation against the declarative subset.
 * @see ModelASTScriptBlock
 * @see ModelASTWhen
 */
public abstract class AbstractModelASTCodeBlock extends ModelASTStep {

    protected AbstractModelASTCodeBlock(Object sourceLocation, String name) {
        super(sourceLocation);
        this.setName(name);
    }

    @Override
    public String toGroovy() {
        StringBuilder result = new StringBuilder(getName()).append(" {\n");
        if (getArgs() != null
                && getArgs() instanceof ModelASTSingleArgument
                && ((ModelASTSingleArgument) getArgs()).getValue()!=null
                && ((ModelASTSingleArgument) getArgs()).getValue().isLiteral()) {
            result.append(((ModelASTSingleArgument) getArgs()).getValue().getValue());
        } else if (getArgs() != null) {
            result.append(getArgs().toGroovy());
        }
        result.append("\n}\n");
        return result.toString();
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{" +
                "name='" + getName() + '\'' +
                ", args=" + getArgs() +
                "}";
    }
}
