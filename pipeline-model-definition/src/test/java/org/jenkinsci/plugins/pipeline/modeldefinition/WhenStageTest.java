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
 *
 */

package org.jenkinsci.plugins.pipeline.modeldefinition;

import com.gargoylesoftware.htmlunit.HttpMethod;
import com.gargoylesoftware.htmlunit.WebRequest;
import com.gargoylesoftware.htmlunit.util.NameValuePair;
import hudson.model.Queue;
import hudson.model.Result;
import hudson.model.Slave;
import jenkins.branch.BranchSource;
import jenkins.scm.api.SCMHead;
import jenkins.scm.impl.mock.MockSCMController;
import jenkins.scm.impl.mock.MockSCMDiscoverChangeRequests;
import jenkins.scm.impl.mock.MockSCMSource;
import net.sf.json.JSONObject;
import org.jenkinsci.plugins.pipeline.modeldefinition.endpoints.ModelConverterAction;
import org.jenkinsci.plugins.pipeline.modeldefinition.model.Stage;
import org.jenkinsci.plugins.pipeline.modeldefinition.when.ChangeLogStrategy;
import org.jenkinsci.plugins.pipeline.modeldefinition.when.DeclarativeStageConditional;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject;
import org.jenkinsci.plugins.workflow.pickles.Pickle;
import org.jenkinsci.plugins.workflow.support.pickles.SingleTypedPickleFactory;
import org.jenkinsci.plugins.workflow.support.pickles.XStreamPickle;
import org.junit.BeforeClass;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.net.URL;
import java.util.Collections;
import java.util.concurrent.ExecutionException;

import static org.hamcrest.collection.IsEmptyCollection.empty;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.hamcrest.core.IsNot.not;
import static org.jenkinsci.plugins.pipeline.modeldefinition.util.IsJsonObjectContaining.hasEntry;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;


/**
 * Tests {@link Stage#when}
 */
public class WhenStageTest extends AbstractModelDefTest {

    private static Slave s;

    @BeforeClass
    public static void setUpAgent() throws Exception {
        s = j.createOnlineSlave();
        s.setLabelString("here");
    }

    @Test
    public void simpleWhen() throws Exception {
        env(s).put("SECOND_STAGE", "NOPE").set();
        ExpectationsBuilder expect = expect("when", "simpleWhen").runFromRepo(false);
        expect.logContains("One", "Hello", "Should I run?", "Two").logNotContains("World").go();
        env(s).put("SECOND_STAGE", "RUN").set();
        expect.resetForNewRun(Result.SUCCESS).logContains("One", "Hello", "Should I run?", "Two", "World").go();
    }

    @Test
    public void whenException() throws Exception {
        env(s).put("SECOND_STAGE", "NOPE").set();
        expect(Result.FAILURE, "when", "whenException").runFromRepo(false)
                .logContains("One", "Hello", "Should I run?", "NullPointerException", "Two")
                .logNotContains("World").go();
    }

    @Test
    public void whenEmpty() throws Exception {
        env(s).put("SECOND_STAGE", "NOPE").set();
        expect(Result.FAILURE, "when", "whenEmpty").runFromRepo(false)
                .logContains(Messages.ModelValidatorImpl_EmptyWhen()).logNotContains("Two", "World").go();
    }
    
    @Test
    public void whenAllOfEmpty() throws Exception {
        ExpectationsBuilder expect = expect(Result.FAILURE, "when", "allOfEmpty").runFromRepo(false);
        expect.logContains(Messages.ModelValidatorImpl_NestedWhenWithoutChildren("allOf")).logNotContains("Hello", "World").go();
    }

    @Test
    public void toJson() throws IOException {
        final String rawJenkinsfile = fileContentsFromResources("when/simpleWhen.groovy", true);
        JenkinsRule.WebClient wc = j.createWebClient();
        WebRequest req = new WebRequest(new URL(wc.getContextPath() + ModelConverterAction.PIPELINE_CONVERTER_URL + "/toJson"), HttpMethod.POST);

        assertNotNull(rawJenkinsfile);

        NameValuePair pair = new NameValuePair("jenkinsfile", rawJenkinsfile);
        req.setRequestParameters(Collections.singletonList(pair));

        String rawResult = wc.getPage(req).getWebResponse().getContentAsString();
        assertNotNull(rawResult);

        JSONObject result = JSONObject.fromObject(rawResult);
        assertNotNull(result);
        assertThat(result, hasEntry("status", "ok"));
        assertThat(result, hasEntry("data", hasEntry("result", "success")));

        req = new WebRequest(new URL(wc.getContextPath() + ModelConverterAction.PIPELINE_CONVERTER_URL + "/toJenkinsfile"), HttpMethod.POST);
        pair = new NameValuePair("json", result.getJSONObject("data").getJSONObject("json").toString());
        req.setRequestParameters(Collections.singletonList(pair));

        rawResult = wc.getPage(req).getWebResponse().getContentAsString();
        assertNotNull(rawResult);
        result = JSONObject.fromObject(rawResult);
        assertThat(result, hasEntry("status", "ok"));
        assertThat(result, hasEntry("data", hasEntry("result", "success")));
    }

    @Issue("JENKINS-43143")
    @Test
    public void paramsInWhenExpression() throws Exception {
        expect("paramsInWhenExpression")
                .logContains("[Pipeline] { (One)", "[Pipeline] { (Two)", "World")
                .go();
    }

    @Test
    public void whenChangeset() throws Exception {
        //TODO JENKINS-46086 First time build always skips the changelog
        final ExpectationsBuilder builder = expect("when/changelog", "changeset")
                .logContains("Hello", "Stage \"Two\" skipped due to when conditional", "Warning, empty changelog. Probably because this is the first build.")
                .logNotContains("JS World");
        builder.go();

        builder.resetForNewRun(Result.SUCCESS);

        sampleRepo.write("webapp/js/somecode.js", "//fake file");
        sampleRepo.git("add", "webapp/js/somecode.js");
        sampleRepo.git("commit", "--message=files");

        builder.logContains("Hello", "JS World")
                .logNotContains(
                        "Stage \"Two\" skipped due to when conditional",
                        "Warning, empty changelog.",
                        "Examining changelog from all builds of this change request.")
                .go();
    }

    @Test
    public void whenChangesetMoreCommits() throws Exception {
        //TODO JENKINS-46086 First time build always skips the changelog
        final ExpectationsBuilder builder = expect("when/changelog", "changeset")
                .logContains("Hello", "Stage \"Two\" skipped due to when conditional", "Warning, empty changelog. Probably because this is the first build.")
                .logNotContains("JS World");
        builder.go();

        builder.resetForNewRun(Result.SUCCESS);

        sampleRepo.write("somefile.txt", "//fake file");
        sampleRepo.git("add", "somefile.txt");
        sampleRepo.git("commit", "--message=Irrelevant");

        sampleRepo.write("webapp/js/somecode.js", "//fake file");
        sampleRepo.git("add", "webapp/js/somecode.js");
        sampleRepo.git("commit", "--message=files");
        sampleRepo.write("webapp/js/somecode.js", "//same file");
        sampleRepo.git("add", "webapp/js/somecode.js");
        sampleRepo.git("commit", "--message=same");

        sampleRepo.write("somefile2.txt", "//fake file");
        sampleRepo.git("add", "somefile2.txt");
        sampleRepo.git("commit", "--message=Irrelevant");

        builder.logContains("Hello", "JS World")
                .logNotContains(
                        "Stage \"Two\" skipped due to when conditional",
                        "Warning, empty changelog.",
                        "Examining changelog from all builds of this change request.")
                .go();
    }

    @Test
    public void whenChangesetPR() throws Exception {
        //TODO JENKINS-46086 First time build "always" skips the changelog when git, not when mock

        MockSCMController controller = MockSCMController.create();
        controller.createRepository("repoX");
        controller.createBranch("repoX", "master");
        final int num = controller.openChangeRequest("repoX", "master");
        final String crNum = "change-request/" + num;
        controller.addFile("repoX", crNum, "Jenkinsfile", "Jenkinsfile", pipelineSourceFromResources("when/changelog/changeset").getBytes());

        WorkflowMultiBranchProject project = j.createProject(WorkflowMultiBranchProject.class);
        project.getSourcesList().add(new BranchSource(new MockSCMSource(controller, "repoX", new MockSCMDiscoverChangeRequests())));

        waitFor(project.scheduleBuild2(0));
        j.waitUntilNoActivity();
        assertThat(project.getItems(), not(empty()));

        final WorkflowJob job = project.getItems().iterator().next();
        final WorkflowRun build = job.getLastBuild();
        assertNotNull(build);
        j.assertLogContains("Hello", build);
        j.assertLogContains("Stage \"Two\" skipped due to when conditional", build);
        j.assertLogNotContains("JS World", build);

        controller.addFile("repoX", crNum,
                "files",
                "webapp/js/somecode.js", "//fake file".getBytes());

        waitFor(project.scheduleBuild2(0));
        j.waitUntilNoActivity();
        final WorkflowRun build2 = job.getLastBuild();
        assertThat(build2, not(equalTo(build)));

        j.assertLogContains("Hello", build2);
        j.assertLogContains("JS World", build2);
        j.assertLogNotContains("Stage \"Two\" skipped due to when conditional", build2);
        j.assertLogNotContains("Warning, empty changelog", build2);

        controller.addFile("repoX", crNum,
                "file",
                "dontcare.txt", "empty".getBytes());

        waitFor(project.scheduleBuild2(0));
        j.waitUntilNoActivity();
        final WorkflowRun build3 = job.getLastBuild();
        assertThat(build3, not(equalTo(build2)));

        j.assertLogContains("Hello", build3);
        j.assertLogContains("JS World", build3);
        j.assertLogContains("Examining changelog from all builds of this change request", build3);
        j.assertLogNotContains("Stage \"Two\" skipped due to when conditional", build3);
        j.assertLogNotContains("Warning, empty changelog", build3);
    }

    @Test
    public void whenChangelog() throws Exception {
        //TODO JENKINS-46086 First time build always skips the changelog
        final ExpectationsBuilder builder = expect("when/changelog", "changelog")
                .logContains("Hello", "Stage \"Two\" skipped due to when conditional", "Warning, empty changelog. Probably because this is the first build.")
                .logNotContains("Dull World");
        builder.go();

        builder.resetForNewRun(Result.SUCCESS);

        sampleRepo.write("something.txt", "//fake file");
        sampleRepo.git("add", "something.txt");
        sampleRepo.git("commit", "-m", "Some title that we don't care about\n\nSome explanation\n[DEPENDENCY] some-app#45");

        builder.logContains("Hello", "Dull World")
                .logNotContains("Stage \"Two\" skipped due to when conditional", "Warning, empty changelog.")
                .go();
    }

    @Test
    public void whenChangelogMoreCommits() throws Exception {
        //TODO JENKINS-46086 First time build always skips the changelog
        final ExpectationsBuilder builder = expect("when/changelog", "changelog")
                .logContains("Hello", "Stage \"Two\" skipped due to when conditional", "Warning, empty changelog. Probably because this is the first build.")
                .logNotContains("Dull World");
        builder.go();

        builder.resetForNewRun(Result.SUCCESS);

        sampleRepo.write("something.txt", "//fake file");
        sampleRepo.git("add", "something.txt");
        sampleRepo.git("commit", "-m", "Irrelevant");

        sampleRepo.write("something.txt", "//slightly bigger fake file");
        sampleRepo.git("add", "something.txt");
        sampleRepo.git("commit", "-m", "Some title that we don't care about\n\nSome explanation\n[DEPENDENCY] some-app#45");

        sampleRepo.write("other.txt", "//fake file");
        sampleRepo.git("add", "other.txt");
        sampleRepo.git("commit", "-m", "You should not care");

        builder.logContains("Hello", "Dull World")
                .logNotContains("Stage \"Two\" skipped due to when conditional", "Warning, empty changelog.")
                .go();
    }

    @Test
    public void whenChangelogBadRegularExpression() throws Exception {
        expect(Result.FAILURE,"when/changelog", "badRegularExpression")
                .logContains("\"{\"user_id\" : 24}\" is not a valid regular expression.")
                .logNotContains("Hello,", "Dull World").go();
    }

    @Test
    public void whenChangelogPR() throws Exception {
        //TODO JENKINS-46086 First time build "always" skips the changelog when git, not when mock

        MockSCMController controller = MockSCMController.create();
        controller.createRepository("repoX");
        controller.createBranch("repoX", "master");
        final int num = controller.openChangeRequest("repoX", "master");
        final String crNum = "change-request/" + num;
        controller.addFile("repoX", crNum, "Jenkinsfile", "Jenkinsfile", pipelineSourceFromResources("when/changelog/changelog").getBytes());

        WorkflowMultiBranchProject project = j.createProject(WorkflowMultiBranchProject.class);
        project.getSourcesList().add(new BranchSource(new MockSCMSource(controller, "repoX", new MockSCMDiscoverChangeRequests())));

        waitFor(project.scheduleBuild2(0));
        j.waitUntilNoActivity();
        assertThat(project.getItems(), not(empty()));

        final WorkflowJob job = project.getItems().iterator().next();
        final WorkflowRun build = job.getLastBuild();
        assertNotNull(build);
        j.assertLogContains("Hello", build);
        j.assertLogContains("Stage \"Two\" skipped due to when conditional", build);
        j.assertLogNotContains("Dull World", build);

        controller.addFile("repoX", crNum,
                "Some title that we don't care about\n\nSome explanation\n[DEPENDENCY] some-app#45",
                "something.txt", "//fake file".getBytes());

        waitFor(project.scheduleBuild2(0));
        j.waitUntilNoActivity();
        final WorkflowRun build2 = job.getLastBuild();
        assertThat(build2, not(equalTo(build)));

        j.assertLogContains("Hello", build2);
        j.assertLogContains("Dull World", build2);
        j.assertLogNotContains("Stage \"Two\" skipped due to when conditional", build2);
        j.assertLogNotContains("Warning, empty changelog", build2);

        controller.addFile("repoX", crNum,
                "Some title",
                "something2.txt", "//fake file".getBytes());

        waitFor(project.scheduleBuild2(0));
        j.waitUntilNoActivity();
        final WorkflowRun build3 = job.getLastBuild();
        assertThat(build3, not(equalTo(build2)));

        j.assertLogContains("Hello", build3);
        j.assertLogContains("Dull World", build3);
        j.assertLogContains("Examining changelog from all builds of this change request", build3);
        j.assertLogNotContains("Stage \"Two\" skipped due to when conditional", build3);
        j.assertLogNotContains("Warning, empty changelog", build3);

    }

    @Issue("JENKINS-48209")
    @Test
    public void whenExprDurableTask() throws Exception {
        expect("whenExprDurableTask")
                .logContains("Heal it")
                .go();
    }

    @Issue("JENKINS-44461")
    @Test
    public void whenBeforeAgentTrue() throws Exception {
        expect("whenBeforeAgentTrue")
                .logContains("Heal it")
                .go();
    }

    @Issue("JENKINS-44461")
    @Test
    public void whenBeforeAgentFalse() throws Exception {
        expect("whenBeforeAgentFalse")
                .logContains("Heal it")
                .go();
    }

    @Issue("JENKINS-44461")
    @Test
    public void whenBeforeAgentUnspecified() throws Exception {
        expect("whenBeforeAgentUnspecified")
                .logContains("Heal it")
                .go();
    }

    @Issue("JENKINS-49226")
    @Test
    public void whenEquals() throws Exception {
        env(s).put("SECOND_STAGE", "NOPE").set();
        ExpectationsBuilder expect = expect("when", "whenEquals").runFromRepo(false);
        expect.logContains("One", "Hello", "Two").logNotContains("World").go();
        env(s).put("SECOND_STAGE", "RUN").set();
        expect.resetForNewRun(Result.SUCCESS).logContains("One", "Hello", "Two", "World").go();
    }

    @Issue("JENKINS-50815")
    @Test
    public void whenBranchNotMultibranch() throws Exception {
        expect("whenBranchNotMultibranch")
                .logContains("[Pipeline] { (One)", "[Pipeline] { (Two)", "World")
                .go();
    }

    @Issue("JENKINS-46809")
    @Test
    public void simpleGroupWhen() throws Exception {
        env(s).put("RUN_GROUP", "NOPE").set();
        ExpectationsBuilder expect = expect("when", "simpleGroupWhen").runFromRepo(false);
        expect.logContains("One", "Hello", "Should I run?", "Two").logNotContains("World").go();
        env(s).put("RUN_GROUP", "RUN").set();
        expect.resetForNewRun(Result.SUCCESS).logContains("One", "Hello", "Should I run?", "Two", "World").go();
    }

    public static void waitFor(Queue.Item item) throws InterruptedException, ExecutionException {
        while (item != null && item.getFuture() == null) {
            Thread.sleep(200);
        }
        assertNotNull(item);
        item.getFuture().waitForStart();
    }

    @TestExtension
    public static class TestChangeLogStrategy extends ChangeLogStrategy {
        //Implement in a similar way as DefaultChangeLogStrategy to be a bit more true to reality.
        private Class<?> mockPr;

        public TestChangeLogStrategy() {
            try {
                mockPr = Class.forName("jenkins.scm.impl.mock.MockChangeRequestSCMHead");
            } catch (ClassNotFoundException e) {
                mockPr = null;
            }
        }

        @Override
        protected boolean shouldExamineAllBuilds(@Nonnull SCMHead head) {
            if (mockPr != null && head.getClass().isAssignableFrom(mockPr)) {
                return true;
            }
            return false;
        }
    }

    @TestExtension
    public static class WhenConditionPickleFactory extends SingleTypedPickleFactory<DeclarativeStageConditional<?>> {
        @Override
        @Nonnull
        protected Pickle pickle(DeclarativeStageConditional<?> d) {
            return new XStreamPickle(d);
        }
    }

}
