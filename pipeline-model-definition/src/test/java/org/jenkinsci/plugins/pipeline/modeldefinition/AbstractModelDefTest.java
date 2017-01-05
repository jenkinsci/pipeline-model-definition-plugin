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
package org.jenkinsci.plugins.pipeline.modeldefinition;

import com.cloudbees.hudson.plugins.folder.Folder;
import com.google.common.collect.ImmutableList;
import hudson.Launcher;
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
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.SystemUtils;
import org.hamcrest.Matcher;
import org.jenkinsci.plugins.docker.commons.tools.DockerTool;
import org.jenkinsci.plugins.docker.workflow.client.DockerClient;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTStep;
import org.jenkinsci.plugins.pipeline.modeldefinition.util.HasArchived;
import org.jenkinsci.plugins.pipeline.modeldefinition.when.DeclarativeStageConditionalDescriptor;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.stringContainsInOrder;
import static org.junit.Assert.assertNotNull;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.text.IsEqualIgnoringWhiteSpace.equalToIgnoringWhiteSpace;
import static org.junit.Assert.assertThat;
import static org.junit.Assume.assumeTrue;

/**
 * @author Andrew Bayer
 */
public abstract class AbstractModelDefTest {
    @ClassRule
    public static BuildWatcher buildWatcher = new BuildWatcher();
    @ClassRule
    public static JenkinsRule j = new JenkinsRule();
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
            "agentAny",
            "agentDocker",
            "agentLabel",
            "agentNoneWithNode",
            "metaStepSyntax",
            "simpleEnvironment",
            "simpleScript",
            "twoStagePipeline",
            "validStepParameters",
            "simpleEnvironment",
            "parallelPipeline",
            "simplePostBuild",
            "simpleTools",
            "legacyMetaStepSyntax",
            "globalLibrarySuccess",
            "perStageConfigAgent",
            "simpleJobProperties",
            "simpleTriggers",
            "simpleParameters",
            "stringsNeedingEscapeLogic",
            "simpleWrapper",
            "multipleWrappers",
            "agentTypeOrdering",
            "toolsInStage",
            "environmentInStage",
            "basicWhen",
            "skippedWhen",
            "parallelPipelineWithFailFast",
            "whenBranch",
            "whenEnv",
            "parallelPipelineWithSpaceInBranch",
            "parallelPipelineQuoteEscaping"
    );

    public static Iterable<Object[]> configsWithErrors() {
        List<Object[]> result = new ArrayList<>();
        // First element is config name, second element is expected JSON error.
        result.add(new Object[]{"missingStages", Messages.JSONParser_MissingRequiredProperties("/pipeline", "'stages'")});
        result.add(new Object[]{"missingAgent", Messages.JSONParser_MissingRequiredProperties("/pipeline", "'agent'")});

        result.add(new Object[]{"emptyStages", Messages.JSONParser_TooFewItems("/pipeline/stages", 0, 1)});
        result.add(new Object[]{"emptyEnvironment", Messages.JSONParser_TooFewItems("/pipeline/environment", 0, 1)});
        result.add(new Object[]{"emptyPostBuild", Messages.JSONParser_TooFewItems("/pipeline/post/conditions", 0, 1)});

        result.add(new Object[]{"rejectStageInSteps", Messages.ModelValidatorImpl_BlockedStep("stage", ModelASTStep.getBlockedSteps().get("stage"))});
        result.add(new Object[]{"rejectParallelMixedInSteps", Messages.ModelValidatorImpl_BlockedStep("parallel", ModelASTStep.getBlockedSteps().get("parallel"))});

        result.add(new Object[]{"stageWithoutName", Messages.JSONParser_MissingRequiredProperties("/pipeline/stages/0", "'name'")});

        result.add(new Object[]{"emptyParallel", Messages.ModelValidatorImpl_NothingForStage("foo")});

        result.add(new Object[]{"emptyJobProperties", Messages.JSONParser_TooFewItems("/pipeline/options/options", 0, 1)});
        result.add(new Object[]{"emptyParameters", Messages.JSONParser_TooFewItems("/pipeline/parameters/parameters", 0, 1)});
        result.add(new Object[]{"emptyTriggers", Messages.JSONParser_TooFewItems("/pipeline/triggers/triggers", 0, 1)});
        result.add(new Object[]{"emptyWhen", Messages.JSONParser_TooFewItems("/pipeline/stages/0/when/conditions", 0, 1)});
        result.add(new Object[]{"mixedMethodArgs", Messages.ModelValidatorImpl_MixedNamedAndUnnamedParameters()});

        result.add(new Object[]{"rejectPropertiesStepInMethodCall",
                Messages.ModelValidatorImpl_BlockedStep("properties", ModelASTStep.getBlockedSteps().get("properties"))});

        result.add(new Object[]{"wrongParameterNameMethodCall", Messages.ModelValidatorImpl_InvalidStepParameter("namd", "name")});
        result.add(new Object[]{"invalidParameterTypeMethodCall", Messages.ModelValidatorImpl_InvalidParameterType("class java.lang.String", "name", "1234")});

        result.add(new Object[]{"perStageConfigEmptySteps", Messages.JSONParser_TooFewItems("/pipeline/stages/0/branches/0/steps", 0, 1)});
        result.add(new Object[]{"perStageConfigMissingSteps", Messages.JSONParser_MissingRequiredProperties("/pipeline/stages/0/branches/0",
                "'steps'")});
        result.add(new Object[]{"perStageConfigUnknownSection", "At /pipeline/stages/0: additional properties are not allowed"});

        result.add(new Object[]{"unknownAgentType", Messages.ModelValidatorImpl_NoAgentType("[otherField, docker, dockerfile, label, any, none]")});
        result.add(new Object[]{"invalidWrapperType", Messages.ModelValidatorImpl_InvalidSectionType("option", "echo", "[buildDiscarder, catchError, disableConcurrentBuilds, overrideIndexTriggers, retry, script, skipDefaultCheckout, timeout, waitUntil, withContext, withEnv, ws]")});

        result.add(new Object[]{"unknownBareAgentType", Messages.ModelValidatorImpl_InvalidAgent("foo", "[any, none]")});
        result.add(new Object[]{"agentMissingRequiredParam", Messages.ModelValidatorImpl_MissingAgentParameter("otherField", "label")});
        result.add(new Object[]{"agentUnknownParamForType", Messages.ModelValidatorImpl_InvalidAgentParameter("fruit", "otherField", "[label, otherField]")});
        result.add(new Object[]{"notificationsSectionRemoved", "At /pipeline: additional properties are not allowed"});
        result.add(new Object[]{"unknownWhenConditional", Messages.ModelValidatorImpl_UnknownWhenConditional("banana",
                "branch, environment, expression")});
        result.add(new Object[]{"whenInvalidParameterType", Messages.ModelValidatorImpl_InvalidUnnamedParameterType("class java.lang.String", 4)});
        result.add(new Object[]{"whenMissingRequiredParameter", Messages.ModelValidatorImpl_MissingRequiredStepParameter("value")});
        result.add(new Object[]{"whenUnknownParameter", Messages.ModelValidatorImpl_InvalidStepParameter("banana", "name")});

        result.add(new Object[]{"malformed", "Expected a ',' or '}' at character 243 of {\"pipeline\": {\n" +
                "  \"stages\": [  {\n" +
                "    \"name\": \"foo\",\n" +
                "    \"branches\": [    {\n" +
                "      \"name\": \"default\",\n" +
                "      \"steps\": [      {\n" +
                "        \"name\": \"echo\",\n" +
                "        \"arguments\":         {\n" +
                "          \"isLiteral\": true,\n" +
                "          \"value\": \"hello\"\n" +
                "\n" +
                "      }]\n" +
                "    }]\n" +
                "  }],\n" +
                "  \"agent\":   {\n" +
                "    \"isLiteral\": true,\n" +
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
                Messages.ModelValidatorImpl_NoToolVersion("maven", "apache-maven-3.0.2", "apache-maven-3.0.1")});

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
        return getAndStartBuild(null);
    }

    protected WorkflowRun getAndStartBuild(Folder folder) throws Exception {
        WorkflowJob p = createWorkflowJob(folder);
        p.setDefinition(new CpsScmFlowDefinition(new GitStep(sampleRepo.toString()).createSCM(), "Jenkinsfile"));
        return p.scheduleBuild2(0).waitForStart();
    }

    protected WorkflowRun getAndStartNonRepoBuild(String pipelineScriptFile) throws Exception {
        return getAndStartNonRepoBuild(null, pipelineScriptFile);
    }

    protected WorkflowRun getAndStartNonRepoBuild(Folder folder, String pipelineScriptFile) throws Exception {
        WorkflowJob p = createWorkflowJob(folder);
        p.setDefinition(new CpsFlowDefinition(pipelineSourceFromResources(pipelineScriptFile), true));
        return p.scheduleBuild2(0).waitForStart();
    }

    private WorkflowJob createWorkflowJob(Folder folder) throws IOException {
        if (folder == null) {
            return j.createProject(WorkflowJob.class);
        } else {
            return folder.createProject(WorkflowJob.class, "test" + (folder.getItems().size() + 1));
        }
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
        FileUtils.writeStringToFile(new File(vars, "returnAThing.groovy"), StringUtils.join(Arrays.asList(
                "def call(a) { return \"${a} tada\" }"), "\n"
        ));
        FileUtils.writeStringToFile(new File(vars, "acmeFunc.groovy"), StringUtils.join(Arrays.asList(
                "def call(a,b) { echo \"call($a,$b)\" }")
                , "\n"));
        FileUtils.writeStringToFile(new File(vars, "acmeFuncClosure1.groovy"), StringUtils.join(Arrays.asList(
                "def call(a, Closure body) { echo \"closure1($a)\"; body() }")
                , "\n"));
        FileUtils.writeStringToFile(new File(vars, "acmeFuncClosure2.groovy"), StringUtils.join(Arrays.asList(
                "def call(a, b, Closure body) { echo \"closure2($a, $b)\"; body() }")
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


    protected <T extends ParameterDefinition> T getParameterOfType(List<ParameterDefinition> params, Class<T> c) {
        for (ParameterDefinition p : params) {
            if (c.isInstance(p)) {
                return (T)p;
            }
        }
        return null;
    }

    protected EnvBuilder env(Slave s) {
        return new EnvBuilder(s);
    }

    protected ExpectationsBuilder expect(String resource) {
        return expect((String)null, resource);
    }

    protected ExpectationsBuilder expect(Result result, String resource) {
        return expect(result, null, resource);
    }

    protected ExpectationsBuilder expect(String resourceParent, String resource) {
        return new ExpectationsBuilder(resourceParent, resource);
    }

    protected ExpectationsBuilder expectError(String resource) {
        return expect(Result.FAILURE, "errors", resource);
    }

    protected ExpectationsBuilder expect(Result result, String resourceParent, String resource) {
        return new ExpectationsBuilder(result, resourceParent, resource);
    }

    public class ExpectationsBuilder {
        private Result result = Result.SUCCESS;
        private final String resourceParent;
        private String resource;
        private List<String> logContains;
        private List<String> logNotContains;
        private WorkflowRun run;
        private boolean runFromRepo = true;
        private Folder folder; //We use the real stuff here, no mocking fluff
        private boolean hasFailureCause;
        private List<String> inLogInOrder;
        private List<Matcher<Run>> buildMatchers;

        private ExpectationsBuilder(String resourceParent, String resource) {
            this(Result.SUCCESS, resourceParent, resource);
        }

        private ExpectationsBuilder(Result result, String resourceParent, String resource) {
            this.result = result;
            this.resourceParent = resourceParent;
            this.resource = resource;
            buildMatchers = new ArrayList<>();
        }

        public ExpectationsBuilder inLogInOrder(String... msgsInOrder) {
            this.inLogInOrder = Arrays.asList(msgsInOrder);
            return this;
        }

        public ExpectationsBuilder runFromRepo(boolean mode) {
            runFromRepo = mode;
            return this;
        }

        public ExpectationsBuilder inFolder(Folder folder) {
            this.folder = folder;
            return this;
        }

        public ExpectationsBuilder logContains(String... logEntries) {
            if (this.logContains != null) {
                logContains.addAll(Arrays.asList(logEntries));
            } else {
                this.logContains = new ArrayList<>(Arrays.asList(logEntries));
            }
            return this;
        }

        public ExpectationsBuilder logNotContains(String... logEntries) {
            if (this.logNotContains != null) {
                this.logNotContains.addAll(Arrays.asList(logEntries));
            } else {
                this.logNotContains = new ArrayList<>(Arrays.asList(logEntries));
            }
            return this;
        }

        public ExpectationsBuilder hasFailureCase() {
            this.hasFailureCause = true;
            return this;
        }

        public ExpectationsBuilder buildMatches(Matcher<Run>... matchers) {
            buildMatchers.addAll(Arrays.asList(matchers));
            return this;
        }

        public ExpectationsBuilder archives(Matcher<String> fileName, Matcher<String> content, Charset encoding) {
            return buildMatches(HasArchived.hasArchivedString(fileName, content, encoding));
        }

        public ExpectationsBuilder archives(Matcher<String> fileName, Matcher<String> content) {
            return buildMatches(HasArchived.hasArchivedString(fileName, content));
        }

        public ExpectationsBuilder archives(String fileName, String content) {
            return buildMatches(HasArchived.hasArchivedString(equalTo(fileName), equalToIgnoringWhiteSpace(content)));
        }

        public ExpectationsBuilder archives(String fileName, Matcher<String> content) {
            return buildMatches(HasArchived.hasArchivedString(equalTo(fileName), content));
        }

        public WorkflowRun go() throws Exception {
            String resourceFullName = resource;
            if (resourceParent != null) {
                resourceFullName = resourceParent + "/" + resource;
            }

            if (run == null) {
                if (runFromRepo) {
                    prepRepoWithJenkinsfile(resourceFullName);
                    run = getAndStartBuild(folder);
                } else {
                    run = getAndStartNonRepoBuild(folder, resourceFullName);
                }
            } else {
                run = run.getParent().scheduleBuild2(0).waitForStart();
            }
            j.assertBuildStatus(result, j.waitForCompletion(run));

            if (logContains != null) {
                for (String entry : logContains) {
                    j.assertLogContains(entry, run);
                }
            }
            if (logNotContains != null) {
                for (String logNotContain : logNotContains) {
                    j.assertLogNotContains(logNotContain, run);
                }
            }
            if (hasFailureCause) {
                assertNotNull(run.getExecution().getCauseOfFailure());
            }
            if (inLogInOrder != null && !inLogInOrder.isEmpty()) {
                String buildLog = JenkinsRule.getLog(run);
                assertThat(buildLog, stringContainsInOrder(inLogInOrder));
            }

            for (Matcher<Run> matcher : buildMatchers) {
                assertThat(run, matcher);
            }
            return run;
        }

        public ExpectationsBuilder resetForNewRun(Result result) {
            this.result = result;
            resource = null;
            logContains = null;
            logNotContains = null;
            buildMatchers = new ArrayList<>();
            return this;
        }
    }

    public class EnvBuilder {
        private final Slave agent;
        private Map<String, String> env;

        protected EnvBuilder(Slave agent) {
            this.agent = agent;
            this.env = new HashMap<>();
            env.put("ONSLAVE", "true");
        }

        public EnvBuilder put(String key, String value) {
            env.put(key, value);
            return this;
        }

        public void set() throws IOException {
            List<EnvironmentVariablesNodeProperty.Entry> entries = new ArrayList<>(env.size());
            for (Map.Entry<String, String> entry : env.entrySet()) {
                entries.add(new EnvironmentVariablesNodeProperty.Entry(entry.getKey(), entry.getValue()));
            }
            EnvironmentVariablesNodeProperty newProperty = new EnvironmentVariablesNodeProperty(entries);
            DescribableList<NodeProperty<?>, NodePropertyDescriptor> nodeProperties = agent.getNodeProperties();
            nodeProperties.replace(newProperty);
        }

    }
}
