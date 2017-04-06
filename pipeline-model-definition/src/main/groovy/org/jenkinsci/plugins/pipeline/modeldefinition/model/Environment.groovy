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
import hudson.EnvVars
import hudson.model.Run
import hudson.model.TaskListener
import org.apache.commons.lang.StringUtils
import org.jenkinsci.plugins.credentialsbinding.MultiBinding
import org.jenkinsci.plugins.pipeline.modeldefinition.Utils
import org.jenkinsci.plugins.scriptsecurity.sandbox.groovy.SecureGroovyScript
import org.jenkinsci.plugins.scriptsecurity.scripts.ApprovalContext
import org.jenkinsci.plugins.workflow.cps.CpsScript
import org.jenkinsci.plugins.workflow.cps.CpsThread
import org.jenkinsci.plugins.workflow.cps.EnvActionImpl

/**
 * Special wrapper for environment to deal with mapped closure problems with property declarations.
 *
 * @author Andrew Bayer
 */
@SuppressFBWarnings(value="SE_NO_SERIALVERSIONID")
public class Environment implements Serializable {
    Map<String,EnvValue> valueMap = new TreeMap<>()
    Map<String,EnvValue> credsMap = new TreeMap<>()

    public void setValueMap(Map<String,EnvValue> inMap) {
        this.valueMap.putAll(inMap)
    }

    public void setCredsMap(Map<String,EnvValue> inMap) {
        this.credsMap.putAll(inMap)
    }

    public Map<String,EnvValue> getMap() {
        return valueMap
    }

    public Map<String,String> getCredsMap(CpsScript script) {
        Map<String,String> resolvedMap = new TreeMap<>()
        EnvVars contextEnv = new EnvVars((Map<String,String>)script.getProperty("params"))
        contextEnv.overrideExpandingAll(((EnvActionImpl)script.getProperty("env")).getEnvironment())

        credsMap.each { k, v ->
            resolvedMap.put(k, Utils.trimQuotes(contextEnv.expand(v.value.toString())))
        }

        return resolvedMap
    }

    public Map<String,String> resolveEnvVars(CpsScript script, boolean withContext, Environment parent = null) {
        Map<String, String> overrides = getMap().collectEntries { k, v ->
            String val = v.value.toString()
            if (!v.isLiteral) {
                // Switch out env.FOO for FOO, since the env global variable isn't available in the shell we're processing.
                val = replaceEnvDotInCurlies(val)
            }
            // Remove quotes from any GStrings that may have them.
            if (v.isLiteral || (v.value.toString().startsWith('$'))) {
                [(k): val]
            } else {
                [(k): Utils.trimQuotes(val)]
            }
        }

        Map<String, String> alreadySet = new TreeMap<>()
        // Add any already-set environment variables for the run to the map of already-set variables.
        alreadySet.putAll(CpsThread.current()?.getExecution()?.getShell()?.getContext()?.variables?.findAll { k, v ->
            k instanceof String && v instanceof String
        })

        // If we're being called directly and not to pull in root-level environment variables into a stage, add anything
        // in the current env global variable.
        if (withContext) {
            alreadySet.putAll(((EnvActionImpl) script.getProperty("env")).getEnvironment())
        }
        // Add parameters.
        alreadySet.putAll((Map<String, String>) script.getProperty("params"))

        // If we're being called for a stage, add any root level environment variables after resolving them.
        if (parent != null) {
            alreadySet.putAll(parent.resolveEnvVars(script, false))
        }

        List<String> unsetKeys = []
        // Initially define the list of unset environment variable keys to the full list of keys for this environment
        // directive.
        unsetKeys.addAll(overrides.keySet())

        Binding binding = new Binding(alreadySet)

        // Also add the params global variable to deal with any references to params.FOO.
        binding.setProperty("params", (Map<String, String>) script.getProperty("params"))

        // Do a first round of resolution before we proceed onward to credentials - if no credentials are defined, we
        // don't need to do anything more.
        Map<String, String> preCreds = roundRobin(binding, alreadySet, overrides, unsetKeys)

        if (!credsMap.isEmpty()) {
            // Get the current build for use in credentials processing.
            Run<?, ?> r = script.$build()

            // Keep a list of resolved credentials environment variables.
            List<String> credKeys = []

            // Resolve credentials that don't require a workspace so we can insert them into the environment variables as needed.
            // Note that we'll discard these values for now and recreate them later in the actual withCredentials block.
            credsMap.each { k, v ->
                try {
                    // Resolve the string passed to credentials(...) in case it contains an environment variable defined
                    // in the environment directive.

                    String resolvedCredId = resolveAsScript(binding, v.value.toString())
                    CredentialsBindingHandler handler = CredentialsBindingHandler.forId(resolvedCredId, r)

                    if (handler != null) {
                        // Iterate over the bindings corresponding to the credential the ID represents.
                        handler.toBindings(k, resolvedCredId).each { b ->
                            // We don't actually bother resolving anything that needs a workspace at this point. So a file
                            // credential, for example, will not be resolved in time for insertion into the environment.
                            if (!b.descriptor.requiresWorkspace()) {
                                // Get the actual environment variables that would be produced by the binding.
                                MultiBinding.MultiEnvironment bindingEnv = b.bind(r, null, null, TaskListener.NULL)
                                // Add those environment variables to the shell we're using for resolving the environment
                                // entries, and record that we've processed that environment variable.
                                bindingEnv.values.each { cKey, cVal ->
                                    credKeys.add(cKey)
                                    binding.setVariable(cKey, cVal)
                                }
                            }
                        }
                    }
                } catch (_) {
                    // Something went wrong? Don't care! We'll be processing this for real later anyway.
                }
            }

            // Only bother with any of this if there are resolved credentials.
            if (!credKeys.isEmpty()) {
                // Find all environment variable keys that have a value containing one or more of the now-resolved
                // credentials environment variables. Ideally, we'd do this before resolving the credentials themselves,
                // but we can't know what those variables may be until we've actually resolved them.
                List<String> keysWithCreds = overrides.findAll { k, v -> containsVariable(v, credKeys) }.collect {
                    it.key
                }

                // Only actually evaluate anything if there are env keys with unresolved cred references.
                if (!keysWithCreds.isEmpty()) {
                    // Round-robin resolve the environment one more time, with the credentials included.
                    Map<String, String> postCreds = roundRobin(binding, preCreds, overrides, keysWithCreds, credKeys)

                    return postCreds
                }
            }
        }
        return alreadySet
    }

    /**
     * Loop over environment entries and try to resolve them. To prevent infinite looping for unresolvable variables,
     * stop looping once we've hit as many unresolvable entries in a row as the total size of possible variables.
     *
     * @param binding The {@link Binding} containing already-set variables.
     * @param preSet A map of environment variables we know have already been set.
     * @param toSet A map of environment variables that are defined in the environment directive we're processing.
     * @param unsetKeys A list of environment variable keys we know aren't yet set.
     * @param otherKeysToAllow A list of additional environment variable keys to look for besides the ones defined
     *          in the environment directive, such as credentials.
     * @return A map of environment variables after we've resolved as many as we can.
     */
    private Map<String,String> roundRobin(Binding binding, Map<String,String> preSet, Map<String,String> toSet,
                                          List<String> unsetKeys, List<String> otherKeysToAllow = []) {
        Map<String,String> alreadySet = new TreeMap<>()
        alreadySet.putAll(preSet)

        int unsuccessfulCount = 0
        // Stop once all keys have been resolved or we've looped over everything enough that we know no further
        // resolution is possible.
        while (!unsetKeys.isEmpty() && unsuccessfulCount <= toSet.size()) {
            String nextKey = unsetKeys.first()

            unsetKeys.remove(nextKey)
            try {
                String resolved = toSet.get(nextKey)
                // Only do the eval here if the string actually contains another environment variable we know about.
                // This is to defer evaluation of things like ${WORKSPACE} or currentBuild.getNumber() until later.
                if (containsVariable(resolved, toSet.keySet()) ||
                    containsVariable(resolved, alreadySet.keySet()) ||
                    containsVariable(resolved, otherKeysToAllow)) {
                    resolved = resolveAsScript(binding, resolved)
                }

                alreadySet.put(nextKey, resolved)
                binding.setVariable(nextKey, resolved)
                unsuccessfulCount = 0
            } catch (_) {
                unsuccessfulCount++
                unsetKeys.add(nextKey)
            }
        }

        return alreadySet
    }

    private boolean containsVariable(String var, Collection<String> keys) {
        def group = (var =~ /(\$\{.*?\})/)
        return group.any { m ->
            keys.any { k ->
                String curlies = m[1]
                return curlies.contains(k)
            }
        }
    }

    private String replaceEnvDotInCurlies(String inString) {
        def group = (inString =~ /(\$\{.*?\})/)
        def outString = inString

        group.each { m ->
            String toReplace = m[1]
            String replaced = toReplace.replaceAll(/env\.([a-zA-Z_][a-zA-Z0-9_]*?)/,
                { full, part -> "${part}" })

            outString = StringUtils.replace(outString, toReplace, replaced)
        }

        return outString
    }

    public static class EnvValue implements Serializable {
        public boolean isLiteral
        public Object value
    }

    private String resolveAsScript(Binding binding, String script) {
        String toRun = Utils.prepareForEvalToString(script)
        SecureGroovyScript toExec = new SecureGroovyScript(toRun, true)
            .configuring(ApprovalContext.create().withCurrentUser())
        return Utils.unescapeFromEval((String)toExec.evaluate(this.class.getClassLoader(), binding))
    }
}
