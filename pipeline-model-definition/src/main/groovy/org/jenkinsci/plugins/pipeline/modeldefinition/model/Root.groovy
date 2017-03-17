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
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTPipelineDef
import org.jenkinsci.plugins.pipeline.modeldefinition.steps.CredentialWrapper
import org.jenkinsci.plugins.workflow.cps.CpsScript
import org.jenkinsci.plugins.workflow.job.WorkflowRun
import org.jenkinsci.plugins.workflow.support.steps.build.RunWrapper

import javax.annotation.CheckForNull
import javax.annotation.Nonnull

/**
 * Root-level configuration object for the entire model.
 *
 * @author Andrew Bayer
 */
@ToString
@EqualsAndHashCode
@SuppressFBWarnings(value="SE_NO_SERIALVERSIONID")
public class Root implements Serializable {
    Agent agent

    Stages stages

    PostBuild post

    Environment environment

    Tools tools

    Options options

    Triggers triggers

    Parameters parameters

    Libraries libraries

    /**
     * Helper method for translating the key/value pairs in the {@link Environment} into a list of "key=value" strings
     * suitable for use with the withEnv step.
     *
     * @return a list of "key=value" strings.
     */
    List<String> getEnvVars(CpsScript script) {
        if (environment != null) {
            return environment.resolveEnvVars(script, true).findAll {
                it.key in environment.keySet()
            }.collect { k, v ->
                "${k}=${v}"
            }
        } else {
            return []
        }
    }

    Map<String, CredentialWrapper> getEnvCredentials() {
        Map<String, CredentialWrapper> m = [:]
        environment.each {k, v ->
            if (v instanceof  CredentialWrapper) {
                m["${k}"] = v;
            }
        }
        return m
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

    @CheckForNull
    public static Root fromAST(@Nonnull WorkflowRun run, @CheckForNull ModelASTPipelineDef ast) {
        if (ast != null) {
            Root r = new Root()
            r.agent = Agent.fromAST(ast.agent)
            r.stages = Stages.fromAST(run, ast.stages)
            r.environment = Environment.fromAST(run, ast.environment)
            r.libraries = Libraries.fromAST(ast.libraries)
            r.tools = Tools.fromAST(ast.tools)
            r.options = Options.fromAST(ast.options)
            r.triggers = Triggers.fromAST(ast.triggers)
            r.parameters = Parameters.fromAST(ast.parameters)
            r.post = PostBuild.fromAST(ast.postBuild)

            return r
        } else {
            return null
        }
    }
}
