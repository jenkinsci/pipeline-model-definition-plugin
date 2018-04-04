/*
 * The MIT License
 *
 * Copyright (c) 2018, CloudBees, Inc.
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

import hudson.ExtensionList;
import hudson.model.Descriptor;
import org.codehaus.groovy.ast.stmt.Statement;
import org.jenkinsci.plugins.pipeline.modeldefinition.shaded.com.fasterxml.jackson.databind.JsonNode;
import org.jenkinsci.plugins.pipeline.modeldefinition.shaded.com.github.fge.jsonschema.tree.JsonTree;
import org.jenkinsci.plugins.pipeline.modeldefinition.shaded.com.github.fge.jsonschema.util.JsonLoader;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public abstract class DeclarativeDirectiveDescriptor<D extends DeclarativeDirective<D>> extends Descriptor<D> {
    private static Logger LOGGER = Logger.getLogger(DeclarativeDirectiveDescriptor.class.getName());

    @Nonnull
    public abstract String getName();

    public boolean isAllowedInStage() {
        return false;
    }

    public boolean isAllowedAtTopLevel() {
        return true;
    }

    // Can construct it by hand, read it from URI, parse it from string. Utility methods provided. Throw an illegal argument exception
    // or similar if null.
    @Nonnull
    public abstract JsonNode getSchema();

    @CheckForNull
    protected final JsonNode jsonNodeFromURI(@Nonnull URI uri) {
        try {
            return JsonLoader.fromURL(uri.toURL());
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Could not read JSON schema from URI " + uri, e);
            return null;
        }
    }

    @CheckForNull
    protected final JsonNode jsonNodeFromResource(@Nonnull String resource) {
        try {
            return JsonLoader.fromResource(resource);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Could not read JSON schema from resource " + resource, e);
            return null;
        }
    }

    @CheckForNull
    protected final JsonNode jsonNodeFromString(@Nonnull String s) {
        try {
            return JsonLoader.fromString(s);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Could not read JSON schema from string", e);
            return null;
        }
    }

    public abstract DeclarativeDirective parseDirectiveFromGroovy(Statement st);

    public abstract DeclarativeDirective parseDirectiveFromJSON(JsonTree json);

    /**
     * Get all {@link DeclarativeDirectiveDescriptor}s.
     *
     * @return a list of all {@link DeclarativeDirectiveDescriptor}s registered.
     */
    public static ExtensionList<DeclarativeDirectiveDescriptor> all() {
        return ExtensionList.lookup(DeclarativeDirectiveDescriptor.class);
    }

    public static Map<String,DeclarativeDirectiveDescriptor> allByName() {
        Map<String,DeclarativeDirectiveDescriptor> m = new HashMap<>();

        for (DeclarativeDirectiveDescriptor d : all()) {
            m.put(d.getName(), d);
        }

        return m;
    }
}
