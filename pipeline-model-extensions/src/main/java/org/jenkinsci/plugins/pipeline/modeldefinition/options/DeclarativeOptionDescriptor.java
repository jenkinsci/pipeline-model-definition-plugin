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

package org.jenkinsci.plugins.pipeline.modeldefinition.options;

import hudson.ExtensionList;
import hudson.model.Descriptor;
import org.jenkinsci.plugins.structs.SymbolLookup;
import org.jenkinsci.plugins.structs.describable.DescribableModel;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public abstract class DeclarativeOptionDescriptor extends Descriptor<DeclarativeOption> {

    public @Nonnull String getName() {
        Set<String> symbolValues = SymbolLookup.getSymbolValue(this);
        if (symbolValues.isEmpty()) {
            throw new IllegalArgumentException("Declarative Option descriptor class " + this.getClass().getName()
                    + " does not have a @Symbol and does not override getName().");
        }
        return symbolValues.iterator().next();
    }

    /**
     * Get all {@link DeclarativeOptionDescriptor}s.
     *
     * @return a list of all {@link DeclarativeOptionDescriptor}s registered.`
     */
    public static ExtensionList<DeclarativeOptionDescriptor> all() {
        return ExtensionList.lookup(DeclarativeOptionDescriptor.class);
    }

    /**
     * Get a map of name-to-{@link DescribableModel} of all known/registered descriptors.
     *
     * @return A map of name-to-{@link DescribableModel}s
     */
    public static Map<String,DescribableModel> getDescribableModels() {
        Map<String,DescribableModel> models = new HashMap<>();

        for (DeclarativeOptionDescriptor d : all()) {
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
    public static @Nullable DeclarativeOptionDescriptor byName(@Nonnull String name) {
        return (DeclarativeOptionDescriptor) SymbolLookup.get().findDescriptor(DeclarativeOption.class, name);
    }

    /**
     * For a given name and map of arguments, find the corresponding descriptor and return an instance using those arguments.
     *
     * @param name The name of the descriptor
     * @param arguments A map of arguments
     * @return The instantiated {@link DeclarativeOption} instance, or null if the name isn't found.
     * @throws Exception
     */
    public static @Nullable DeclarativeOption instanceFromName(@Nonnull String name,
                                                               Map<String,Object> arguments) throws Exception {
        DeclarativeOptionDescriptor descriptor = byName(name);

        if (descriptor != null) {
            return instanceFromDescriptor(descriptor, arguments);
        }

        return null;
    }

    /**
     * Creates an instance of the corresponding {@link DeclarativeOption} from the given arguments.
     *
     * @param arguments A map of strings/objects to be passed to the constructor.
     * @return An instantiated {@link DeclarativeOption}
     * @throws Exception
     */
    public DeclarativeOption newInstance(Map<String,Object> arguments) throws Exception {
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
    public static @Nonnull DeclarativeOption instanceFromDescriptor(@Nonnull DeclarativeOptionDescriptor descriptor,
                                                                    Map<String,Object> arguments) throws Exception {
        return descriptor.newInstance(arguments);
    }

}
