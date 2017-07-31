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
 * Special wrapper for environment to deal with mapped closure problems with property declarations.
 *
 * @author Andrew Bayer
 */
@SuppressFBWarnings(value="SE_NO_SERIALVERSIONID")
public class Environment implements Serializable {

    private final EnvironmentResolver envResolver
    private final EnvironmentResolver credsResolver

    @Whitelisted
    Environment(EnvironmentResolver envResolver, EnvironmentResolver credsResolver) {
        this.envResolver = envResolver
        this.credsResolver = credsResolver
        this.credsResolver.setFallback(this.envResolver)
    }

    EnvironmentResolver getEnvResolver() {
        return envResolver
    }

    EnvironmentResolver getCredsResolver() {
        return credsResolver
    }

    static class EnvironmentResolver implements Serializable {
        private static final long serialVersionUID = 1L

        private CpsScript script
        private Map<String,Closure> closureMap = new TreeMap<>()
        private EnvironmentResolver fallback

        @Whitelisted
        EnvironmentResolver() {
        }

        @Whitelisted
        void setScript(CpsScript script) {
            this.script = script
        }

        @Whitelisted
        CpsScript getScript() {
            return script
        }

        void setFallback(EnvironmentResolver fallback) {
            this.fallback = fallback
        }

        void addClosure(String key, Closure closure) {
            this.closureMap.put(key, closure)
        }

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
         * Called at runtime for fetching a variable defined outside of the resolver.
         */
        @Whitelisted
        Object getScriptPropOrParam(String name) {
            return Utils.getScriptPropOrParam(script, name)
        }

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
