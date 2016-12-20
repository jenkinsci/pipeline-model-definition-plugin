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
import hudson.model.Descriptor;
import org.jenkinsci.plugins.pipeline.modeldefinition.options.DeclarativeOption;
import org.jenkinsci.plugins.pipeline.modeldefinition.options.DeclarativeOptionDescriptor;
import org.jenkinsci.plugins.structs.SymbolLookup;
import org.jenkinsci.plugins.structs.describable.DescribableModel;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;

/**
 * Base descriptor for {@link DeclarativeStageConditional}.
 */
public abstract class DeclarativeStageConditionalDescriptor extends Descriptor<DeclarativeStageConditional> {

    public @Nonnull
    String getName() {
        Set<String> symbolValues = SymbolLookup.getSymbolValue(this);
        if (symbolValues.isEmpty()) {
            throw new IllegalArgumentException("DeclarativeStageConditional descriptor class " + this.getClass().getName()
                    + " does not have a @Symbol and does not override getName().");
        }
        return symbolValues.iterator().next();
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
     * Creates an instance of the corresponding {@link DeclarativeOption} from the given arguments.
     *
     * @param arguments A map of strings/objects to be passed to the constructor.
     * @return An instantiated {@link DeclarativeOption}
     * @throws Exception
     */
    public DeclarativeStageConditional newInstance(Map<String,Object> arguments) throws Exception {
        return new DescribableModel<>(clazz).instantiate(arguments);
    }


    /**
     * For a given descriptor and map of arguments, return an instance using those arguments.
     *
     * @param descriptor The descriptor instance
     * @param arguments A map of arguments
     * @return The instantiated {@link DeclarativeOption} instance.
     * @throws Exception
     */
    public static @Nonnull DeclarativeStageConditional instanceFromDescriptor(@Nonnull DeclarativeStageConditionalDescriptor descriptor,
                                                                    Map<String,Object> arguments) throws Exception {
        return descriptor.newInstance(arguments);
    }
}
