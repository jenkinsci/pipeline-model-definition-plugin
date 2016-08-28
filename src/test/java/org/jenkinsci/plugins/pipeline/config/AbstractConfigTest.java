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
package org.jenkinsci.plugins.pipeline.config;

import com.google.common.collect.ImmutableList;
import hudson.Launcher;
import hudson.util.StreamTaskListener;
import hudson.util.VersionNumber;
import jenkins.plugins.git.GitSampleRepoRule;
import jenkins.plugins.git.GitStep;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.SystemUtils;
import org.jenkinsci.plugins.docker.commons.tools.DockerTool;
import org.jenkinsci.plugins.docker.workflow.client.DockerClient;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.cps.CpsScmFlowDefinition;
import org.jenkinsci.plugins.workflow.cps.global.UserDefinedGlobalVariableList;
import org.jenkinsci.plugins.workflow.cps.global.WorkflowLibRepository;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Assume;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.runners.model.Statement;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.RestartableJenkinsRule;
import org.jvnet.hudson.test.ToolInstallations;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assume.assumeTrue;

/**
 * @author Andrew Bayer
 */
public abstract class AbstractConfigTest {
    @ClassRule
    public static BuildWatcher buildWatcher = new BuildWatcher();
    @Rule
    public JenkinsRule j = new JenkinsRule();
    @Rule public GitSampleRepoRule sampleRepo = new GitSampleRepoRule();
    @Rule public GitSampleRepoRule otherRepo = new GitSampleRepoRule();

    @Inject
    WorkflowLibRepository globalLibRepo;

    @Inject
    UserDefinedGlobalVariableList uvl;

    @Before
    public void setUp() throws Exception {
        ToolInstallations.configureMaven3();
    }

    public static final List<String> SHOULD_PASS_CONFIGS = ImmutableList.of(
            "simplePipeline",
            "agentDocker",
            "agentLabel",
            "agentNoneWithNode",
            "metaStepSyntax",
            "postBuildAndNotifications",
            "simpleEnvironment",
            "simpleScript",
            "twoStagePipeline",
            "validStepParameters",
            "simpleEnvironment",
            "parallelPipeline",
            "simpleNotification",
            "simplePostBuild",
            "simpleTools",
            "legacyMetaStepSyntax"
    );

    public static Iterable<Object[]> configsWithErrors() {
        List<Object[]> result = new ArrayList<>();
        // First element is config name, second element is expected JSON error.
        result.add(new Object[]{"missingStages", "At /pipeline: Missing one or more required properties: 'stages'"});
        result.add(new Object[]{"missingAgent", "At /pipeline: Missing one or more required properties: 'agent'"});

        result.add(new Object[]{"emptyStages", "At /pipeline/stages: Array has 0 entries, requires minimum of 1"});
        result.add(new Object[]{"emptyEnvironment", "At /pipeline/environment: Array has 0 entries, requires minimum of 1"});
        result.add(new Object[]{"emptyPostBuild", "At /pipeline/postBuild/conditions: Array has 0 entries, requires minimum of 1"});
        result.add(new Object[]{"emptyNotifications", "At /pipeline/notifications/conditions: Array has 0 entries, requires minimum of 1"});

        result.add(new Object[]{"rejectStageInSteps", "Invalid step 'stage' used - not allowed in this context"});

        result.add(new Object[]{"stageWithoutName", "At /pipeline/stages/0: Missing one or more required properties: 'name'"});

        result.add(new Object[]{"emptyParallel", "Nothing to execute within stage 'foo'"});

        result.add(new Object[]{"malformed", "Expected a ',' or '}' at character 244 of {\"pipeline\": {\n" +
                "  \"stages\": [  {\n" +
                "    \"name\": \"foo\",\n" +
                "    \"branches\": [    {\n" +
                "      \"name\": \"default\",\n" +
                "      \"steps\": [      {\n" +
                "        \"name\": \"echo\",\n" +
                "        \"arguments\":         {\n" +
                "          \"isConstant\": true,\n" +
                "          \"value\": \"hello\"\n" +
                "\n" +
                "      }]\n" +
                "    }]\n" +
                "  }],\n" +
                "  \"agent\":   {\n" +
                "    \"isConstant\": true,\n" +
                "    \"value\": \"none\"\n" +
                "  }\n" +
                "}}"});

        return result;
    }

    public static Iterable<Object[]> runtimeConfigsWithErrors() {
        List<Object[]> result = new ArrayList<>();
        for (Object[] e : configsWithErrors()) {
            result.add(e);
        }
        result.add(new Object[] { "notInstalledToolVersion",
                "Tool type 'maven' does not have an install of 'apache-maven-3.0.2' configured - did you mean 'apache-maven-3.0.1'?" });

        return result;
    }


    public enum PossibleOS {
        WINDOWS,
        LINUX,
        MAC
    }

    protected void onAllowedOS(PossibleOS... osList) throws Exception {
        boolean passed = true;
        for (PossibleOS os : osList) {
            switch (os) {
                case LINUX:
                    if (!SystemUtils.IS_OS_LINUX) {
                        passed = false;
                    }
                    break;
                case WINDOWS:
                    if (!SystemUtils.IS_OS_WINDOWS) {
                        passed = false;
                    }
                    break;
                case MAC:
                    if (!SystemUtils.IS_OS_MAC) {
                        passed = false;
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

    protected void prepRepoWithJenkinsfile(String pipelineName) throws Exception {
        prepRepoWithJenkinsfileAndOtherFiles(pipelineName);
    }

    protected void prepRepoWithJenkinsfile(String subDir, String pipelineName) throws Exception {
        prepRepoWithJenkinsfileAndOtherFiles(subDir + "/" + pipelineName);
    }

    protected void prepRepoWithJenkinsfileAndOtherFiles(String pipelineName, String... otherFiles) throws Exception {
        sampleRepo.init();
        sampleRepo.write("Jenkinsfile",
                pipelineSourceFromResources(pipelineName));
        sampleRepo.git("add", "Jenkinsfile");


        for (String otherFile : otherFiles) {
            if (otherFile != null) {
                sampleRepo.write(otherFile, fileContentsFromResources(otherFile));
                sampleRepo.git("add", otherFile);
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

    protected WorkflowRun getAndStartBuild() throws Exception {
        WorkflowJob p = j.createProject(WorkflowJob.class);
        p.setDefinition(new CpsScmFlowDefinition(new GitStep(sampleRepo.toString()).createSCM(), "Jenkinsfile"));
        return p.scheduleBuild2(0).waitForStart();
    }

    protected WorkflowRun getAndStartNonRepoBuild(String pipelineScriptFile) throws Exception {
        WorkflowJob p = j.createProject(WorkflowJob.class);
        p.setDefinition(new CpsFlowDefinition(pipelineSourceFromResources(pipelineScriptFile)));
        return p.scheduleBuild2(0).waitForStart();
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

    protected void initGlobalLibrary() throws IOException {
        // Need to do the injection by hand because we're not running with a RestartableJenkinsRule.
        j.jenkins.getInjector().injectMembers(this);
        File vars = new File(globalLibRepo.workspace, "vars");
        vars.mkdirs();
        FileUtils.writeStringToFile(new File(vars, "acmeVar.groovy"), StringUtils.join(Arrays.asList(
                "def hello(name) {echo \"Hello ${name}\"}",
                "def foo(x) { this.x = x+'-set'; }",
                "def bar() { return x+'-get' }",
                "def baz() { return 'nothing here' }")
                , "\n"));
        FileUtils.writeStringToFile(new File(vars, "acmeFunc.groovy"), StringUtils.join(Arrays.asList(
                "def call(a,b) { echo \"call($a,$b)\" }")
                , "\n"));
        FileUtils.writeStringToFile(new File(vars, "acmeFuncMap.groovy"), StringUtils.join(Arrays.asList(
                "def call(m) { echo \"map call(${m.a},${m.b})\" }")
                , "\n"));
        FileUtils.writeStringToFile(new File(vars, "acmeBody.groovy"), StringUtils.join(Arrays.asList(
                "def call(body) { ",
                "  def config = [:]",
                "  body.resolveStrategy = Closure.DELEGATE_FIRST",
                "  body.delegate = config",
                "  body()",
                "  echo 'title was '+config.title",
                "}")
                , "\n"));

        // simulate the effect of push
        uvl.rebuild();
    }


}
