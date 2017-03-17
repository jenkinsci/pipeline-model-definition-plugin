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

package org.jenkinsci.plugins.pipeline.modeldefinition;

import hudson.Extension;
import hudson.ExtensionList;
import hudson.init.InitMilestone;
import hudson.init.Initializer;
import hudson.model.Describable;
import hudson.model.Descriptor;
import org.jenkinsci.plugins.structs.SymbolLookup;
import org.jenkinsci.plugins.structs.describable.DescribableModel;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

import java.util.LinkedHashMap;
import java.util.Map;

@Restricted(NoExternalUse.class)
@Extension
public class DescriptorLookupCache {
    private transient Map<String, StepDescriptor> stepMap;
    private transient Map<String, DescribableModel<? extends Step>> modelMap;
    private transient Map<Class<? extends Describable>, Map<String, Descriptor<? extends Describable>>> describableMap;
    private transient Map<Class<? extends Describable>, Map<String, DescribableModel<? extends Describable>>> describableModelMap;

    public static DescriptorLookupCache getPublicCache() {
        return ExtensionList.lookup(DescriptorLookupCache.class).get(0);
    }

    @Initializer(after = InitMilestone.EXTENSIONS_AUGMENTED)  // Prevents potential leakage on reload
    public static void invalidateGlobalCache() {
        getPublicCache().invalidateAll();
    }

    public DescriptorLookupCache() {
        invalidateAll();
    }

    public synchronized void invalidateAll() {
        this.stepMap = new LinkedHashMap<>();
        this.modelMap = new LinkedHashMap<>();
        this.describableMap = new LinkedHashMap<>();
        this.describableModelMap = new LinkedHashMap<>();
    }

    public synchronized DescribableModel<? extends Step> modelForStep(String n) {
        if (!modelMap.containsKey(n)) {
            final StepDescriptor descriptor = lookupStepDescriptor(n);
            Class<? extends Step> c = (descriptor == null ? null : descriptor.clazz);
            modelMap.put(n, c != null ? new DescribableModel<>(c) : null);
        }


        return modelMap.get(n);
    }

    public synchronized DescribableModel<? extends Describable> modelForDescribable(String n) {
        return modelForDescribable(Describable.class, n);
    }

    public synchronized DescribableModel<? extends Describable> modelForDescribable(Class<? extends Describable> base, String n) {
        Map<String,DescribableModel<? extends Describable>> forBase = new LinkedHashMap<>();
        if (describableModelMap.containsKey(base)) {
            forBase.putAll(describableModelMap.get(base));
        }
        if (!forBase.containsKey(n)) {
            final Descriptor<? extends Describable> function = lookupFunction(base, n);
            Class<? extends Describable> c = (function == null ? null : function.clazz);
            forBase.put(n, c != null ? new DescribableModel<>(c) : null);
            describableModelMap.put(base, forBase);
        }

        return forBase.get(n);
    }

    public synchronized StepDescriptor lookupStepDescriptor(String n) {
        if (stepMap.isEmpty()) {
            for (StepDescriptor d : StepDescriptor.all()) {
                stepMap.put(d.getFunctionName(), d);
            }
        }
        return stepMap.get(n);
    }

    public Descriptor<? extends Describable> lookupFunction(String n) {
        return lookupFunction(Describable.class, n);
    }

    public synchronized Descriptor<? extends Describable> lookupFunction(Class<? extends Describable> base, String n) {
        Map<String, Descriptor<? extends Describable>> forBase = new LinkedHashMap<>();

        if (describableMap.containsKey(base)) {
            forBase.putAll(describableMap.get(base));
        }

        if (!forBase.containsKey(n)) {
            try {
                Descriptor<? extends Describable> d = SymbolLookup.get().findDescriptor(base, n);
                forBase.put(n, d);
                describableMap.put(base, forBase);
            } catch (NullPointerException e) {
                forBase.put(n, null);
                describableMap.put(base, forBase);
            }
        }

        return forBase.get(n);
    }

    public synchronized Descriptor<? extends Describable> lookupStepFirstThenFunction(String name) {
        return lookupStepFirstThenFunction(Describable.class, name);
    }

    public synchronized Descriptor<? extends Describable> lookupStepFirstThenFunction(Class<? extends Describable> base, String name) {
        return lookupStepDescriptor(name) != null ? lookupStepDescriptor(name) : lookupFunction(base, name);
    }

    public synchronized Descriptor<? extends Describable> lookupFunctionFirstThenStep(String name) {
        return lookupFunctionFirstThenStep(Describable.class, name);
    }

    public synchronized Descriptor<? extends Describable> lookupFunctionFirstThenStep(Class<? extends Describable> base, String name) {
        return lookupFunction(base, name) != null ? lookupFunction(base, name) : lookupStepDescriptor(name);
    }

    public synchronized DescribableModel<? extends Describable> modelForStepFirstThenFunction(String name) {
        return modelForStepFirstThenFunction(Describable.class, name);
    }

    public synchronized DescribableModel<? extends Describable> modelForStepFirstThenFunction(Class<? extends Describable> base, String name) {
        Descriptor<? extends Describable> desc = lookupStepDescriptor(name);
        DescribableModel<? extends Describable> model = null;

        if (desc != null) {
            model = modelForStep(name);
        } else {
            desc = lookupFunction(base, name);
            if (desc != null) {
                model = modelForDescribable(base, name);
            }
        }

        return model;
    }

    public synchronized DescribableModel<? extends Describable> modelForFunctionFirstThenStep(String name) {
        return modelForFunctionFirstThenStep(Describable.class, name);
    }

    public synchronized DescribableModel<? extends Describable> modelForFunctionFirstThenStep(Class<? extends Describable> base,
                                                                                              String name) {
        Descriptor<? extends Describable> desc = lookupFunction(base, name);
        DescribableModel<? extends Describable> model = null;

        if (desc != null) {
            model = modelForDescribable(base, name);
        } else {
            desc = lookupStepDescriptor(name);
            if (desc != null) {
                model = modelForStep(name);
            }
        }

        return model;
    }

    public boolean stepTakesClosure(Descriptor d) {
        if (d instanceof StepDescriptor) {
            return ((StepDescriptor)d).takesImplicitBlockArgument();
        } else {
            return false;
        }
    }
}
