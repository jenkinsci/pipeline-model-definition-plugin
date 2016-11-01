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
    @Whitelisted
    Agent agent

    @Whitelisted
    Stages stages

    @Whitelisted
    Notifications notifications

    @Whitelisted
    PostBuild postBuild

    @Whitelisted
    Environment environment

    @Whitelisted
    Tools tools

    @Whitelisted
    JobProperties jobProperties

    @Whitelisted
    Triggers triggers

    @Whitelisted
    Parameters parameters

    @Whitelisted
    Wrappers wrappers

    @Whitelisted
    Root stages(Stages s) {
        this.stages = s
        return this
    }

    @Whitelisted
    Root notifications(Notifications n) {
        this.notifications = n
        return this
    }

    @Whitelisted
    Root postBuild(PostBuild p) {
        this.postBuild = p
        return this
    }

    @Whitelisted
    Root agent(Map<String,String> args) {
        this.agent = new Agent(args)
        return this
    }

    @Whitelisted
    Root agent(String s) {
        this.agent = new Agent(s)
        return this
    }

    @Whitelisted
    Root environment(Environment m) {
        this.environment = m
        return this
    }

    @Whitelisted
    Root tools(Tools t) {
        this.tools = t
        return this
    }

    @Whitelisted
    Root jobProperties(JobProperties p) {
        this.jobProperties = p
        return this
    }

    @Whitelisted
    Root triggers(Triggers t) {
        this.triggers = t
        return this
    }

    @Whitelisted
    Root parameters(Parameters p) {
        this.parameters = p
        return this
    }

    @Whitelisted
    Root wrappers(Wrappers w) {
        this.wrappers = w
        return this
    }

    /**
     * Helper method for translating the key/value pairs in the {@link Environment} into a list of "key=value" strings
     * suitable for use with the withEnv step.
     *
     * @return a list of "key=value" strings.
     */
    @Whitelisted
    List<String> getEnvVars() {
        return environment.collect { k, v ->
            "${k}=${v}"
        }
    }

    /**
     * Returns a list of notification closures whose conditions have been satisfied and should be run.
     *
     * @param runWrapperObj The {@link RunWrapper} for the build.
     * @return a list of closures whose conditions have been satisfied.
     */
    @Whitelisted
    List<Closure> satisfiedNotifications(Object runWrapperObj) {
        return satisfiedConditionsForField(notifications, runWrapperObj)
    }

    /**
     * Returns a list of post-build closures whose conditions have been satisfied and should be run.
     *
     * @param runWrapperObj The {@link RunWrapper} for the build.
     * @return a list of closures whose conditions have been satisfied.
     */
    @Whitelisted
    List<Closure> satisfiedPostBuilds(Object runWrapperObj) {
        return satisfiedConditionsForField(postBuild, runWrapperObj)
    }

    @Override
    @Whitelisted
    public void modelFromMap(Map<String,Object> m) {
        m.each { k, v ->
            this."${k}"(v)
        }
    }

    /**
     * Gets the list of satisfied build condition closures for the given responder.
     *
     * @param r an {@link AbstractBuildConditionResponder}, such as {@link Notifications} or {@link PostBuild}.
     * @param runWrapperObj The {@link RunWrapper} for the build.
     * @return A list of closures from the responder which have had their conditions satisfied.
     */
    /*package*/ List<Closure> satisfiedConditionsForField(AbstractBuildConditionResponder r, Object runWrapperObj) {
        if (r != null) {
            return r.satisfiedConditions(runWrapperObj)
        } else {
            return []
        }

    }
}
