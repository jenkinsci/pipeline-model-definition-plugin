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

package org.jenkinsci.plugins.pipeline.modeldefinition.withscript;

import org.jenkinsci.plugins.workflow.cps.CpsScript;

import java.io.Serializable;

public abstract class WithScriptScript<T extends WithScriptDescribable<T>> implements Serializable {
    protected CpsScript script;
    protected T describable;

    public WithScriptScript(CpsScript s, T d) {
        this.script = s;
        this.describable = d;
    }

    /**
     * Takes a string and makes sure it starts/ends with double quotes so that it can be evaluated correctly.
     *
     * @param s The original string
     * @return Either the original string, if it already starts/ends with double quotes, or the original string
     * prepended/appended with double quotes.
     */
    public static String prepareForEvalToString(String s) {
        String toEval = s != null ? s : "";
        if (!toEval.startsWith("\"") || !toEval.endsWith("\"")) {
            toEval = '"' + toEval + '"';
        }

        return toEval;
    }

}
