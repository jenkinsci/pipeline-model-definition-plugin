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


package org.jenkinsci.plugins.pipeline.modeldefinition

import org.jenkinsci.plugins.pipeline.modeldefinition.model.MethodMissingWrapper
import org.jenkinsci.plugins.pipeline.modeldefinition.model.NestedModel
import org.jenkinsci.plugins.pipeline.modeldefinition.model.Wrappers
import org.jenkinsci.plugins.pipeline.modeldefinition.model.WrappersToMap
import org.jenkinsci.plugins.workflow.cps.CpsScript

/**
 * Translates a closure containing a sequence of wrapper calls into a {@link WrappersToMap} implementation.
 *
 * @author Andrew Bayer
 */
public class WrappersToMapTranslator implements MethodMissingWrapper, Serializable {
    Map<String,Object> actualMap = [:]
    CpsScript script

    WrappersToMapTranslator(CpsScript script) {
        this.script = script
    }

    def methodMissing(String s, args) {
        def argVal
        if (args instanceof List || args instanceof Object[]) {
            if (args.size() > 0) {
                argVal = args[0]
            } else {
                argVal = null
            }
        } else {
            argVal = args
        }

        if (s in Wrappers.getEligibleSteps()) {
            actualMap[s] = argVal
            return true
        } else {
            if (argVal != null) {
                return script."${s}"(argVal)
            } else {
                return script."${s}"()
            }
        }
    }

    public Map<String, Object> getMap() {
        def mapCopy = [:]
        mapCopy.putAll(actualMap)
        return mapCopy
    }

    NestedModel toNestedModel(Class<NestedModel> c) {
        NestedModel m = c.newInstance()
        m.modelFromMap(actualMap)
        return m
    }
}
