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
import org.apache.commons.lang.StringUtils
import org.jenkinsci.plugins.pipeline.modeldefinition.Utils
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
            resolvedMap.put(k, Utils.trimQuotes(contextEnv.expand(v.value)))
        }

        return resolvedMap
    }

    public Map<String,String> resolveEnvVars(CpsScript script, boolean withContext, Environment parent = null) {
        Map<String,String> overrides = getMap().collectEntries { k, v ->
            String val = v.value.toString()
            if (!v.isLiteral) {
                val = replaceEnvDotInCurlies(val)
            }
            if (v.isLiteral || (v.value.toString().startsWith('$'))) {
                [(k): val]
            } else {
                [(k): Utils.trimQuotes(val)]
            }
        }

        Map<String,String> alreadySet = new TreeMap<>()
        alreadySet.putAll(CpsThread.current()?.getExecution()?.getShell()?.getContext()?.variables?.findAll { k, v ->
            k instanceof String && v instanceof String
        })
        if (withContext) {
            alreadySet.putAll(((EnvActionImpl)script.getProperty("env")).getEnvironment())
        }
        alreadySet.putAll((Map<String,String>)script.getProperty("params"))
        if (parent != null) {
            alreadySet.putAll(parent.resolveEnvVars(script, false))
        }

        List<String> unsetKeys = []
        unsetKeys.addAll(overrides.keySet())

        GroovyShell shell = new GroovyShell(new Binding(alreadySet))
        shell.setProperty("params", (Map<String,String>)script.getProperty("params"))

        int unsuccessfulCount = 0
        while (!unsetKeys.isEmpty() && unsuccessfulCount <= overrides.size()) {
            String nextKey = unsetKeys.first()

            unsetKeys.remove(nextKey)
            try {
                String resolved = overrides.get(nextKey)
                // Only do the eval here if the string actually contains another environment variable we know about.
                // This is to defer evaluation of things like ${WORKSPACE} or currentBuild.getNumber() until later.
                if (containsVariable(resolved, overrides.keySet()) ||
                    containsVariable(resolved, alreadySet.keySet())) {
                    resolved = shell.evaluate(Utils.prepareForEvalToString(resolved))
                }

                alreadySet.put(nextKey, resolved)
                shell.setVariable(nextKey, resolved)
                unsuccessfulCount = 0
            } catch (_) {
                unsuccessfulCount++
                unsetKeys.add(nextKey)
            }
        }

        return alreadySet
    }

    private boolean containsVariable(String var, Set<String> keys) {
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
}
