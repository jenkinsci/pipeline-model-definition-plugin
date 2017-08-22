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
package org.jenkinsci.plugins.pipeline.modeldefinition.model;

import hudson.ExtensionComponent;
import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.Util;
import org.jenkinsci.plugins.structs.SymbolLookup;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.support.steps.build.RunWrapper;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Extension point for build conditions.
 *
 * @author Andrew Bayer
 */
public abstract class BuildCondition implements Serializable, ExtensionPoint {

    /**
     * @deprecated since 1.2
     */
    @Deprecated
    public boolean meetsCondition(WorkflowRun r) {
        if (Util.isOverridden(BuildCondition.class, getClass(), "meetsCondition", WorkflowRun.class)) {
            return getClass().cast(this).meetsCondition(r);
        } else {
            return true;
        }
    }

    public boolean meetsCondition(WorkflowRun r, Exception error) {
        if (Util.isOverridden(BuildCondition.class, getClass(), "meetsCondition", WorkflowRun.class, Exception.class)) {
            return getClass().cast(this).meetsCondition(r, error);
        } else {
            return meetsCondition(r);
        }
    }

    /**
     * @deprecated since 1.2
     */
    @Deprecated
    public final boolean meetsCondition(Object runWrapperObj) {
        if (Util.isOverridden(BuildCondition.class, getClass(), "meetsCondition", RunWrapper.class)) {
            RunWrapper runWrapper = (RunWrapper) runWrapperObj;
            WorkflowRun run = (WorkflowRun) runWrapper.getRawBuild();
            return meetsCondition(run);
        } else {
            return meetsCondition(runWrapperObj, null);
        }
    }

    public boolean meetsCondition(Object runWrapperObj, Exception error) {
        RunWrapper runWrapper = (RunWrapper)runWrapperObj;
        WorkflowRun run = (WorkflowRun)runWrapper.getRawBuild();

        return meetsCondition(run, error);
    }

    public abstract String getDescription();

    /**
     * All the registered {@link BuildCondition}s.
     *
     * @return A list of all registered {@link BuildCondition}s.
     */
    public static ExtensionList<BuildCondition> all() {
        return ExtensionList.lookup(BuildCondition.class);
    }

    public static List<String> getOrderedConditionNames() {
        List<String> orderedConditions = new ArrayList<>();

        List<ExtensionComponent<BuildCondition>> extensionComponents = new ArrayList<>(all().getComponents());
        Collections.sort(extensionComponents);

        for (ExtensionComponent<BuildCondition> extensionComponent: extensionComponents) {
            BuildCondition b = extensionComponent.getInstance();
            Set<String> symbolValues = SymbolLookup.getSymbolValue(b);

            if (!symbolValues.isEmpty()) {
                orderedConditions.add(symbolValues.iterator().next());
            }
        }

        return orderedConditions;
    }

    public static Map<String, BuildCondition> getConditionMethods() {
        Map<String,BuildCondition> conditions = new HashMap<>();

        for (BuildCondition b: all()) {

            Set<String> symbolValues = SymbolLookup.getSymbolValue(b);

            if (!symbolValues.isEmpty()) {
                conditions.put(symbolValues.iterator().next(), b);
            }
        }
        return conditions;
    }

    private static final long serialVersionUID = 1L;
}
