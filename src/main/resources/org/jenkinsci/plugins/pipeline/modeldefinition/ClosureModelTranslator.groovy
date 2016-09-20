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

import com.cloudbees.groovy.cps.NonCPS
import org.jenkinsci.plugins.pipeline.modeldefinition.model.*

import java.lang.reflect.ParameterizedType

/**
 * CPS-transformed code for translating from the closure argument to the pipeline step into the runtime model.
 *
 * @author Andrew Bayer
 */
public class ClosureModelTranslator implements MethodMissingWrapper, Serializable {
    Map<String, Object> actualMap = [:]
    Class<NestedModel> actualClass

    /**
     * Placeholder to make sure 'agent none' works.
     */
    boolean none = false

    /**
     * Placeholder to make sure 'agent any' works.
     */
    boolean any = true

    ClosureModelTranslator(Class clazz) {
        actualClass = clazz
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

        // If we're already in a MappedClosure, we may need to recurse if the value itself is a closure.
        if (MappedClosure.class.isAssignableFrom(actualClass) && argValue != null) {
            // If the containing class is a MappedClosure and the argument is a Closure, it's most likely a build responder,
            // In which case we don't recurse, or it's just normal nested MappedClosure fun, in which case we *do* recurse.
            if (Closure.class.isInstance(argValue)) {
                if (AbstractBuildConditionResponder.class.isAssignableFrom(actualClass)) {
                    actualMap[methodName] = createStepsBlock(argValue)
                } else {
                    def ctm = new ClosureModelTranslator(MappedClosure.class)

                    resolveClosure(argValue, ctm)

                    actualMap[methodName] = ctm.getMap()
                }
            } else {
                // And if the argValue isn't a closure, we just set the keyName/value directly.
                actualMap[methodName] = argValue
            }
        } else {
            def resultValue
            def actualFieldName = actualFieldName(actualClass, methodName)

            // We care about the field name actually being a thing.
            if (actualFieldName != null) {
                def actualType = actualFieldType(actualClass, methodName)

                // Handle StepBlockWithOtherArgs *first*, since we won't recurse at all on them.
                // If the field is an implementation of StepBlockWithOtherArgs, we need to just call its constructor with the args.
                // Note that only Stage is a StepBlockWithOtherArgs currently, but that may be reused later.
                if (StepBlockWithOtherArgs.class.isAssignableFrom(actualType)) {
                    Object[] origArgs = args
                    List<Object> blockParams = []
                    for (int i = 0; i < origArgs.size(); i++) {
                        def thisArg = origArgs[i]
                        // If the argument is a Closure, create a StepsBlock of it.
                        if (Closure.class.isInstance(thisArg)) {
                            blockParams.add(createStepsBlock(thisArg))
                        } else {
                            // Otherwise, just add the parameter.
                            blockParams.add(thisArg)
                        }
                    }
                    resultValue = actualType.newInstance(blockParams.toArray())
                }
                // If the argument is a Closure, we've got a few possibilities.
                else if (argValue != null && Closure.class.isInstance(argValue)) {

                    // If it's a StepsBlock, create the object.
                    if (StepsBlock.class.isAssignableFrom(actualType)) {
                        resultValue = createStepsBlock(argValue)
                    }
                    // if it's a PropertiesToMap, we use PropertiesToMapTranslator to translate it into the right form.
                    else if (PropertiesToMap.class.isAssignableFrom(actualType)) {
                        def ptm = new PropertiesToMapTranslator()
                        resolveClosure(argValue, ptm)
                        resultValue = ptm.toNestedModel(actualType)
                    }
                    // And lastly, recurse - this must be another container block.
                    else {
                        def ctm = new ClosureModelTranslator(actualType)

                        resolveClosure(argValue, ctm)
                        // If it's a ModelForm, the result value is the ModelForm equivalent of the Map.
                        if (NestedModel.class.isAssignableFrom(actualType)) {
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
                if (isFieldA(List.class, actualClass, methodName)) {
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
     * {@link ClosureModelTranslator}.
     *
     * @param closureObj The object representing the closure block we're resolving
     * @param translator The translator delegate.
     */
    private void resolveClosure(Object closureObj, Object translator) {
        Closure argClosure = closureObj
        argClosure.delegate = translator
        argClosure.resolveStrategy = Closure.DELEGATE_ONLY
        argClosure.call()
    }

    @NonCPS
    private String actualFieldName(Class actualClass, String fieldName) {
        if (actualClass.metaClass.getMetaProperty(fieldName) != null) {
            return fieldName
        } else if (actualClass.metaClass.getMetaProperty("${fieldName}s") != null) {
            return "${fieldName}s"
        } else {
            return null
        }
    }

    @NonCPS
    private Class actualFieldType(Class actualClass, String fieldName) {
        def actualFieldName = actualFieldName(actualClass, fieldName)
        if (actualFieldName == null) {
            return null
        } else {
            def field = actualClass.getDeclaredFields().find { !it.isSynthetic() && it.name == actualFieldName }
            // If the field's a ParameterizedType, we need to check it to see if it's containing a Plumber class.
            if (field.getGenericType() instanceof ParameterizedType) {
                if (Map.class.isAssignableFrom(field.getType())) {
                    return (Class) ((ParameterizedType) field.getGenericType()).getActualTypeArguments()[1]
                } else {
                    // First class listed in the actual type arguments - we ignore anything past this because eh.
                    return (Class) ((ParameterizedType) field.getGenericType()).getActualTypeArguments()[0]
                }
            } else {
                return field.getType()
            }
        }

    }

    @NonCPS
    private boolean isFieldA(Class fieldType, Class actualClass, String fieldName) {
        def actualFieldName = actualFieldName(actualClass, fieldName)
        def realFieldType = actualClass.metaClass.getMetaProperty(actualFieldName)?.type

        if (realFieldType == null) {
            return false
        } else {
            return realFieldType == fieldType || fieldType.isAssignableFrom(realFieldType)
        }
    }

}
