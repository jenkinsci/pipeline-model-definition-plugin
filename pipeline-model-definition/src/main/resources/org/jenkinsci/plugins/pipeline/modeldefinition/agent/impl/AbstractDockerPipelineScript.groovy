/*
 * The MIT License
 *
 * Copyright (c) 2017, CloudBees, Inc.
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


package org.jenkinsci.plugins.pipeline.modeldefinition.agent.impl

import hudson.FilePath
import org.jenkinsci.plugins.pipeline.modeldefinition.agent.AbstractDockerAgent
import org.jenkinsci.plugins.pipeline.modeldefinition.agent.DeclarativeAgentScript
import org.jenkinsci.plugins.pipeline.modeldefinition.agent.DeclarativeDockerUtils
import org.jenkinsci.plugins.workflow.cps.CpsScript

abstract class AbstractDockerPipelineScript<A extends AbstractDockerAgent<A>> extends DeclarativeAgentScript<A> {

    AbstractDockerPipelineScript(CpsScript s, A a) {
        super(s, a)
    }

    @Override
    Closure run(Closure body) {
        if (describable.reuseNode && script.getContext(FilePath.class) != null) {
            return {
                configureRegistry(body).call()
            }
        } else if (describable.containerPerStageRoot) {
            return getLabelScript().run {
                body.call()
            }
        } else {
            return getLabelScript().run {
                configureRegistry(body).call()
            }
        }
    }

    protected LabelScript getLabelScript() {
        String targetLabel = DeclarativeDockerUtils.getLabel(describable.label)
        Label l = (Label) Label.DescriptorImpl.instanceForName("label", [label: targetLabel])
        l.copyFlags(describable)
        l.customWorkspace = describable.customWorkspace
        return (LabelScript) l.getScript(script)
    }

    protected Closure configureRegistry(Closure body) {
        return {
            String registryUrl = DeclarativeDockerUtils.getRegistryUrl(describable.registryUrl)
            String registryCreds = DeclarativeDockerUtils.getRegistryCredentialsId(describable.registryCredentialsId)
            if (registryUrl != null) {
                script.getProperty("docker").withRegistry(registryUrl, registryCreds) {
                    runImage(body).call()
                }
            } else {
                runImage(body).call()
            }
        }
    }

    protected abstract Closure runImage(Closure body)
}