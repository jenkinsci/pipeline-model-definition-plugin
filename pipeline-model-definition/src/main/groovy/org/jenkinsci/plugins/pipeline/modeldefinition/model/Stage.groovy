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

/**
 * An individual stage to be executed within the build.
 *
 * @author Andrew Bayer
 */
@ToString
@EqualsAndHashCode
@SuppressFBWarnings(value="SE_NO_SERIALVERSIONID")
class Stage implements Serializable {

    String name

    Agent agent

    PostStage post

    StageConditionals when

    Tools tools

    Environment environment

    StepsBlock steps

    Stages stages

    List<Stage> parallelContent = []

    @Deprecated
    transient Stages parallel

    boolean failFast

    StageOptions options

    StageInput input

    @Deprecated
    Stage(String name, StepsBlock steps, Agent agent, PostStage post, StageConditionals when, Tools tools,
          Environment environment, Stages parallel, boolean failFast) {
        this(name, steps, agent, post, when, tools, environment, failFast, null, null, parallel?.stages, null)
    }

    @Deprecated
    Stage(String name, StepsBlock steps, Agent agent, PostStage post, StageConditionals when, Tools tools,
          Environment environment, Stages parallel, boolean failFast, StageOptions options, StageInput input) {
        this(name, steps, agent, post, when, tools, environment, failFast, options, input, parallel?.stages, null)
    }

    @Whitelisted
    Stage(String name, StepsBlock steps, Agent agent, PostStage post, StageConditionals when, Tools tools,
          Environment environment, boolean failFast, StageOptions options, StageInput input,
          List<Stage> parallelContent, Stages stages) {
        this.name = name
        this.agent = agent
        this.post = post
        this.when = when
        this.tools = tools
        this.environment = environment
        this.steps = steps
        this.failFast = failFast
        this.options = options
        this.input = input
        this.stages = stages
        if (parallelContent != null) {
            this.parallelContent.addAll(parallelContent)
        }
    }

    protected Object readResolve() throws IOException {
        if (this.parallel != null) {
            if (this.parallelContent == null) {
                this.parallelContent = []
            }
            this.parallelContent.addAll(this.parallel.stages)
            this.parallel = null
        }
        return this
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
}
