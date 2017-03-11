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
import org.jenkinsci.plugins.pipeline.modeldefinition.SerializableGString


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
public abstract class MappedClosure<O,M extends MappedClosure<O,M>>
    implements NestedModel, MethodMissingWrapper, Serializable {

    @Delegate Map<String,O> resultMap = [:]

    public MappedClosure() {
    }

    public MappedClosure(Map<String,O> inMap) {
        this.modelFromMap(inMap)
    }

    /**
     * Convenience method to allow for "foo 'bar'" style population of the underlying map.
     *
     * @param methodName Key name, basically.
     * @param args First element will be a String hopefully.
     *
     * @return this
     */
    def methodMissing(String methodName, args) {
        def argValue
        if (args.length > 1) {
            argValue = args
        } else if (args.length == 1) {
            argValue = args[0]
        }

        if (argValue != null && argValue instanceof GString) {
            argValue = SerializableGString.dehydrate(argValue)
        }

        this."${methodName}" = argValue

        this
    }

    @Override
    public void modelFromMap(Map<String,Object> inMap) {
        def newMap = new TreeMap<String,Object>()
        inMap.each { k, v ->
            if (v instanceof GString) {
                newMap.put(k, SerializableGString.dehydrate(v))
            } else {
                newMap.put(k, v)
            }
        }
        this.resultMap.putAll(newMap)
    }

    public Map<String, Object> getMap() {
        def mapCopy = [:]
        resultMap.each { k, v ->
            if (v instanceof SerializableGString) {
                mapCopy.put(k, v.rehydrate())
            } else if (v instanceof MappedClosure) {
                mapCopy.put(k, v.getMap())
            } else {
                mapCopy.put(k, v)
            }
        }
        return mapCopy
    }

    static final int serialVersionUID = 1L
}
