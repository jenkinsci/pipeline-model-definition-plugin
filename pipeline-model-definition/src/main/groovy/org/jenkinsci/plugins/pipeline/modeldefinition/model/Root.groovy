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
import org.jenkinsci.plugins.scriptsecurity.sandbox.whitelists.Whitelisted
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
class Root implements Serializable {
    Agent agent

    Stages stages

    PostBuild post

    Environment environment

    Tools tools

    Options options

    Triggers triggers

    Parameters parameters

    Libraries libraries

    final String astUUID

    @Whitelisted
    Root(Agent agent, Stages stages, PostBuild post, Environment environment, Tools tools, Options options,
         Triggers triggers, Parameters parameters, Libraries libraries, String astUUID) {
        this.agent = agent
        this.stages = stages
        this.post = post
        this.environment = environment
        this.tools = tools
        this.options = options
        this.triggers = triggers
        this.parameters = parameters
        this.libraries = libraries
        this.astUUID = astUUID
    }

    /**
     * Helper method for translating the key/value pairs in the {@link Environment} into a list of "key=value" strings
     * suitable for use with the withEnv step.
     *
     * @return a map of keys to closures.
     */
    Map<String,Closure> getEnvVars(CpsScript script) {
        if (environment != null) {
            environment.envResolver.setScript(script)
            return environment.envResolver.closureMap
        } else {
            return [:]
        }
    }

    /**
     * Returns true if at least one build condition for the given responder is satisfied currently.
     *
     * @param r an {@link AbstractBuildConditionResponder}, such as {@link PostStage} or {@link PostBuild}.
     * @param runWrapperObj The {@link RunWrapper} for the build.
     * @param context The context where this is being called, which could be either a {@link Root} or a {@link Stage}, but also could be null.
     * @param error The first error seen in the relevant context. Can be null.
     * @return True if at least one condition is satisfied, false otherwise.
     */
    /*package*/ boolean hasSatisfiedConditions(AbstractBuildConditionResponder r, Object runWrapperObj, Object context = null,
                                               Throwable error = null) {
        if (r != null) {
            return r.satisfiedConditions(runWrapperObj, context, error)
        } else {
            return false
        }

    }
}
