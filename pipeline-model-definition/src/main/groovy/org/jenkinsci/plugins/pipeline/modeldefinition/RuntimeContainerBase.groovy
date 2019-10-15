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


import edu.umd.cs.findbugs.annotations.SuppressFBWarnings
import org.codehaus.groovy.runtime.InvokerHelper
import org.jenkinsci.plugins.scriptsecurity.sandbox.whitelists.Whitelisted
import org.jenkinsci.plugins.workflow.cps.CpsScript

/**
 *
 *
 * @author Liam Newman
 */
@SuppressFBWarnings(value="SE_NO_SERIALVERSIONID")
class RuntimeContainerBase {

    private static CpsScript that
    private static Map<Class,RuntimeContainerBase> classMap = new HashMap<>()

    @Whitelisted
    RuntimeContainerBase() {
    }

    @Whitelisted
    static void initialize(CpsScript script) {
        that = script
    }

    @Whitelisted
    static Object getInstance(Class instanceClass) {
        if (!classMap.containsKey(instanceClass)) {
            classMap[instanceClass] = instanceClass.getConstructor().newInstance()
        }
        return classMap[instanceClass]
    }

    /**
     * @see CpsScript#sleep(long)
     */
    @Whitelisted
    Object sleep(long arg) {
        return InvokerHelper.invokeMethod(that, "sleep", arg)
    }


/* Overriding methods defined in DefaultGroovyMethods
        if we don't do this, definitions in DefaultGroovyMethods get called. One problem
        is that most of them are not whitelisted, and the other problem is that they don't
        always forward the call to the closure owner.

        In CpsScript we override these methods and redefine them as variants of the 'echo' step,
        so for this to work the same from closure body, we need to redefine them.
 */
    @Whitelisted
    void println(Object arg) {
        InvokerHelper.invokeMethod(that, "println", [arg])
    }

    @Whitelisted
    void println() {
        InvokerHelper.invokeMethod(that, "println",[])
    }

    @Whitelisted
    void print(Object arg) {
        InvokerHelper.invokeMethod(that, "print", [arg]);
    }

    @Whitelisted
    void printf(String format, Object value) {
        InvokerHelper.invokeMethod(that, "printf", [])
    }
}
