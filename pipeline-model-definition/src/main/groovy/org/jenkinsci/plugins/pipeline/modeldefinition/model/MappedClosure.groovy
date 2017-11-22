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
package org.jenkinsci.plugins.pipeline.modeldefinition.model

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString


/**
 * Helper class for translating from a closure to a map.
 *
 * @author Andrew Bayer
 * @see AbstractBuildConditionResponder
 * @see Environment
 * @see Tools
 */
@ToString
@EqualsAndHashCode
@SuppressFBWarnings(value="SE_NO_SERIALVERSIONID")
abstract class MappedClosure<O,M extends MappedClosure<O,M>> implements Serializable {

    Map<String,O> resultMap = [:]

    MappedClosure() {
    }

    MappedClosure(Map<String,O> inMap) {
        this.resultMap.putAll(inMap)
    }

    O remove(String p) {
        return resultMap.remove(p)
    }

    void put(String k, O v) {
        resultMap.put(k, v)
    }

    Map<String, Object> getMap() {
        def mapCopy = [:]
        mapCopy.putAll(resultMap)
        return mapCopy
    }

    static final int serialVersionUID = 1L
}
