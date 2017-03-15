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
import hudson.EnvVars
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTEnvironment
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTInternalFunctionCall
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTValue
import org.jenkinsci.plugins.pipeline.modeldefinition.steps.CredentialWrapper
import org.jenkinsci.plugins.workflow.cps.CpsScript
import org.jenkinsci.plugins.workflow.cps.EnvActionImpl
import org.jenkinsci.plugins.workflow.job.WorkflowRun

import javax.annotation.CheckForNull
import javax.annotation.Nonnull

/**
 * Special wrapper for environment to deal with mapped closure problems with property declarations.
 *
 * @author Andrew Bayer
 */
@SuppressFBWarnings(value="SE_NO_SERIALVERSIONID")
public class Environment implements Serializable {
    @Delegate Map<String,Object> envMap = new TreeMap<>()

    public Map<String,Object> getMap() {
        def mapCopy = new TreeMap()
        mapCopy.putAll(envMap)
        return mapCopy
    }

    @CheckForNull
    public static Environment fromAST(@Nonnull WorkflowRun r, @CheckForNull ModelASTEnvironment ast) {
        if (ast != null) {
            Environment env = new Environment()

            Map<String, Object> inMap = new TreeMap<>()
            ast.variables.each { k, v ->
                if (v instanceof ModelASTInternalFunctionCall) {
                    ModelASTInternalFunctionCall func = (ModelASTInternalFunctionCall) v
                    // TODO: JENKINS-41759 - look up the right method and dispatch accordingly, with the right # of args
                    String credId = func.args.first().value
                    CredentialsBindingHandler handler = CredentialsBindingHandler.forId(credId, r)
                    inMap.put(k.key, new CredentialWrapper(credId, handler.getWithCredentialsParameters(credId)))
                } else {
                    inMap.put(k.key, ((ModelASTValue) v).value)
                }
            }

            env.putAll(inMap)

            return env
        } else {
            return null
        }
    }

    public EnvVars resolveEnvVars(CpsScript script, boolean withContext, Environment parent = null) {
        EnvVars newEnv

        if (withContext) {
            EnvVars contextEnv = ((EnvActionImpl)script.getProperty("env")).getEnvironment()
            newEnv = new EnvVars(contextEnv)
        } else {
            newEnv = new EnvVars()
        }

        if (parent != null) {
            newEnv.overrideExpandingAll(parent.resolveEnvVars(script, false))
        }

        Map<String,String> overrides = getMap().findAll {
            !(it.value instanceof CredentialWrapper)
        }.collectEntries { k, v ->
            [(k): v.toString()]
        }

        newEnv.overrideExpandingAll(overrides)

        return newEnv
    }

}
