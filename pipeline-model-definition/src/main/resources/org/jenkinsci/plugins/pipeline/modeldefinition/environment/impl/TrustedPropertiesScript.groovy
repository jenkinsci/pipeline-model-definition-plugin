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
        }
        String content = script.readTrusted(describable.path)
        return generateEnvStrings(prefix, content)
    }

    @NonCPS
    List<String> generateEnvStrings(String prefix, String content) {
        Properties p = new Properties()
        p.load(new StringReader(content))
        List<String> env = []

        for (String suffix : p.keySet()) {
            env.add("${prefix}${suffix.trim()}=${p.getProperty(suffix, "").trim()}")
        }
        return env
    }
}
