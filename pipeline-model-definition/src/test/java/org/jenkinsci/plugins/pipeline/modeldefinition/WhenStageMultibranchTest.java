/*
 * The MIT License
 *
 * Copyright (c) 2018, CloudBees, Inc.
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

import hudson.model.Slave;
import jenkins.branch.BranchSource;
import jenkins.scm.impl.mock.MockChangeRequestFlags;
import jenkins.scm.impl.mock.MockRepositoryFlags;
import jenkins.scm.impl.mock.MockSCMController;
import jenkins.scm.impl.mock.MockSCMDiscoverBranches;
import jenkins.scm.impl.mock.MockSCMDiscoverChangeRequests;
import jenkins.scm.impl.mock.MockSCMDiscoverTags;
import jenkins.scm.impl.mock.MockSCMSource;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject;
import org.junit.BeforeClass;
import org.junit.Test;
import org.jvnet.hudson.test.TestExtension;

import java.io.IOException;
import java.util.concurrent.ExecutionException;

import static org.hamcrest.collection.IsCollectionWithSize.hasSize;
import static org.hamcrest.collection.IsEmptyCollection.empty;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.hamcrest.core.IsNot.not;
import static org.jenkinsci.plugins.pipeline.modeldefinition.WhenStageTest.waitFor;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;

public class WhenStageMultibranchTest extends AbstractModelDefTest {

    private static Slave s;

    @BeforeClass
    public static void setUpAgent() throws Exception {
        s = j.createOnlineSlave();
        s.setLabelString("here");
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
    public void whenTag() throws Exception {
        MockSCMController controller = MockSCMController.create();
        controller.createRepository("repoX");
        controller.createBranch("repoX", "master");

        controller.addFile("repoX", "master", "Jenkinsfile", "Jenkinsfile", pipelineSourceFromResources("when/whenTag").getBytes());

        WorkflowMultiBranchProject project = j.createProject(WorkflowMultiBranchProject.class);
        project.getSourcesList().add(new BranchSource(new MockSCMSource(controller, "repoX", new MockSCMDiscoverTags(), new MockSCMDiscoverBranches())));

        waitFor(project.scheduleBuild2(0));
        j.waitUntilNoActivity();
        assertThat(project.getItems(), hasSize(1)); //Just tests the multibranch is correctly configured

        //controller.addFile("repoX", "master", "Mock file for random tag", "some-random-file.txt", "Hello".getBytes());
        controller.createTag("repoX", "master", "some-random-tag");


        waitFor(project.scheduleBuild2(0));
        j.waitUntilNoActivity();
        assertThat(project.getItems(), hasSize(2)); //Just tests the multibranch is correctly configured

        WorkflowJob tagJob = project.getItem("some-random-tag");
        assertNotNull(tagJob);
        WorkflowRun build = tagJob.getLastBuild();
        if (build == null) {
            //This seems to happen for unknown reason; the tag is discovered but no build produced.
            j.assertBuildStatusSuccess(tagJob.scheduleBuild2(0));
            build = tagJob.getLastBuild();
        }
        j.assertBuildStatusSuccess(build);
        j.assertLogContains("Hello", build);
        j.assertLogContains("World", build);
        j.assertLogNotContains("release it", build);

        controller.createTag("repoX", "master", "release-1");

        waitFor(project.scheduleBuild2(0));
        j.waitUntilNoActivity();
        assertThat(project.getItems(), hasSize(3)); //Just tests the multibranch is correctly configured

        tagJob = project.getItem("release-1");
        assertNotNull(tagJob);
        build = tagJob.getLastBuild();
        if (build == null) {
            //This seems to happen for unknown reason; the tag is discovered but no build produced.
            j.assertBuildStatusSuccess(tagJob.scheduleBuild2(0));
            build = tagJob.getLastBuild();
        }
        j.assertBuildStatusSuccess(build);
        j.assertLogContains("Hello", build);
        j.assertLogContains("World", build);
        j.assertLogContains("release it", build);
        j.assertLogContains("Digit release", build);

        controller.createTag("repoX", "master", "release-master");

        waitFor(project.scheduleBuild2(0));
        j.waitUntilNoActivity();
        assertThat(project.getItems(), hasSize(4)); //Just tests the multibranch is correctly configured

        tagJob = project.getItem("release-master");
        assertNotNull(tagJob);
        build = tagJob.getLastBuild();
        if (build == null) {
            //This seems to happen for unknown reason; the tag is discovered but no build produced.
            j.assertBuildStatusSuccess(tagJob.scheduleBuild2(0));
            build = tagJob.getLastBuild();
        }
        j.assertBuildStatusSuccess(build);
        j.assertLogContains("release it", build);
        j.assertLogNotContains("Digit release", build);
    }

    @Test
    public void whenChangeRequest() throws Exception {
        MockSCMController controller = MockSCMController.create();
        controller.createRepository("repo", MockRepositoryFlags.FORKABLE);
        controller.createBranch("repo", "master");
        controller.addFile("repo", "master", "Jenkinsfile", "Jenkinsfile", pipelineSourceFromResources("when/whenChangeRequest").getBytes());
        int id = controller.openChangeRequest("repo", "master");
        controller.addFile("repo", "change-request/" + id, "mbopalua", "cr" + id + ".txt", "hello".getBytes());
        controller.cloneBranch("repo", "master", "release-2");
        controller.addFile("repo", "release-2", "second release", "rel.txt", "rel".getBytes());
        id = controller.openChangeRequest("repo", "release-2", MockChangeRequestFlags.FORK);
        controller.addFile("repo", "change-request/" + id, "change release notes", "cr" + id + ".txt", "hello".getBytes());
        //controller.setUrl("repo", "http://hax0r.com/repo/"); //TODO jenkins.scm.impl.mock.MockSCMController.Repository.url is not used in MockSCMController (yet)

        WorkflowMultiBranchProject project = j.createProject(WorkflowMultiBranchProject.class);
        project.getSourcesList().add(new BranchSource(new MockSCMSource(controller, "repo", new MockSCMDiscoverBranches(), new MockSCMDiscoverChangeRequests())));

        waitFor(project.scheduleBuild2(0));
        j.waitUntilNoActivity();

        assertThat(project.getItems(), hasSize(4)); //Just tests the multibranch is correctly configured
        final WorkflowJob master = project.getItem("master");
        WorkflowRun build = master.getLastBuild();
        j.assertBuildStatusSuccess(build);
        j.assertLogContains("Stage \"IsChange\" skipped due to when conditional", build);
        j.assertLogNotContains("World", build);
        j.assertLogNotContains("From CR branch", build);
        j.assertLogNotContains("From urlX example", build);
        j.assertLogNotContains("Author is a cool guy", build);
        j.assertLogNotContains("Author displays coolness", build);
        j.assertLogNotContains("Author has a cool job", build);
        j.assertLogNotContains("Author is probably a robot", build);

        WorkflowJob crJob = project.getItem("CR-1");
        assertNotNull(crJob);
        WorkflowRun run = crJob.getLastBuild();
        assertNotNull(run);
        j.assertBuildStatusSuccess(run);
        j.assertLogContains("Hello", run);
        j.assertLogContains("World", run);
        j.assertLogContains("From CR branch", run);
        j.assertLogContains("From urlX example", run);
        j.assertLogContains("Author is a cool guy", run);
        j.assertLogContains("Author displays coolness", run);
        j.assertLogContains("Author has a cool job", run);
        j.assertLogContains("Author is probably a robot", run);
        j.assertLogContains("AuthorX is nice", run);
        j.assertLogContains("AuthorX displays coolness", run);

        //controller.closeChangeRequest("repo", id);


        crJob = project.getItem("CR-2");
        assertNotNull(crJob);
        run = crJob.getLastBuild();
        assertNotNull(run);
        j.assertBuildStatusSuccess(run);
        j.assertLogContains("Hello", run);
        j.assertLogContains("World", run);
        j.assertLogContains("Target release", run);
        j.assertLogContains("From origin fork", run);

        j.assertLogNotContains("Id is in the tens", run);
        j.assertLogNotContains("We are in the tens", run);
        //TODO validate title whenever the mock API allows

        while (id < 10) {
            id = controller.openChangeRequest("repo", "master");
        }

        waitFor(project.scheduleBuild2(0));
        j.waitUntilNoActivity();

        crJob = project.getItem("CR-10");
        assertNotNull(crJob);
        run = crJob.getLastBuild();
        assertNotNull(run);
        j.assertBuildStatusSuccess(run);

        j.assertLogContains("Id is in the tens", run);
        j.assertLogContains("We are in the tens", run);

    }


    @TestExtension
    public static class TestChangeLogStrategy extends WhenStageTest.TestChangeLogStrategy {

    }

    @TestExtension
    public static class WhenConditionPickleFactory extends WhenStageTest.WhenConditionPickleFactory {

    }
}
