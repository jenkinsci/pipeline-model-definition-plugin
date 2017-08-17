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

import org.jenkinsci.plugins.pipeline.modeldefinition.SyntheticStageNames
import org.jenkinsci.plugins.pipeline.modeldefinition.Utils
import org.jenkinsci.plugins.workflow.cps.CpsScript

public class DockerPipelineFromDockerfileScript extends AbstractDockerPipelineScript<DockerPipelineFromDockerfile> {

    public DockerPipelineFromDockerfileScript(CpsScript s, DockerPipelineFromDockerfile a) {
        super(s, a)
    }

    @Override
    public Closure runImage(Closure body) {
        return {
            def img = null
            if (!Utils.withinAStage()) {
                script.stage(SyntheticStageNames.agentSetup()) {
                    try {
                        img = buildImage().call()
                    } catch (Exception e) {
                        script.getProperty("currentBuild").result = Utils.getResultFromException(e)
                        Utils.markStageFailedAndContinued(SyntheticStageNames.agentSetup())
                        throw e
                    }
                }
            } else {
                try {
                    img = buildImage().call()
                } catch (Exception e) {
                    script.getProperty("currentBuild").result = Utils.getResultFromException(e)
                    throw e
                }
            }
            if (img != null) {
                try {
                    img.inside(describable.args, {
                        body.call()
                    })
                } catch (Exception e) {
                    script.getProperty("currentBuild").result = Utils.getResultFromException(e)
                    throw e
                }
            }
        }
    }

    private Closure buildImage() {
        return {
            try {
                def hash = Utils.stringToSHA1(script.readFile("${describable.getDockerfilePath()}"))
                def imgName = "${hash}"
                def additionalBuildArgs = describable.getAdditionalBuildArgs() ? " ${describable.additionalBuildArgs}" : ""
                script.sh "docker build -t ${imgName}${additionalBuildArgs} -f \"${describable.getDockerfilePath()}\" \"${describable.getActualDir()}\""
                script.dockerFingerprintFrom dockerfile: describable.dockerfilePath, image: imgName, toolName: script.env.DOCKER_TOOL_NAME
                return script.getProperty("docker").image(imgName)
            } catch (FileNotFoundException f) {
                script.error("No Dockerfile found at ${describable.getDockerfilePath()} in repository - failing.")
                return null
            }
        }
    }
}