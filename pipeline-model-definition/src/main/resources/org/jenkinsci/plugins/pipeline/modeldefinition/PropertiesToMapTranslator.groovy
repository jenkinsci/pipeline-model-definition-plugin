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
package org.jenkinsci.plugins.pipeline.modeldefinition

import org.jenkinsci.plugins.pipeline.modeldefinition.environment.DeclarativeEnvironmentContributor
import org.jenkinsci.plugins.pipeline.modeldefinition.model.CredentialsBindingHandler
import org.jenkinsci.plugins.pipeline.modeldefinition.model.MethodMissingWrapper
import org.jenkinsci.plugins.pipeline.modeldefinition.model.NestedModel
import org.jenkinsci.plugins.pipeline.modeldefinition.options.DeclarativeOption
import org.jenkinsci.plugins.pipeline.modeldefinition.steps.CredentialWrapper
import org.jenkinsci.plugins.structs.describable.UninstantiatedDescribable
import org.jenkinsci.plugins.workflow.cps.CpsScript
import org.jenkinsci.plugins.workflow.support.steps.build.RunWrapper

import static org.jenkinsci.plugins.pipeline.modeldefinition.Utils.isOfType

/**
 * Translates a closure containing one or more "foo = 'bar'" statements into a map.
 * @author Andrew Bayer
 */
public class PropertiesToMapTranslator implements MethodMissingWrapper, Serializable {
    Map<String,Object> actualMap = [:]
    CpsScript script
    private final boolean resolveEnvironmentContributors

    PropertiesToMapTranslator(CpsScript script, boolean resolveEnvironmentContributors = false) {
        this.resolveEnvironmentContributors = resolveEnvironmentContributors
        this.script = script
    }

    def methodMissing(String s, args) {
        if (resolveEnvironmentContributors) {
            def describable = Utils.getDescribable(s, DeclarativeEnvironmentContributor.class, args)
            if (describable != null) {
                return describable.instantiate()
            }
        }

        def argValue
        if (args.length > 1) {
            argValue = args
        } else if (args.length == 1) {
            argValue = args[0]
        }

        return script."${s}"(argValue)
    }


    void propertyMissing(String s, args) {
        actualMap.put(s, args)
    }

    public Map<String, Object> getMap() {
        def mapCopy = [:]
        mapCopy.putAll(actualMap)
        return mapCopy
    }

    NestedModel toNestedModel(Class<NestedModel> c) {
        NestedModel m = c.newInstance()
        m.modelFromMap(actualMap)
        return m
    }
}
