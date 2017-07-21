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
import org.codehaus.groovy.ast.ASTNode
import org.codehaus.groovy.ast.tools.GeneralUtils
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTStage
import org.jenkinsci.plugins.pipeline.modeldefinition.parser.ASTParserUtils
import org.jenkinsci.plugins.scriptsecurity.sandbox.whitelists.Whitelisted
import org.jenkinsci.plugins.workflow.cps.CpsScript

import javax.annotation.CheckForNull

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

    @Whitelisted
    Stage(String name, StepsBlock steps, Agent agent, PostStage post, StageConditionals when, Tools tools,
          Environment environment) {
        this.name = name
        this.steps = steps
        this.agent = agent
        this.post = post
        this.when = when
        this.tools = tools
        this.environment = environment
    }

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

    /**
     * Helper method for translating the key/value pairs in the {@link Environment} into a list of "key=value" strings
     * suitable for use with the withEnv step.
     *
     * @return a list of "key=value" strings.
     */
    List<List<Object>> getEnvVars(CpsScript script) {
        if (environment != null) {
            environment.envResolver.setScript(script)
            return environment.envResolver.closureMap.collect { k, v ->
                [k, v]
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

    static ASTNode transformToRuntimeAST(@CheckForNull ModelASTStage original) {
        if (original != null) {
            return ASTParserUtils.buildAst {
                constructorCall(Stage) {
                    argumentList {
                        constant original.name
                        expression.add(StepsBlock.transformToRuntimeAST(original))
                        expression.add(Agent.transformToRuntimeAST(original.agent))
                        expression.add(PostStage.transformToRuntimeAST(original.post))
                        expression.add(StageConditionals.transformToRuntimeAST(original.when))
                        expression.add(Tools.transformToRuntimeAST(original.tools))
                        expression.add(Environment.transformToRuntimeAST(original.environment))
                    }
                }
            }
        }

        return GeneralUtils.constX(null)
    }
}
