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
import org.jenkinsci.plugins.workflow.cps.CpsScript

/**
 * An individual stage to be executed within the build.
 *
 * @author Andrew Bayer
 */
@ToString
@EqualsAndHashCode
@SuppressFBWarnings(value="SE_NO_SERIALVERSIONID")
public class Stage implements NestedModel, Serializable {

    String name

    StepsBlock steps

    Agent agent

    PostStage post

    StageConditionals when

    Tools tools

    Environment environment

    Stages parallel

    Stage name(String n) {
        this.name = n
        return this
    }

    Stage agent(Agent a) {
        this.agent = a?.convertZeroArgs()
        return this
    }

    Stage steps(StepsBlock s) {
        this.steps = s
        return this
    }

    Stage post(PostStage post) {
        this.post = post
        return this
    }

    Stage when(StageConditionals when) {
        this.when = when
        return this
    }

    Stage tools(Tools tools) {
        this.tools = tools
        return this
    }

    Stage environment(Environment environment) {
        this.environment = environment
        return this
    }

    Stage parallel(Stages stages) {
        this.parallel = stages
        return this
    }
    /**
     * Helper method for translating the key/value pairs in the {@link Environment} into a list of "key=value" strings
     * suitable for use with the withEnv step.
     *
     * @return a list of "key=value" strings.
     */
    List<String> getEnvVars(Root root, CpsScript script, Stage parentStage = null) {
        if (environment != null) {
            return environment.resolveEnvVars(script, true, root.environment, parentStage).findAll {
                it.key in environment.getMap().keySet()
            }.collect { k, v ->
                "${k}=${v}"
            }
        } else {
            return []
        }
    }

    @Override
    public void modelFromMap(Map<String,Object> m) {
        m.each { k, v ->
            this."${k}"(v)
        }
    }
}
