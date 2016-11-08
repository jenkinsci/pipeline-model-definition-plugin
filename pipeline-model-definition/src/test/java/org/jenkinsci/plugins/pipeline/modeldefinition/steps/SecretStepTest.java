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

package org.jenkinsci.plugins.pipeline.modeldefinition.steps;

import hudson.slaves.DumbSlave;
import hudson.slaves.EnvironmentVariablesNodeProperty;
import org.hamcrest.core.StringContains;
import org.jenkinsci.plugins.pipeline.modeldefinition.AbstractModelDefTest;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.core.StringContains.containsString;
import static org.jenkinsci.plugins.pipeline.modeldefinition.steps.SecretStep.DescriptorImpl.decrypt;
import static org.jenkinsci.plugins.pipeline.modeldefinition.steps.SecretStep.DescriptorImpl.encrypt;
import static org.junit.Assert.*;

/**
 * Tests for {@link SecretStep}.
 */
public class SecretStepTest extends AbstractModelDefTest {

    @Test
    public void decryptEncrypt() throws Exception {
        String expected = "Hello World";
        String s = encrypt(expected);
        assertEquals(expected, decrypt(s));
    }

    @Test
    public void consecutiveEncryptionNotEqual() throws Exception {
        String plain = "Hello World";
        String s1 = encrypt(plain);
        String s2 = encrypt(plain);
        String s3 = encrypt(plain);

        assertNotEquals(s1, s2);
        assertNotEquals(s1, s3);
        assertNotEquals(s2, s3);

        assertEquals(decrypt(s1), decrypt(s2));
        assertEquals(decrypt(s1), decrypt(s3));
        assertEquals(decrypt(s2), decrypt(s3));
    }

    @Test
    public void worksInVanillaPipelineWithEnv() throws Exception {
        String plainText = "Bobby`s lil secret";
        String script = "node {\n" +
                "    withEnv([\"FOO=${secret('"+encrypt(plainText)+"')}\"]) {\n" +
                "        sh 'echo \"FOO is $FOO\"'\n" +
                "    }\n" +
                "}\n";
        assertThat(script, not(containsString(plainText)));
        prepRepoWithJenkinsfileFromString(script);
        WorkflowRun build = getAndStartBuild();
        j.assertBuildStatusSuccess(j.waitForCompletion(build));
        j.assertLogContains("FOO is " + plainText, build);
    }

    @Test
    public void worksInEnvironment() throws Exception {
        DumbSlave s = j.createOnlineSlave();
        s.setLabelString("some-label docker");
        s.getNodeProperties().add(new EnvironmentVariablesNodeProperty(new EnvironmentVariablesNodeProperty.Entry("ONSLAVE", "true")));

        String plainText = "Bobby`s lil secret";
        String script = "pipeline {\n" +
                "    environment {\n" +
                "        FOO = secret('"+encrypt(plainText)+"')\n" +
                "    }\n" +
                "\n" +
                "    agent label:\"some-label\"\n" +
                "\n" +
                "    stages {\n" +
                "        stage(\"foo\") {\n" +
                "            steps {\n" +
                "                sh 'echo \"FOO is $FOO\"'\n" +
                "            }\n" +
                "        }\n" +
                "    }\n" +
                "}";
        assertThat(script, not(containsString(plainText)));
        prepRepoWithJenkinsfileFromString(script);
        WorkflowRun build = getAndStartBuild();
        j.assertBuildStatusSuccess(j.waitForCompletion(build));
        j.assertLogContains("FOO is " + plainText, build);
    }
}