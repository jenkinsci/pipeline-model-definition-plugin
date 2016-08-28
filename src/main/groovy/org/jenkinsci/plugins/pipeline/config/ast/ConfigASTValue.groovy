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
package org.jenkinsci.plugins.pipeline.config.ast

import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import net.sf.json.JSONObject

/**
 * Represents the value in a key/value pair, as used in {@link ConfigASTEnvironment}, {@link ConfigASTNamedArgumentList} and elsewhere.
 *
 * @author Andrew Bayer
 * @author Kohsuke Kawaguchi
 */
@ToString(includeSuper = true, includeSuperProperties = true)
@EqualsAndHashCode(callSuper = true)
public abstract class ConfigASTValue extends ConfigASTElement {
    /* package */ ConfigASTValue(Object sourceLocation, Object v) {
        super(sourceLocation)
        this.value = v
    }

    Object value

    /**
     * If the value can be determined without side-effect at AST parsing time,
     * this method returns true, and {@Link #getValue()} returns its value.
     */
    public abstract boolean isConstant();

    /**
     * Returns a value or an expression that represents this value.
     *
     * This model is used at the compile time, so it's not always possible
     * to obtain the actual value. Imagine something like {@code secret('12345')}
     * or even {@code pow(2,10)}.
     *
     * In case the value is an expression, this method returns a string represntation
     * suitable for the editor.
     *
     * For example, if the value is {@code foobar(x)}, we want the editor to show
     * "${foobar(x)}"
     */
    public Object getValue() {
        return value
    }

    @Override
    public JSONObject toJSON() {
        return new JSONObject()
            .accumulate("isConstant", isConstant())
            .accumulate("value", getValue())
    }

    public static final ConfigASTValue fromConstant(final Object o, Object sourceLocation) {
        return new ConfigASTValue(sourceLocation, o) {
            @Override
            boolean isConstant() {
                return true;
            }

            @Override
            public String toGroovy() {
                if (o instanceof String) {
                    return "'${o}'"
                } else {
                    return o
                }
            }
        }
    }

    public static final ConfigASTValue fromGString(String gstring, Object sourceLocation) {
        return new ConfigASTValue(sourceLocation, gstring) {
            @Override
            boolean isConstant() {
                return false;
            }

            @Override
            public String toGroovy() {
                return gstring
            }
        }
    }
}
