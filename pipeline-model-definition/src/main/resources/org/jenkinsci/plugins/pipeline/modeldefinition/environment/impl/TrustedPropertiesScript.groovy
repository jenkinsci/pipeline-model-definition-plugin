/*
 * The MIT License
 *
 * Copyright (c) 2017, CloudBees, Inc.
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
 *
 */

package org.jenkinsci.plugins.pipeline.modeldefinition.environment.impl

import com.cloudbees.groovy.cps.NonCPS
import hudson.Util
import org.jenkinsci.plugins.pipeline.modeldefinition.environment.DeclarativeEnvironmentContributorScript
import org.jenkinsci.plugins.workflow.cps.CpsScript

/**
 * Script side of {@link TrustedProperties}.
 */
class TrustedPropertiesScript extends DeclarativeEnvironmentContributorScript<TrustedProperties> {

    TrustedPropertiesScript(CpsScript s, TrustedProperties d) {
        super(s, d)
    }

    @Override
    List<String> generate(String key) {
        String prefix = Util.fixNull(key)
        if (!prefix.isEmpty() && !prefix.endsWith("_")) {
            prefix = prefix.trim() + "_"
        } else if (prefix == "_") {
            prefix = ""
        }

        if (describable.data == null) {
            return Collections.emptyList()
        } else if (describable.data instanceof Map) {
            return generateEnvStrings(prefix, (Map)describable.data)
        } else {
            String sData = "${describable.data}"
            String content = null
            if (sData.contains("=") && sData.contains("\n")) {
                //It is the contents of a Properties file (have to have at least end with a new line)
                content = sData
            } else {
                //treat it as a path
                if (sData.startsWith("resources://")) { //TODO should be an extension
                    content = script.libraryResource(sData.drop(12))
                } else if (sData.startsWith("scm://")) { //TODO should be an extension
                    content = script.readTrusted(sData.drop(6))
                } else { //Hey, someone might have missed the switch to url?
                    content = script.readTrusted(sData)
                }
            }
            return generateEnvStrings(prefix, content)
        }
    }

    @NonCPS
    List<String> generateEnvStrings(String prefix, String content) {
        Properties p = new Properties()
        p.load(new StringReader(content))
        return generateEnvStrings(prefix, p)
    }

    @NonCPS
    List<String> generateEnvStrings(String prefix, Map p) {
        List<String> env = []

        for (String suffix : p.keySet()) {
            String value = p[suffix]?.toString() ?: ""
            env.add("${prefix}${suffix.trim()}=${value.trim()}")
        }
        return env
    }
}
