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
 *
 */

package org.jenkinsci.plugins.pipeline.modeldefinition.when;

import hudson.ExtensionList;
import org.jenkinsci.plugins.pipeline.modeldefinition.agent.DeclarativeAgentDescriptor;
import org.jenkinsci.plugins.pipeline.modeldefinition.options.DeclarativeOption;
import org.jenkinsci.plugins.pipeline.modeldefinition.withscript.WithScriptDescriptor;
import org.jenkinsci.plugins.structs.SymbolLookup;
import org.jenkinsci.plugins.structs.describable.DescribableModel;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;

/**
 * Base descriptor for {@link DeclarativeStageConditional}.
 */
public abstract class DeclarativeStageConditionalDescriptor<S extends DeclarativeStageConditional<S>>
        extends WithScriptDescriptor<S> {

    /**
     * How many nested conditions are allowed. -1 for unlimited, 0 for none, anything greater than 0 for
     * requiring exactly that many nested conditions.
     */
    public int getAllowedNestedCount() {
        return 0;
    }

    /**
     * Get all {@link DeclarativeStageConditionalDescriptor}s.
     *
     * @return a list of all {@link DeclarativeStageConditionalDescriptor}s registered.`
     */
    public static ExtensionList<DeclarativeStageConditionalDescriptor> all() {
        return ExtensionList.lookup(DeclarativeStageConditionalDescriptor.class);
    }

    public static List<String> allNames() {
        ExtensionList<DeclarativeStageConditionalDescriptor> all = all();
        List<String> names = new ArrayList<>(all.size());
        for (DeclarativeStageConditionalDescriptor descriptor : all) {
            names.add(descriptor.getName());
        }
        return names;
    }

    /**
     * Get a map of name-to-{@link DescribableModel} of all known/registered descriptors.
     *
     * @return A map of name-to-{@link DescribableModel}s
     */
    public static Map<String,DescribableModel> getDescribableModels() {
        Map<String,DescribableModel> models = new HashMap<>();

        for (DeclarativeStageConditionalDescriptor d : all()) {
            for (String s : SymbolLookup.getSymbolValue(d)) {
                models.put(s, new DescribableModel<>(d.clazz));
            }
        }

        return models;
    }
    /**
     * Get the descriptor for a given name or null if not found.
     *
     * @param name The name for the descriptor to look up
     * @return The corresponding descriptor or null if not found.
     */
    @Nullable
    public static DeclarativeStageConditionalDescriptor byName(@Nonnull String name) {
        return (DeclarativeStageConditionalDescriptor) SymbolLookup.get().findDescriptor(DeclarativeStageConditional.class, name);
    }

    /**
     * For a given descriptor and map of arguments, return an instance using those arguments.
     *
     * @param descriptor The descriptor instance
     * @param arguments A map of arguments
     * @return The instantiated {@link DeclarativeOption} instance.
     * @throws Exception
     */
    @Nonnull
    public static DeclarativeStageConditional<?> instanceFromDescriptor(@Nonnull DeclarativeStageConditionalDescriptor<?> descriptor,
                                                                        Map<String,Object> arguments) throws Exception {
        return descriptor.newInstance(arguments);
    }
}
