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
import hudson.model.Run
import org.codehaus.groovy.control.CompilationFailedException
import org.jenkinsci.plugins.scriptsecurity.sandbox.whitelists.Whitelisted
import org.jenkinsci.plugins.workflow.cps.CpsScript

/**
 * IMPORTANT: This class must inherit from CpsScript or Declarative Pipeline will halt and catch fire.
 * Exmples:
 * <ul>
 * <li> this class must inherit the sleep method from CpsScript (or implement the same behavior itself).
 * <li> script step resolution can't find globals
 * <li> Groovy's getProperty is not whitelisted by default except for on CpsScript
 * </ul>
 *
 * @author Liam Newman
 */
@SuppressFBWarnings(value="SE_NO_SERIALVERSIONID")
class RuntimeContainerBase extends CpsScript {

    private static CpsScript workflowScript
    private static final Map<Class,RuntimeContainerBase> classMap = new HashMap<>()

    @Whitelisted
    RuntimeContainerBase() throws IOException {
        super()
    }

    @Whitelisted
    static void initialize(CpsScript script) {
        workflowScript = script
    }

    @Whitelisted
    static RuntimeContainerBase getInstance(Class instanceClass) throws IOException {
        if (!classMap.containsKey(instanceClass)) {
            classMap[instanceClass] = instanceClass.getConstructor().newInstance()
        }
        return classMap[instanceClass]
    }

    /*
     * We want all classes that derive from this one to behave as though they are the main
     * "WorkflowScript" class. The methods below form a passthrough.
     */

    // Script overrides
    @Override
    Object run() {
        return null
    }

    @Override
    Binding getBinding() {
        return workflowScript.getBinding()
    }

    @Override
    void setBinding(Binding binding) {
        workflowScript.setBinding(binding)
    }

    @Override
    Object getProperty(String propertyName) {
        return workflowScript.getProperty(propertyName)
    }

    @Override
    void setProperty(String propertyName, Object newValue) {
        workflowScript.setProperty(propertyName, newValue)
    }


    // CpsScript overrides

    @Override
    Run<?, ?> $build() throws IOException {
        return workflowScript.$build()
    }

    @Override
    Run<?, ?> $buildNoException() {
        return workflowScript.$buildNoException()
    }

    @Override
    Object evaluate(String script) throws CompilationFailedException {
        return workflowScript.evaluate(script)
    }

    @Override
    Object evaluate(File file) throws CompilationFailedException, IOException {
        return workflowScript.evaluate(file)
    }

    @Override
    void run(File file, String[] arguments) throws CompilationFailedException, IOException {
        workflowScript.run(file, arguments)
    }

}
