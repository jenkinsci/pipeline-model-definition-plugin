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

import org.jenkinsci.plugins.pipeline.modeldefinition.model.AbstractBuildConditionResponder
import org.jenkinsci.plugins.pipeline.modeldefinition.model.ClosureContentsChecker
import org.jenkinsci.plugins.pipeline.modeldefinition.model.Environment
import org.jenkinsci.plugins.pipeline.modeldefinition.model.MappedClosure
import org.jenkinsci.plugins.pipeline.modeldefinition.model.MethodMissingWrapper
import org.jenkinsci.plugins.pipeline.modeldefinition.model.MethodsToList
import org.jenkinsci.plugins.pipeline.modeldefinition.model.NestedModel
import org.jenkinsci.plugins.pipeline.modeldefinition.model.Options
import org.jenkinsci.plugins.pipeline.modeldefinition.model.PropertiesToMap
import org.jenkinsci.plugins.pipeline.modeldefinition.model.Stage
import org.jenkinsci.plugins.pipeline.modeldefinition.model.StepsBlock
import org.jenkinsci.plugins.workflow.cps.CpsScript

/**
 * CPS-transformed code for translating from the closure argument to the pipeline step into the runtime model.
 *
 * @author Andrew Bayer
 */
public class ClosureModelTranslator implements MethodMissingWrapper, Serializable {
    Map<String,Object> actualMap = [:]
    Class<NestedModel> actualClass
    CpsScript script

    /**
     * Placeholder to make sure 'agent none' works.
     */
    String none = "none"

    /**
     * Placeholder to make sure 'agent any' works.
     */
    String any = "any"

    ClosureModelTranslator(Class clazz, CpsScript s) {
        actualClass = clazz
        this.script = s
    }

    NestedModel toNestedModel() {
        NestedModel m = actualClass.newInstance()
        m.modelFromMap(actualMap)
        return m
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

        if (Utils.assignableFromWrapper(ClosureContentsChecker.class, actualClass)) {
            actualMap[methodName] = argValue
        }
        // If we're already in a MappedClosure, we may need to recurse if the value itself is a closure.
        else if (Utils.assignableFromWrapper(MappedClosure.class, actualClass) && argValue != null) {
            // If the containing class is a MappedClosure and the argument is a Closure, it's most likely a build responder,
            // In which case we don't recurse, or it's just normal nested MappedClosure fun, in which case we *do* recurse.
            if (Utils.instanceOfWrapper(Closure.class, argValue)) {
                if (Utils.assignableFromWrapper(AbstractBuildConditionResponder.class, actualClass)) {
                    actualMap[methodName] = createStepsBlock(argValue)
                } else {
                    def ctm = new ClosureModelTranslator(MappedClosure.class, script)

                    resolveClosure(argValue, ctm)

                    actualMap[methodName] = ctm.getMap()
                }
            } else {
                // And if the argValue isn't a closure, we just set the keyName/value directly.
                actualMap[methodName] = argValue
            }
        } else {
            def resultValue
            def actualFieldName = Utils.actualFieldName(actualClass, methodName)
            def actualType = Utils.actualFieldType(actualClass, methodName)

            // We care about the field name actually being a thing.
            if (actualFieldName != null) {
                // Due to Stage taking an argument, not just a closure, we need to handle it differently.
                if (Utils.assignableFromWrapper(Stage.class, actualType)) {
                    Object[] origArgs = args
                    String n = origArgs[0]
                    def ctm = new ClosureModelTranslator(actualType, script)

                    if (Utils.instanceOfWrapper(Closure.class, origArgs[1])) {
                        resolveClosure(origArgs[1], ctm)
                    }
                    ctm.actualMap.name = n
                    resultValue = ctm.toNestedModel()
                }
                // If the argument is a Closure, we've got a few possibilities.
                else if (argValue != null && Utils.instanceOfWrapper(Closure.class, argValue)) {

                    // If it's a StepsBlock, create the object.
                    if (Utils.assignableFromWrapper(StepsBlock.class, actualType)) {
                        resultValue = createStepsBlock(argValue)
                    }
                    // If it's Options, use the special translator.
                    else if (Utils.assignableFromWrapper(Options.class, actualType)) {
                        def ot = new OptionsTranslator(script)
                        resolveClosure(argValue, ot)
                        resultValue = ot.toOptions()
                    }
                    // if it's a PropertiesToMap, we use PropertiesToMapTranslator to translate it into the right form.
                    else if (Utils.assignableFromWrapper(PropertiesToMap.class, actualType)) {
                        def ptm = new PropertiesToMapTranslator(script, Utils.assignableFromWrapper(Environment.class, actualType))
                        resolveClosure(argValue, ptm)
                        resultValue = ptm.toNestedModel(actualType)
                    } else if (Utils.assignableFromWrapper(MethodsToList.class, actualType)) {
                        def mtl = new MethodsToListTranslator(script, actualType)
                        resolveClosure(argValue, mtl)
                        resultValue = mtl.toListModel(actualType)
                    }
                    // And lastly, recurse - this must be another container block.
                    else {
                        def ctm = new ClosureModelTranslator(actualType, script)

                        resolveClosure(argValue, ctm)
                        // If it's a ModelForm, the result value is the ModelForm equivalent of the Map.
                        if (Utils.assignableFromWrapper(NestedModel.class, actualType)) {
                            resultValue = ctm.toNestedModel()
                        } else {
                            // TODO: Put some kind of error handling in here. Shouldn't actually be possible to get here, but...
                            // error!
                        }
                    }
                }
                // And if it's not any special case, just use the args.
                else {
                    resultValue = argValue
                }

                // Now that we've got a result value, do something with it!
                // Behavior is a bit different if the field is a list - if so, we need to make sure the field is init'd,
                // And add the value to the list. Otherwise, just set the field/value pair.
                if (Utils.isFieldA(List.class, actualClass, methodName)) {
                    if (!actualMap.containsKey(actualFieldName)) {
                        actualMap[actualFieldName] = []
                    }
                    actualMap[actualFieldName].add(resultValue)
                } else {
                    actualMap[actualFieldName] = resultValue
                }
            }
        }
        this
    }

    public Map<String, Object> getMap() {
        def mapCopy = [:]
        mapCopy.putAll(actualMap)
        return mapCopy
    }

    /**
     * @param c The closure to wrap.
     */
    private StepsBlock createStepsBlock(c) {
        // Jumping through weird hoops to get around the ejection for cases of JENKINS-26481.
        StepsBlock wrapper = new StepsBlock()
        wrapper.setClosure(c)

        return wrapper
    }

    /**
     * Resolve an object that can be cast to {@link Closure} using a translator delegate, such as
     * {@link ClosureModelTranslator} or {@link PropertiesToMapTranslator}
     *
     * @param closureObj The object representing the closure block we're resolving
     * @param translator The translator delegate.
     */
    private void resolveClosure(Object closureObj, Object translator) {
        Closure argClosure = closureObj
        argClosure.delegate = translator
        argClosure.resolveStrategy = Closure.DELEGATE_FIRST
        argClosure.call()
    }
}

