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
package org.jenkinsci.plugins.pipeline.modeldefinition;

import com.cloudbees.hudson.plugins.folder.Folder;
import com.google.common.collect.ImmutableList;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Descriptor;
import hudson.model.JobPropertyDescriptor;
import hudson.model.ParameterDefinition;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.Slave;
import hudson.slaves.EnvironmentVariablesNodeProperty;
import hudson.slaves.NodeProperty;
import hudson.slaves.NodePropertyDescriptor;
import hudson.util.DescribableList;
import hudson.util.StreamTaskListener;
import hudson.util.VersionNumber;
import jenkins.plugins.git.GitSampleRepoRule;
import jenkins.plugins.git.GitStep;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.SystemUtils;
import org.hamcrest.Matcher;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.docker.commons.tools.DockerTool;
import org.jenkinsci.plugins.docker.workflow.client.DockerClient;
import org.jenkinsci.plugins.pipeline.modeldefinition.agent.DeclarativeAgentDescriptor;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTMethodCall;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTStep;
import org.jenkinsci.plugins.pipeline.modeldefinition.model.Options;
import org.jenkinsci.plugins.pipeline.modeldefinition.options.DeclarativeOptionDescriptor;
import org.jenkinsci.plugins.pipeline.modeldefinition.util.HasArchived;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.cps.CpsScmFlowDefinition;
import org.jenkinsci.plugins.workflow.cps.global.UserDefinedGlobalVariableList;
import org.jenkinsci.plugins.workflow.cps.global.WorkflowLibRepository;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.junit.Assume;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.ToolInstallations;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.jcabi.matchers.RegexMatchers.containsPattern;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.stringContainsInOrder;
import static org.hamcrest.text.IsEqualIgnoringWhiteSpace.equalToIgnoringWhiteSpace;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assume.assumeTrue;

/**
 * @author Andrew Bayer
 */
public abstract class AbstractDeclarativeTest {
    @Rule
    public GitSampleRepoRule sampleRepo = new GitSampleRepoRule();

    public enum PossibleOS {
        WINDOWS,
        LINUX,
        MAC
    }

    protected void onAllowedOS(PossibleOS... osList) throws Exception {
        boolean passed = false;
        for (PossibleOS os : osList) {
            switch (os) {
                case LINUX:
                    if (SystemUtils.IS_OS_LINUX) {
                        passed = true;
                    }
                    break;
                case WINDOWS:
                    if (SystemUtils.IS_OS_WINDOWS) {
                        passed = true;
                    }
                    break;
                case MAC:
                    if (SystemUtils.IS_OS_MAC) {
                        passed = true;
                    }
                    break;
                default:
                    break;
            }
        }

        Assume.assumeTrue("Not on a valid OS for this test", passed);
    }

    protected String pipelineSourceFromResources(String pipelineName) throws IOException {
        return fileContentsFromResources(pipelineName + ".groovy");
    }

    protected String fileContentsFromResources(String fileName) throws IOException {
        return fileContentsFromResources(fileName, false);
    }

    protected String fileContentsFromResources(String fileName, boolean swallowError) throws IOException {
        String fileContents = null;

        URL url = getClass().getResource("/" + fileName);
        if (url != null) {
            fileContents = IOUtils.toString(url);
        }

        if (!swallowError) {
            assertNotNull("No file contents for file " + fileName, fileContents);
        } else {
            assumeTrue(fileContents != null);
        }
        return fileContents;

    }

    protected boolean foundExpectedErrorInJSON(JSONArray errors, String expectedError) {
        for (Object e : errors) {
            if (e instanceof JSONObject) {
                JSONObject o = (JSONObject) e;
                if (o.getString("error").equals(expectedError)) {
                    return true;
                } else if (o.getString("error").contains(expectedError)) {
                    return true;
                }
            }
        }

        return false;
    }

    protected void prepRepoWithJenkinsfile(String pipelineName) throws Exception {
        prepRepoWithJenkinsfileAndOtherFiles(pipelineName);
    }

    protected void prepRepoWithJenkinsfile(String subDir, String pipelineName) throws Exception {
        prepRepoWithJenkinsfileAndOtherFiles(subDir + "/" + pipelineName);
    }

    protected void prepRepoWithJenkinsfileAndOtherFiles(String pipelineName, String... otherFiles) throws Exception {
        Map<String, String> otherMap = new HashMap<>();
        for (String otherFile : otherFiles) {
            otherMap.put(otherFile, otherFile);
        }
        prepRepoWithJenkinsfileAndOtherFiles(pipelineName, otherMap);
    }

    protected void prepRepoWithJenkinsfileAndOtherFiles(String pipelineName, Map<String, String> otherFiles) throws Exception {
        sampleRepo.init();
        sampleRepo.write("Jenkinsfile",
                pipelineSourceFromResources(pipelineName));
        sampleRepo.git("add", "Jenkinsfile");


        for (Map.Entry<String, String> otherFile : otherFiles.entrySet()) {
            if (otherFile != null) {
                sampleRepo.write(otherFile.getValue(), fileContentsFromResources(otherFile.getKey()));
                sampleRepo.git("add", otherFile.getValue());
            }
        }

        sampleRepo.git("commit", "--message=files");
    }

    protected void prepRepoWithJenkinsfileFromString(String jf) throws Exception {
        sampleRepo.init();
        sampleRepo.write("Jenkinsfile", jf);
        sampleRepo.git("add", "Jenkinsfile");

        sampleRepo.git("commit", "--message=files");
    }

    protected void assumeDocker() throws Exception {
        Launcher.LocalLauncher localLauncher = new Launcher.LocalLauncher(StreamTaskListener.NULL);
        try {
            Assume.assumeThat("Docker working", localLauncher.launch().cmds(DockerTool.getExecutable(null, null, null, null), "ps").join(), is(0));
        } catch (IOException x) {
            Assume.assumeNoException("have Docker installed", x);
        }
        DockerClient dockerClient = new DockerClient(localLauncher, null, null);
        Assume.assumeFalse("Docker version not < 1.3", dockerClient.version().isOlderThan(new VersionNumber("1.3")));
    }

    protected <T extends ParameterDefinition> T getParameterOfType(List<ParameterDefinition> params, Class<T> c) {
        for (ParameterDefinition p : params) {
            if (c.isInstance(p)) {
                return (T) p;
            }
        }
        return null;
    }
}
