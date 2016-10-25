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
package org.jenkinsci.plugins.pipeline.modeldefinition.agent;

import hudson.ExtensionList;
import hudson.model.Descriptor;
import org.jenkinsci.plugins.structs.SymbolLookup;
import org.jenkinsci.plugins.structs.describable.DescribableModel;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Descriptor for {@link DeclarativeAgent}.
 *
 * @author Andrew Bayer
 */
public abstract class DeclarativeAgentDescriptor extends Descriptor<DeclarativeAgent> {

    /**
     * The name for this agent type. Defaults to the first string in the {@code Symbol} on the class.
     *
     * @return The name.
     */
    public @Nonnull String getName() {
        Set<String> symbolValues = SymbolLookup.getSymbolValue(this);
        if (symbolValues.isEmpty()) {
            throw new IllegalStateException("Cannot get agent type name for class " + clazz.getName());
        }
        return symbolValues.iterator().next();
    }

    /**
     * The full package and class name for the {@link DeclarativeAgentScript} class corresponding to this. Defaults to
     * the {@link DeclarativeAgent} class name with "Script" appended to the end.
     *
     * @return The class name.
     */
    public @Nonnull String getDeclarativeAgentScriptClass() {
        return clazz.getName() + "Script";
    }

    /**
     * Creates an instance of the corresponding {@link DeclarativeAgent} from the given arguments.
     *
     * @param arguments A map of strings/objects to be passed to the constructor.
     * @return An instantiated {@link DeclarativeAgent}
     * @throws Exception
     */
    public DeclarativeAgent newInstance(Map<String,Object> arguments) throws Exception {
        return new DescribableModel<>(clazz).instantiate(arguments);
    }

    /**
     * Creates an instance of the corresponding {@link DeclarativeAgent} with no arguments.
     *
     * @return An instantiated {@link DeclarativeAgent}
     * @throws Exception
     */
    public DeclarativeAgent newInstance() throws Exception {
        return clazz.newInstance();
    }

    /**
     * Get all {@link DeclarativeAgentDescriptor}s.
     *
     * @return a list of all {@link DeclarativeAgentDescriptor}s registered.`
     */
    public static ExtensionList<DeclarativeAgentDescriptor> all() {
        return ExtensionList.lookup(DeclarativeAgentDescriptor.class);
    }

    /**
     * Get a map of name-to-{@link DescribableModel} of all known/registered descriptors.
     *
     * @return A map of name-to-{@link DescribableModel}s
     */
    public static Map<String,DescribableModel> getDescribableModels() {
        Map<String,DescribableModel> models = new HashMap<>();

        for (DeclarativeAgentDescriptor d : all()) {
            for (String s : SymbolLookup.getSymbolValue(d)) {
                models.put(s, new DescribableModel<>(d.clazz));
            }
        }

        return models;
    }

    /**
     * Get the map of the subset of descriptors with no additional arguments - i.e., "none" and "any".
     * @return A map of descriptors with no arguments.
     */
    public static Map<String,DescribableModel> zeroArgModels() {
        Map<String,DescribableModel> models = new HashMap<>();

        for (Map.Entry<String,DescribableModel> entry : getDescribableModels().entrySet()) {
            if (entry.getValue().getParameters().isEmpty()) {
                models.put(entry.getKey(), entry.getValue());
            }
        }

        return models;
    }

    /**
     * An ordered list of all descriptor names.
     *
     * @return A list of names
     */
    public static List<String> getOrderedNames() {
        List<String> orderedNames = new ArrayList<>();

        for (DeclarativeAgentDescriptor d : all()) {
            orderedNames.addAll(SymbolLookup.getSymbolValue(d));
        }

        return orderedNames;
    }

    /**
     * Get the descriptor for a given name or null if not found.
     *
     * @param name The name for the descriptor to look up
     * @return The corresponding descriptor or null if not found.
     */
    public static @Nullable DeclarativeAgentDescriptor byName(@Nonnull String name) {
        return (DeclarativeAgentDescriptor) SymbolLookup.get().findDescriptor(DeclarativeAgent.class, name);
    }

    /**
     * For a given name and map of arguments, find the corresponding descriptor and return an instance using those arguments.
     *
     * @param name The name of the descriptor
     * @param arguments A map of arguments
     * @return The instantiated {@link DeclarativeAgent} instance, or null if the name isn't found.
     * @throws Exception
     */
    public static @Nullable DeclarativeAgent instanceForName(@Nonnull String name,
                                                             Map<String,Object> arguments) throws Exception {
        DeclarativeAgentDescriptor descriptor = byName(name);

        if (descriptor != null) {
            if (zeroArgModels().keySet().contains(name)) {
                return descriptor.newInstance();
            } else {
                return descriptor.newInstance(arguments);
            }
        }

        return null;
    }

}
