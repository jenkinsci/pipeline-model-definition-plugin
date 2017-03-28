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
import org.jenkinsci.plugins.pipeline.modeldefinition.Utils
import org.jenkinsci.plugins.pipeline.modeldefinition.steps.CredentialWrapper
import org.jenkinsci.plugins.workflow.cps.CpsScript
import org.jenkinsci.plugins.workflow.cps.EnvActionImpl

/**
 * Special wrapper for environment to deal with mapped closure problems with property declarations.
 *
 * @author Andrew Bayer
 */
@SuppressFBWarnings(value="SE_NO_SERIALVERSIONID")
public class Environment implements Serializable {
    Map<String,EnvValue> valueMap = new TreeMap<>()
    // TODO: Actually do stuff with creds again
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

    public EnvVars resolveEnvVars(CpsScript script, boolean withContext, Environment parent = null) {
        EnvVars newEnv

        EnvVars contextEnv = new EnvVars((Map<String,String>)script.getProperty("params"))
        if (withContext) {
            contextEnv.overrideExpandingAll(((EnvActionImpl)script.getProperty("env")).getEnvironment())
        }

        newEnv = new EnvVars(contextEnv)

        if (parent != null) {
            newEnv.overrideExpandingAll(parent.resolveEnvVars(script, false))
        }

        Map<String,String> overrides = getMap().collectEntries { k, v ->
            if (v.isLiteral || (v.value.toString().startsWith('$'))) {
                [(k): v.value.toString()]
            } else {
                [(k): Utils.trimQuotes(v.value.toString())]
            }
        }

        newEnv.overrideExpandingAll(overrides)

        return newEnv
    }

    public static class EnvValue implements Serializable {
        public boolean isLiteral
        public Object value
    }
}
