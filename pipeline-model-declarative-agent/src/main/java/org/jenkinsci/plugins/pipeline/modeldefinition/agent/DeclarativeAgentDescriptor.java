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

import hudson.ExtensionComponent;
import hudson.ExtensionList;
import hudson.model.Descriptor;
import org.jenkinsci.plugins.scriptsecurity.sandbox.whitelists.Whitelisted;
import org.jenkinsci.plugins.structs.SymbolLookup;
import org.jenkinsci.plugins.structs.describable.DescribableModel;
import org.jenkinsci.plugins.structs.describable.UninstantiatedDescribable;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
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
     * The name for this agent type. Should match the {@code Symbol} on the class.
     *
     * @return The name.
     */
    public abstract @Nonnull String getName();

    /**
     * The full package and class name for the {@link DeclarativeAgentScript} class corresponding to this.
     *
     * @return The class name.
     */
    public abstract @Nonnull String getDeclarativeAgentScriptClass();

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

        for (DeclarativeAgentDescriptor d : getOrderedDescriptors()) {
            Set<String> symbolValues = SymbolLookup.getSymbolValue(d);

            if (!symbolValues.isEmpty()) {
                models.put(symbolValues.iterator().next(), new DescribableModel<>(d.clazz));
            }

        }

        return models;
    }

    /**
     * Get the map of the subset of descriptors with single-arguments - i.e., "none" and "any".
     * @return A map of descriptors with just one argument.
     */
    public static Map<String,DescribableModel> singleArgModels() {
        Map<String,DescribableModel> models = new HashMap<>();

        for (Map.Entry<String,DescribableModel> entry : getDescribableModels().entrySet()) {
            if (entry.getValue().getParameters().isEmpty()) {
                models.put(entry.getKey(), entry.getValue());
            }
        }

        return models;
    }

    /**
     * An ordered (by Extension ordinal, in descending order) list of all descriptors.
     * @return A list of descriptors.
     */
    public static List<DeclarativeAgentDescriptor> getOrderedDescriptors() {
        List<DeclarativeAgentDescriptor> orderedDescriptors = new ArrayList<>();

        List<ExtensionComponent<DeclarativeAgentDescriptor>> extensionComponents = new ArrayList<>(all().getComponents());
        Collections.sort(extensionComponents);

        for (ExtensionComponent<DeclarativeAgentDescriptor> extensionComponent: extensionComponents) {
            orderedDescriptors.add(extensionComponent.getInstance());
        }

        return orderedDescriptors;
    }

    /**
     * Get the descriptor for a given name or null if not found.
     *
     * @param name The name for the descriptor to look up
     * @return The corresponding descriptor or null if not found.
     */
    @Whitelisted
    public static @Nullable DeclarativeAgentDescriptor byName(@Nonnull String name) {
        for (DeclarativeAgentDescriptor d : all()) {
            if (d.getName().equals(name)) {
                return d;
            }
        }
        return null;
    }

    /**
     * For a given name and map of arguments, find the corresponding descriptor and return an instance using those arguments.
     *
     * @param name The name of the descriptor
     * @param arguments A map of arguments
     * @return The instantiated {@link DeclarativeAgent} instance, or null if the name isn't found.
     * @throws Exception
     */
    @Whitelisted
    public static @Nullable DeclarativeAgent instanceForName(@Nonnull String name,
                                                             Map<String,Object> arguments) throws Exception {
        DeclarativeAgentDescriptor descriptor = byName(name);

        if (descriptor != null) {
            if (singleArgModels().keySet().contains(name)) {
                return descriptor.newInstance(new HashMap<String, Object>());
            } else {
                return descriptor.newInstance(arguments);
            }
        }

        return null;
    }

}
