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

package org.jenkinsci.plugins.pipeline.modeldefinition;


import edu.umd.cs.findbugs.annotations.SuppressFBWarnings
import org.jenkinsci.plugins.pipeline.modeldefinition.actions.SyntheticContext
import org.jenkinsci.plugins.pipeline.modeldefinition.actions.SyntheticStageMarkerAction
import org.jenkinsci.plugins.scriptsecurity.sandbox.whitelists.Whitelisted
import org.jenkinsci.plugins.workflow.actions.StageAction
import org.jenkinsci.plugins.workflow.cps.CpsFlowExecution
import org.jenkinsci.plugins.workflow.cps.CpsScript
import org.jenkinsci.plugins.workflow.cps.CpsThread
import org.jenkinsci.plugins.workflow.cps.nodes.StepNode
import org.jenkinsci.plugins.workflow.cps.nodes.StepStartNode
import org.jenkinsci.plugins.workflow.graph.FlowNode
import org.jenkinsci.plugins.workflow.support.steps.StageStep

import java.lang.reflect.ParameterizedType

import static org.jenkinsci.plugins.pipeline.modeldefinition.actions.SyntheticStageMarkerAction.Context;

// TODO: Prune like mad once we have step-in-groovy and don't need these static whitelisted wrapper methods.
/**
 * Utility methods for use primarily in CPS-transformed code to avoid excessive global whitelisting.
 *
 * @author Andrew Bayer
 */
@SuppressFBWarnings(value="SE_NO_SERIALVERSIONID")
public class Utils {

    /**
     * Workaround for not having to whitelist isAssignableFrom, metaClass etc to determine whether a field on
     * a class is of a specific type.
     *
     * @param fieldType The type we're checking
     * @param actualClass The class we're inspecting
     * @param fieldName The name of the field - could be a singular when the field name is plural, in which case
     *   we'll get the actual name from actualFieldName(...)
     * @return True if the field exists and is of the given type.
     */
    @Whitelisted
    public static boolean isFieldA(Class fieldType, Class actualClass, String fieldName) {
        def actualFieldName = actualFieldName(actualClass, fieldName)
        def realFieldType = actualClass.metaClass.getMetaProperty(actualFieldName)?.type

        if (realFieldType == null) {
            return false
        } else {
            return realFieldType == fieldType || fieldType.isAssignableFrom(realFieldType)
        }
    }

    /**
     * Gets the actual field name for a possibly-needs-to-be-pluralized name.
     *
     * @param actualClass The class we're inspecting
     * @param fieldName The original field name, which could need to be pluralized.
     * @return The real field name, pluralized if necessary, or null if not found.
     */
    @Whitelisted
    public static String actualFieldName(Class actualClass, String fieldName) {
        if (actualClass.metaClass.getMetaProperty(fieldName) != null) {
            return fieldName
        } else if (actualClass.metaClass.getMetaProperty("${fieldName}s") != null) {
            return "${fieldName}s"
        } else {
            return null
        }
    }

    /**
     * Get the actual field type or contained field type in the case of parameterized types in the inspected class.
     *
     * @param actualClass The class we're inspecting
     * @param fieldName The field name we're looking for, which could get pluralized.
     * @return The class of the field in the inspected class, or the class contained in the list or map.
     */
    @Whitelisted
    public static Class actualFieldType(Class actualClass, String fieldName) {
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

    /**
     * Simple wrapper for isInstance to avoid whitelisting issues.
     *
     * @param c The class to check against
     * @param o The object to check
     * @return True if the object is an instance of the class, false otherwise
     */
    @Whitelisted
    public static boolean instanceOfWrapper(Class c, Object o) {
        return c.isInstance(o)
    }

    /**
     * Simple wrapper for isAssignableFrom to avoid whitelisting issues.
     *
     * @param c The class that should be assignable from
     * @param o The class to check
     * @return True if o can be assigned to c, false otherwise
     */
    @Whitelisted
    public static boolean assignableFromWrapper(Class c, Class o) {
        return c.isAssignableFrom(o)
    }

    @Whitelisted
    public static Object[] toObjectArray(List<Object> origList) {
        return origList.toArray()
    }

    @Whitelisted
    public static boolean hasScmContext(CpsScript script) {
        try {
            // Just rely on SCMVar's own context-checking (via CpsScript) rather than brewing our own.
            script.getProperty("scm")
            return true
        } catch (_) {
            // If we get an IllegalStateException, "checkout scm" isn't valid, so return false.
            return false
        }
    }

    /**
     * Marks the containing stage with this name as a synthetic stage, with the appropriate {@link SyntheticContext}.
     *
     * @param stageName
     * @param context
     */
    @Whitelisted
    static void markSyntheticStage(String stageName, SyntheticContext context) {
        CpsThread thread = CpsThread.current()
        CpsFlowExecution execution = thread.execution

        FlowNode currentNode = execution.currentHeads.find { n ->
            n?.displayName?.equals(stageName)
        }

        currentNode.actions.add(new SyntheticStageMarkerAction(context))
    }
}
