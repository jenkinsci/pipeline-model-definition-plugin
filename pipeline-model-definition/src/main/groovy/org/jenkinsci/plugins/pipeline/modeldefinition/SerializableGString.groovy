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
package org.jenkinsci.plugins.pipeline.modeldefinition

import org.codehaus.groovy.runtime.GStringImpl


class SerializableGString implements Serializable {
    public String[] strings
    public Object[] values

    public GString rehydrate() {
        Object[] vals = new Object[values.length]

        values.eachWithIndex { def entry, int i ->
            if (entry instanceof SerializableGString) {
                vals[i] = entry.rehydrate()
            } else {
                vals[i] = entry
            }
        }

        return new GStringImpl(vals, strings)
    }

    public static dehydrate(GString g) {
        String[] strs = g.strings
        Object[] vals = new Object[g.valueCount]
        g.values.eachWithIndex { def entry, int i ->
            if (entry instanceof GString) {
                vals[i] = dehydrate(entry)
            } else {
                vals[i] = entry
            }
        }

        return new SerializableGString(strings: strs, values: vals)
    }

    public static int serialVersionUID = 1L
}
