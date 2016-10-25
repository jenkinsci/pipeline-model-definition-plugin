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

/**
 * An individual stage to be executed within the build.
 *
 * @author Andrew Bayer
 */
@ToString
@EqualsAndHashCode
@SuppressFBWarnings(value="SE_NO_SERIALVERSIONID")
public class Stage implements NestedModel, Serializable {

    @Whitelisted
    String name

    @Whitelisted
    StepsBlock steps

    @Whitelisted
    Agent agent

    @Whitelisted
    PostStage post

    @Whitelisted
    Stage name(String n) {
        this.name = n
        return this
    }

    @Whitelisted
    Stage agent(Agent a) {
        this.agent = a
        return this
    }

    @Whitelisted
    Stage agent(Map<String,String> args) {
        this.agent = new Agent(args)
        return this
    }

    Stage agent(String s) {
        this.agent = new Agent(s)
        return this
    }
    
    @Whitelisted
    Stage steps(StepsBlock s) {
        this.steps = s
        return this
    }

    @Whitelisted
    Stage post(PostStage post) {
        this.post = post
        return this
    }

    @Override
    @Whitelisted
    public void modelFromMap(Map<String,Object> m) {
        m.each { k, v ->
            this."${k}"(v)
        }
    }

    /**
     * Returns a list of notification closures whose conditions have been satisfied and should be run.
     *
     * @param runWrapperObj The {@link org.jenkinsci.plugins.workflow.support.steps.build.RunWrapper} for the build.
     * @return a list of closures whose conditions have been satisfied.
     */
    @Whitelisted
    List<Closure> satisfiedPostStageConditions(Root root, Object runWrapperObj) {
        return root.satisfiedConditionsForField(post, runWrapperObj)
    }

}
