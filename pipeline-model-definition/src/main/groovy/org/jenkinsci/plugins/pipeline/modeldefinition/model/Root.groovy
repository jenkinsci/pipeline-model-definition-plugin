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
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString

import org.jenkinsci.plugins.pipeline.modeldefinition.steps.CredentialWrapper
import org.jenkinsci.plugins.workflow.cps.CpsScript
import org.jenkinsci.plugins.workflow.support.steps.build.RunWrapper

/**
 * Root-level configuration object for the entire model.
 *
 * @author Andrew Bayer
 */
@ToString
@EqualsAndHashCode
@SuppressFBWarnings(value="SE_NO_SERIALVERSIONID")
public class Root implements NestedModel, Serializable {
    Agent agent

    Stages stages

    PostBuild post

    Environment environment

    Tools tools

    Options options

    Triggers triggers

    Parameters parameters

    Libraries libraries

    Root stages(Stages s) {
        this.stages = s
        return this
    }

    Root post(PostBuild p) {
        this.post = p
        return this
    }

    Root agent(Agent a) {
        this.agent = a.convertZeroArgs()
        return this
    }

    Root environment(Environment m) {
        this.environment = m
        return this
    }

    Root tools(Tools t) {
        this.tools = t
        return this
    }

    Root options(Options p) {
        this.options = p
        return this
    }

    Root triggers(Triggers t) {
        this.triggers = t
        return this
    }

    Root parameters(Parameters p) {
        this.parameters = p
        return this
    }

    Root libraries(Libraries l) {
        this.libraries = l
        return this
    }

    /**
     * Helper method for translating the key/value pairs in the {@link Environment} into a list of "key=value" strings
     * suitable for use with the withEnv step.
     *
     * @return a list of "key=value" strings.
     */
    List<String> getEnvVars(CpsScript script) {
        if (environment != null) {
            return environment.resolveEnvVars(script, true).findAll {
                it.key in environment.getMap().keySet()
            }.collect { k, v ->
                "${k}=${v}"
            }
        } else {
            return []
        }
    }

    Map<String, CredentialWrapper> getEnvCredentials() {
        Map<String, CredentialWrapper> m = [:]
        environment?.credsMap?.each {k, v ->
            if (v instanceof  CredentialWrapper) {
                m["${k}"] = v;
            }
        }
        return m
    }

    @Override
    public void modelFromMap(Map<String,Object> m) {
        m.each { k, v ->
            this."${k}"(v)
        }
    }

    /**
     * Returns true if at least one build condition for the given responder is satisfied currently.
     *
     * @param r an {@link AbstractBuildConditionResponder}, such as {@link PostStage} or {@link PostBuild}.
     * @param runWrapperObj The {@link RunWrapper} for the build.
     * @return True if at least one condition is satisfied, false otherwise.
     */
    /*package*/ boolean hasSatisfiedConditions(AbstractBuildConditionResponder r, Object runWrapperObj) {
        if (r != null) {
            return r.satisfiedConditions(runWrapperObj)
        } else {
            return false
        }

    }
}
