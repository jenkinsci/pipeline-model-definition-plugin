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
package org.jenkinsci.plugins.pipeline.modeldefinition.model

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings
import org.jenkinsci.plugins.pipeline.modeldefinition.Utils
import org.jenkinsci.plugins.scriptsecurity.sandbox.whitelists.Whitelisted
import org.jenkinsci.plugins.workflow.cps.CpsScript

/**
 * Special wrapper for environment to deal with cross-references, etc.
 *
 * @author Andrew Bayer
 */
@SuppressFBWarnings(value="SE_NO_SERIALVERSIONID")
class Environment implements Serializable {

    private final EnvironmentResolver envResolver
    private final EnvironmentResolver credsResolver

    /**
     * @param envResolver {@link EnvironmentResolver} for environment variables
     * @param credsResolver {@link EnvironmentResolver} for credentials
     */
    @Whitelisted
    Environment(EnvironmentResolver envResolver, EnvironmentResolver credsResolver) {
        this.envResolver = envResolver
        this.credsResolver = credsResolver
        this.credsResolver.setFallback(this.envResolver)
    }

    /**
     * Gets the {@link EnvironmentResolver} for the environment variables.
     */
    EnvironmentResolver getEnvResolver() {
        return envResolver
    }

    /**
     * Gets the {@link EnvironmentResolver} for the credentials.
     */
    EnvironmentResolver getCredsResolver() {
        return credsResolver
    }

    /**
     * A special class used for containing a map of environment variable keys and closures for each of them that return
     * their actual value. Those closures may call the closures for other keys to resolve other variables.
     */
    static class EnvironmentResolver implements Serializable {
        private static final long serialVersionUID = 1L

        private CpsScript script
        private Map<String,Closure> closureMap = new HashMap<>()
        private EnvironmentResolver fallback

        @Whitelisted
        EnvironmentResolver() {
        }

        /**
         * Used in in the initialization closure to populate the {@link CpsScript} used for fetching pre-existing property
         * and parameter values.
         */
        @Whitelisted
        void setScript(CpsScript script) {
            this.script = script
        }

        @Whitelisted
        CpsScript getScript() {
            return script
        }

        /**
         * Optionally, you can set a fallback {@link EnvironmentResolver}, so that, for example, the credentials resolver
         * can fall back on the environment variables resolver to get the closure to resolve for an environment variable
         * key.
         */
        void setFallback(EnvironmentResolver fallback) {
            this.fallback = fallback
        }

        /**
         * Used in the initialization closure to actually add the value closure for a key. This is needed due to issues
         * with passing CPS-transformed closures to constructors, and may be replaced in the future.
         */
        void addClosure(String key, Closure closure) {
            this.closureMap.put(key, closure)
        }

        /**
         * Actually fetch the value closure to call for a key. First checks our own map, then falls back to the fallback
         * {@link EnvironmentResolver} if it exists, and lastly returns null.
         */
        @Whitelisted
        Closure getClosure(String key) {
            if (closureMap.containsKey(key)) {
                return closureMap.get(key)
            } else if (fallback != null) {
                return fallback.getClosure(key)
            } else {
                return null
            }
        }

        /**
         * Called at runtime from inside value closures for fetching a variable defined outside of the resolver.
         */
        @Whitelisted
        Object getScriptPropOrParam(String name) {
            return Utils.getScriptPropOrParam(script, name)
        }

        /**
         * Get the map of keys to value closures.
         */
        Map<String,Closure> getClosureMap() {
            return closureMap
        }

        /**
         * Called in AST transformation to instantiate the resolver.
         */
        @Whitelisted
        static EnvironmentResolver instanceFromMap(Map<String, Closure> closureMap) {
            EnvironmentResolver resolver = new EnvironmentResolver()
            closureMap.each { k, v ->
                v.delegate = resolver
                resolver.addClosure(k, v)
            }

            return resolver
        }
    }

}
