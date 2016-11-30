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

import hudson.cli.CLICommandInvoker;
import hudson.security.GlobalMatrixAuthorizationStrategy;
import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;

import static hudson.cli.CLICommandInvoker.Matcher.failedWith;
import static hudson.cli.CLICommandInvoker.Matcher.hasNoErrorOutput;
import static hudson.cli.CLICommandInvoker.Matcher.succeeded;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertThat;

public class DeclarativeLinterCommandTest extends AbstractModelDefTest {

    private CLICommandInvoker command;

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Before
    public void setUp() {
        command = new CLICommandInvoker(j, "declarative-linter");
    }

    @Test
    public void validJenkinsfile() throws Exception {
        File testPath = writeJenkinsfileToTmpFile("simplePipeline");
        j.jenkins.disableSecurity();

        final CLICommandInvoker.Result result = command.withStdin(FileUtils.openInputStream(testPath)).invoke();

        assertThat(result, succeeded());
        assertThat(result, hasNoErrorOutput());
        assertThat(result.stdout(), containsString("Jenkinsfile successfully validated."));
    }

    @Test
    public void invalidJenkinsfile() throws Exception {
        File testPath = writeJenkinsfileToTmpFile("errors", "emptyAgent");
        j.jenkins.disableSecurity();

        final CLICommandInvoker.Result result = command.withStdin(FileUtils.openInputStream(testPath)).invoke();

        assertThat(result, failedWith(1));
        assertThat(result, hasNoErrorOutput());
        assertThat(result.stdout(), containsString("Errors encountered validating Jenkinsfile:"));
        assertThat(result.stdout(), containsString("Not a valid section definition: 'agent'. Some extra configuration is required"));
    }

    @Test
    public void invalidUser() throws Exception {
        File testPath = writeJenkinsfileToTmpFile("simplePipeline");

        j.jenkins.setAuthorizationStrategy(new GlobalMatrixAuthorizationStrategy());

        final CLICommandInvoker.Result result = command.withStdin(FileUtils.openInputStream(testPath)).invoke();

        assertThat(result, not(succeeded()));
        assertThat(result.stderr(), containsString("ERROR: anonymous is missing the Overall/Read permission"));
    }

    private File writeJenkinsfileToTmpFile(String dir, String testName) throws IOException {
        return writeJenkinsfileToTmpFile(dir + "/" + testName);
    }

    private File writeJenkinsfileToTmpFile(String testName) throws IOException {
        File jf = tmp.newFile();

        String contents = pipelineSourceFromResources(testName);

        FileUtils.writeStringToFile(jf, contents);

        return jf;
    }
}
