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

import hudson.model.Result;
import hudson.model.Slave;
import hudson.slaves.EnvironmentVariablesNodeProperty;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author Andrew Bayer
 */
public class AgentTest extends AbstractModelDefTest {

    private static Slave s;
    private static Slave s2;

    @BeforeClass
    public static void setUpAgent() throws Exception {
        s = j.createOnlineSlave();
        s.setLabelString("some-label docker");
        s.getNodeProperties().add(new EnvironmentVariablesNodeProperty(new EnvironmentVariablesNodeProperty.Entry("ONAGENT", "true"),
                new EnvironmentVariablesNodeProperty.Entry("WHICH_AGENT", "first")));
        s.setNumExecutors(2);

        s2 = j.createOnlineSlave();
        s2.setLabelString("other-docker");
        s2.getNodeProperties().add(new EnvironmentVariablesNodeProperty(new EnvironmentVariablesNodeProperty.Entry("ONAGENT", "true"),
                new EnvironmentVariablesNodeProperty.Entry("WHICH_AGENT", "second")));
    }

    @Test
    public void agentLabel() throws Exception {
        expect("agentLabel")
                .logContains("[Pipeline] { (foo)", "ONAGENT=true")
                .go();
    }

    @Issue("JENKINS-37932")
    @Test
    public void agentAny() throws Exception {
        expect("agentAny")
                .logContains("[Pipeline] { (foo)", "THIS WORKS")
                .go();
    }

    @Test
    public void noCheckoutScmInWrongContext() throws Exception {
        expect("noCheckoutScmInWrongContext")
                .runFromRepo(false)
                .logContains("[Pipeline] { (foo)", "ONAGENT=true")
                .go();
    }

    @Test
    public void agentDocker() throws Exception {
        agentDocker("agentDocker", "-v /tmp:/tmp");
    }

    @Test
    public void agentDockerReuseNode() throws Exception {
        agentDocker("agentDockerReuseNode");
    }

    @Issue("JENKINS-49558")
    @Test
    public void agentDockerContainerPerStage() throws Exception {
        agentDocker("agentDockerContainerPerStage");
    }

    @Issue("JENKINS-49558")
    @Test
    public void agentDockerWithoutContainerPerStage() throws Exception {
        agentDocker("agentDockerWithoutContainerPerStage");
    }

    @Test
    public void agentDockerDontReuseNode() throws Exception {
        assumeDocker();

        expect(Result.FAILURE, "agentDockerDontReuseNode")
                .logContains("The answer is 42")
                .go();

    }

    @Issue("JENKINS-41605")
    @Test
    public void agentInStageAutoCheckout() throws Exception {
        assumeDocker();

        expect("agentInStageAutoCheckout")
                .logContains("The answer is 42",
                        "found tmp.txt in bar",
                        "did not find tmp.txt in new docker node",
                        "did not find tmp.txt in new label node")
                .go();

    }

    @Test
    public void agentDockerWithNullDockerArgs() throws Exception {
        agentDocker("agentDockerWithNullDockerArgs");
    }

    @Test
    public void agentDockerWithEmptyDockerArgs() throws Exception {
        agentDocker("agentDockerWithEmptyDockerArgs");
    }

    @Test
    public void agentNone() throws Exception {
        expect(Result.FAILURE, "agentNone")
                .logContains(Messages.ModelInterpreter_NoNodeContext(),
                        "Perhaps you forgot to surround the code with a step that provides this, such as: node")
                .go();
    }

    @Test
    public void agentNoneWithNode() throws Exception {
        expect("agentNoneWithNode")
                .logContains("[Pipeline] { (foo)", "ONAGENT=true")
                .go();
    }

    @Test
    public void perStageConfigAgent() throws Exception {
        expect("perStageConfigAgent")
                .logContains("[Pipeline] { (foo)", "ONAGENT=true")
                .go();
    }

    @Test
    public void multipleVariablesForAgent() throws Exception {
        expect("multipleVariablesForAgent")
                .logContains("[Pipeline] { (foo)",
                        "ONAGENT=true",
                        "Running in labelAndOtherField with otherField = banana",
                        "And nested: foo: monkey, bar: false")
                .go();
    }

    @Test
    public void agentAnyInStage() throws Exception {
        expect("agentAnyInStage")
                .logContains("[Pipeline] { (foo)", "THIS WORKS")
                .go();
    }

    @Issue("JENKINS-41950")
    @Test
    public void nonExistentDockerImage() throws Exception {
        assumeDocker();

        expect(Result.FAILURE, "nonExistentDockerImage")
                .logContains("ERROR: script returned exit code 1",
                        "There is no image")
                .go();
    }


    @Test
    public void fromDockerfile() throws Exception {
        assumeDocker();

        sampleRepo.write("Dockerfile", "FROM ubuntu:14.04\n\nRUN echo 'HI THERE' > /hi-there\n\n");
        sampleRepo.git("init");
        sampleRepo.git("add", "Dockerfile");
        sampleRepo.git("commit", "--message=Dockerfile");

        expect("fromDockerfile")
                .logContains("[Pipeline] { (foo)",
                        "The answer is 42",
                        "-v /tmp:/tmp",
                        "HI THERE")
                .go();
    }

    @Test
    public void additionalDockerBuildArgs() throws Exception {
        assumeDocker();

        sampleRepo.write("Dockerfile", "FROM ubuntu:14.04\n\nARG someArg=thisArgHere\n\nRUN echo \"hi there, $someArg\" > /hi-there\n\n");
        sampleRepo.git("init");
        sampleRepo.git("add", "Dockerfile");
        sampleRepo.git("commit", "--message=Dockerfile");

        expect("additionalDockerBuildArgs")
                .logContains("[Pipeline] { (foo)",
                        "The answer is 42",
                        "-v /tmp:/tmp",
                        "hi there, thisOtherArg")
                .logNotContains("hi there, thisArgHere")
                .go();
    }

    @Issue("JENKINS-41668")
    @Test
    public void fromDockerfileInOtherDir() throws Exception {
        assumeDocker();

        sampleRepo.write("subdir/Dockerfile", "FROM ubuntu:14.04\n\nRUN echo 'HI THERE' > /hi-there\n\n");
        sampleRepo.git("init");
        sampleRepo.git("add", "subdir/Dockerfile");
        sampleRepo.git("commit", "--message=Dockerfile");

        expect("fromDockerfileInOtherDir")
                .logContains("[Pipeline] { (foo)",
                        "The answer is 42",
                        "-v /tmp:/tmp",
                        "HI THERE")
                .go();
    }

    @Issue("JENKINS-42286")
    @Test
    public void dirSepInDockerfileName() throws Exception {
        assumeDocker();

        sampleRepo.write("subdir/Dockerfile", "FROM ubuntu:14.04\n\nRUN echo 'HI THERE' > /hi-there\n\n");
        sampleRepo.git("init");
        sampleRepo.git("add", "subdir/Dockerfile");
        sampleRepo.git("commit", "--message=Dockerfile");

        expect("fromDockerfileInOtherDir")
                .logContains("[Pipeline] { (foo)",
                        "The answer is 42",
                        "-v /tmp:/tmp",
                        "HI THERE")
                .go();
    }

    @Test
    public void fromDockerfileNoArgs() throws Exception {
        assumeDocker();

        sampleRepo.write("Dockerfile", "FROM ubuntu:14.04\n\nRUN echo 'HI THERE' > /hi-there\n\n");
        sampleRepo.git("init");
        sampleRepo.git("add", "Dockerfile");
        sampleRepo.git("commit", "--message=Dockerfile");

        expect("fromDockerfileNoArgs")
                .logContains("[Pipeline] { (foo)",
                        "The answer is 42",
                        "HI THERE")
                .go();
    }

    @Test
    public void fromAlternateDockerfile() throws Exception {
        assumeDocker();
        sampleRepo.write("Dockerfile.alternate", "FROM ubuntu:14.04\n\nRUN echo 'HI THERE' > /hi-there\n\n");
        sampleRepo.git("init");
        sampleRepo.git("add", "Dockerfile.alternate");
        sampleRepo.git("commit", "--message=Dockerfile");

        expect("fromAlternateDockerfile")
                .logContains("[Pipeline] { (foo)",
                        "The answer is 42",
                        "-v /tmp:/tmp",
                        "HI THERE")
                .go();
    }

    @Ignore("Still not sure yet whether we'll ever fix JENKINS-43911, but wanted to have a test here for if we do")
    @Issue("JENKINS-43911")
    @Test
    public void agentFromEnv() throws Exception {
        expect("agentFromEnv")
                .logContains("WHICH_AGENT=first",
                        "WHICH_AGENT=second")
                .go();
    }

    @Ignore("Until JENKINS-46831 is addressed")
    @Issue("JENKINS-46831")
    @Test
    public void agentDockerGlobalThenLabel() throws Exception {
        expect("agentDockerGlobalThenLabel")
            .logContains(
                "first agent = first",
                "second agent = second"
            )
            .go();
    }

    @Issue("JENKINS-47106")
    @Test
    public void dockerPullLocalImage() throws Exception {
        assumeDocker();

        sampleRepo.write("Dockerfile", "FROM ubuntu:14.04\n\nRUN echo 'HI THERE' > /hi-there\n\n");
        sampleRepo.git("init");
        sampleRepo.git("add", "Dockerfile");
        sampleRepo.git("commit", "--message=Dockerfile");

        expect("dockerPullLocalImage")
                .logContains("[Pipeline] { (in built image)",
                        "The answer is 42",
                        "-v /tmp:/tmp",
                        "HI THERE",
                        "Maven home: /usr/share/maven")
                .go();
    }

    @Issue("JENKINS-46809")
    @Test
    public void agentOnGroup() throws Exception {
        expect("agentOnGroup")
                .logContains("Solo stage agent: first",
                        "First other stage agent: second",
                        "Second other stage agent: first")
                .go();
    }

    private void agentDocker(final String jenkinsfile, String... additionalLogContains) throws Exception {
        assumeDocker();

        List<String> logContains = new ArrayList<>();
        logContains.add("[Pipeline] { (foo)");
        logContains.add("The answer is 42");
        logContains.addAll(Arrays.asList(additionalLogContains));

        expect(jenkinsfile)
                .logContains(logContains.toArray(new String[logContains.size()]))
                .go();
    }
}
