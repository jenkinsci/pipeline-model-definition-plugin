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

import com.google.common.cache.LoadingCache
import hudson.Extension
import hudson.ExtensionList
import hudson.FilePath
import hudson.Launcher
import hudson.init.InitMilestone
import hudson.init.Initializer
import hudson.model.JobPropertyDescriptor
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTMethodCall
import org.jenkinsci.plugins.pipeline.modeldefinition.options.DeclarativeOptionDescriptor
import org.jenkinsci.plugins.workflow.steps.StepDescriptor
import org.kohsuke.accmod.Restricted
import org.kohsuke.accmod.restrictions.NoExternalUse


@Restricted(NoExternalUse.class)
@Extension
public class TypeLookupCache {
    private LoadingCache<Object,Map<String,String>> propertyTypeCache

    private LoadingCache<Object,Map<String,String>> optionTypeCache

    private LoadingCache<Object,Map<String,String>> wrapperStepsTypeCache

    private static final Object OPTION_CACHE_KEY = new Object()
    private static final Object PROPS_CACHE_KEY = new Object()
    private static final Object WRAPPER_STEPS_KEY = new Object()

    public static TypeLookupCache getPublicCache() {
        return ExtensionList.lookup(TypeLookupCache.class).get(0);
    }

    @Initializer(after = InitMilestone.EXTENSIONS_AUGMENTED)  // Prevents potential leakage on reload
    public static void invalidateGlobalCache() {
        getPublicCache().invalidateAll();
    }

    public TypeLookupCache() {
        invalidateAll();
    }

    public synchronized void invalidateAll() {
        this.propertyTypeCache =
            Utils.generateTypeCache(JobPropertyDescriptor.class, false, ["pipelineTriggers", "parameters"])

        this.optionTypeCache =
            Utils.generateTypeCache(DeclarativeOptionDescriptor.class, false, [])

        this.wrapperStepsTypeCache  =
            Utils.generateTypeCache(StepDescriptor.class, false, [],
                { StepDescriptor s ->
                    return s.takesImplicitBlockArgument() &&
                        !(s.getFunctionName() in ModelASTMethodCall.blockedSteps.keySet()) &&
                        !(Launcher.class in s.getRequiredContext()) &&
                        !(FilePath.class in s.getRequiredContext())
                }
            )
    }

    public synchronized Map<String,String> getPropertyTypes() {
        return propertyTypeCache.getUnchecked(PROPS_CACHE_KEY)
    }

    public synchronized Map<String,String> getOptionTypes() {
        return optionTypeCache.getUnchecked(OPTION_CACHE_KEY)
    }

    public synchronized Map<String,String> getWrapperTypes() {
        return wrapperStepsTypeCache.getUnchecked(WRAPPER_STEPS_KEY)
    }
}
