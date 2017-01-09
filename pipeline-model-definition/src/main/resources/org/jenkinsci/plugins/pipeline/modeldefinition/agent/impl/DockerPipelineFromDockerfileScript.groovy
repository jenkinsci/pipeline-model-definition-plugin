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


package org.jenkinsci.plugins.pipeline.modeldefinition.agent.impl

import hudson.model.Result
import org.jenkinsci.plugins.pipeline.modeldefinition.SyntheticStageNames
import org.jenkinsci.plugins.pipeline.modeldefinition.Utils
import org.jenkinsci.plugins.pipeline.modeldefinition.agent.DeclarativeAgentScript
import org.jenkinsci.plugins.workflow.cps.CpsScript

public class DockerPipelineFromDockerfileScript extends DeclarativeAgentScript<DockerPipelineFromDockerfile> {

    public DockerPipelineFromDockerfileScript(CpsScript s, DockerPipelineFromDockerfile a) {
        super(s, a)
    }

    @Override
    public Closure run(Closure body) {
        String targetLabel = describable.label
        if (targetLabel == null) {
            targetLabel = script.dockerLabel()?.trim()
        }
        LabelScript labelScript = (LabelScript) Label.DescriptorImpl.instanceForName("label", [label: targetLabel]).getScript(script)
        return labelScript.run {
            def img = null
            if (!Utils.withinAStage()) {
                script.stage(SyntheticStageNames.agentSetup()) {
                    Utils.markSyntheticStage(SyntheticStageNames.agentSetup(), Utils.getSyntheticStageMetadata().pre)
                    try {
                        img = buildImage().call()
                    } catch (Exception e) {
                        script.getProperty("currentBuild").result = Result.FAILURE
                        Utils.markStageFailedAndContinued(SyntheticStageNames.agentSetup())
                        throw e
                    }
                }
            } else {
                try {
                    img = buildImage().call()
                } catch (Exception e) {
                    script.getProperty("currentBuild").result = Result.FAILURE
                    throw e
                }
            }
            if (img != null) {
                try {
                    img.inside(describable.args, {
                        body.call()
                    })
                } catch (Exception e) {
                    script.getProperty("currentBuild").result = Result.FAILURE
                    throw e
                }
            }

        }
    }

    private Closure buildImage() {
        return {
            script.checkout script.scm
            try {
                def hash = Utils.stringToSHA1(script.readFile(describable.getDockerfileAsString()))
                def imgName = "${hash}"
                return script.getProperty("docker").build(imgName, "-f ${describable.getDockerfileAsString()} .")
            } catch (FileNotFoundException f) {
                script.error("No Dockerfile found at root of repository - failing.")
                return null
            }
        }
    }
}