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
    public static final String DOLLAR_PLACEHOLDER = "___DOLLAR_SIGN___"

    Map<String,EnvValue> valueMap = new TreeMap<>()
    Map<String,EnvValue> credsMap = new TreeMap<>()

    // Caching for resolved environment variables and credentials so we don't process them twice if we don't need to.
    private transient Map<String,String> interimResolved = new TreeMap<>()
    private transient Map<String,String> interimResolvedCreds = new TreeMap<>()

    public void setValueMap(Map<String,EnvValue> inMap) {
        this.valueMap.putAll(inMap)
    }

    public void setCredsMap(Map<String,EnvValue> inMap) {
        this.credsMap.putAll(inMap)
    }

    public Map<String,EnvValue> getMap() {
        return valueMap
    }

    public Map<String,String> getCredsMap(CpsScript script, Environment parent = null) {
        Map<String,String> resolvedMap = new TreeMap<>()
        if (!credsMap.isEmpty()) {
            if (interimResolvedCreds.isEmpty()) {
                EnvVars contextEnv = new EnvVars()
                resolveEnvVars(script, true, parent).each { k, v ->
                    if (v instanceof String) {
                        contextEnv.put(k, v)
                    }
                }

                credsMap.each { k, v ->
                    resolvedMap.put(k, Utils.trimQuotes(contextEnv.expand(v.value.toString())))
                }
                interimResolvedCreds.putAll(resolvedMap)
            }
        } else {
            interimResolvedCreds.putAll(resolvedMap)
        }

        return resolvedMap
    }

    public Map<String,String> resolveEnvVars(CpsScript script, boolean withContext, Environment parent = null) {
        Map<String, String> alreadySet = new TreeMap<>()
        if (getMap().isEmpty()) {
            return alreadySet
        } else if (!interimResolved.isEmpty()) {
            alreadySet.putAll(interimResolved)
            return alreadySet
        } else {
            Map<String, String> overrides = getMap().collectEntries { k, v ->
                String val = v.value.toString()
                if (v.isLiteral) {
                    // Escape dollar-signs.
                    val = StringUtils.replace(val, '$', DOLLAR_PLACEHOLDER)
                } else {
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
            ((Map<String, Object>) script.getProperty("params")).each { k, v ->
                alreadySet.put(k, v?.toString() ?: "")
            }

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
            binding.setProperty("params", (Map<String, Object>) script.getProperty("params"))

            // Do resolution
            Map<String, String> resolved = roundRobin(binding, alreadySet, overrides, unsetKeys)

            // Stash aside the resolved vars for use elsewhere, but only if we're not a nested call.
            if (withContext) {
                interimResolved.putAll(resolved)
            }
            return resolved
        }
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
                // Make sure we escape backslashes - they'll only show up at this point if they were '\\' in the original.
                resolved = StringUtils.replace(resolved, '\\', '\\\\')
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
        def found = group.any { m ->
            keys.any { k ->
                String curlies = m[1]
                return curlies.matches(/.*\W${k}\W.*/)
            }
        }
        if (!found) {
            def explicit = (var =~ /(\$.*?)\W/)
            found = explicit.any { m ->
                keys.any { k ->
                    String single = m[1]
                    return single == '$' + k
                }
            }
        }
        return found
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
