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

import hudson.model.Slave;
import hudson.slaves.EnvironmentVariablesNodeProperty;
import org.junit.BeforeClass;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;

import java.io.File;
import java.util.regex.Pattern;

/**
 * @author Andrew Bayer
 */
public class EnvironmentTest extends AbstractModelDefTest {

    private static Slave s;

    @BeforeClass
    public static void setUpAgent() throws Exception {
        s = j.createOnlineSlave();
        s.setLabelString("some-label docker");
        s.getNodeProperties().add(
                new EnvironmentVariablesNodeProperty(
                        new EnvironmentVariablesNodeProperty.Entry("HAS_BACKSLASHES", "C:\\Windows"),
                        new EnvironmentVariablesNodeProperty.Entry("FOO", "OTHER")));
    }

    @Test
    public void simpleEnvironment() throws Exception {
        expect("simpleEnvironment")
                .logContains("[Pipeline] { (foo)",
                        "FOO is BAR",
                        "_UNDERSCORE is VALID")
                .go();
    }

    @Issue("JENKINS-42082")
    @Test
    public void envVarContainsTEST() throws Exception {
        expect("envVarContainsTEST")
                .logContains("TEST_VAR is BAR",
                        "VAR_TEST is VALID",
                        "TEST_VAR from shell is BAR",
                        "VAR_TEST from shell is VALID")
                .go();
    }

    @Issue("JENKINS-43143")
    @Test
    public void paramsInEnvironment() throws Exception {
        expect("paramsInEnvironment")
                .logContains("[Pipeline] { (foo)",
                        "FOO is BAR",
                        "_UNDERSCORE is VALIDAValue")
                .go();
    }

    @Issue("JENKINS-43137")
    @Test
    public void multilineEnvironment() throws Exception {
        expect("multilineEnvironment")
                .logContains("[Pipeline] { (foo)",
                        "FOO is BAR",
                        "MULTILINE is VALID\n\"SO THERE\"")
                .go();
    }

    @Issue("JENKINS-42771")
    @Test
    public void multiExpressionEnvironment() throws Exception {
        expect("multiExpressionEnvironment")
                .logContains("[Pipeline] { (foo)",
                        "FOO is BAR",
                        "_UNDERSCORE is VALID")
                .go();
    }

    @Test
    public void environmentInStage() throws Exception {
        expect("environmentInStage")
                .logContains("[Pipeline] { (foo)", "FOO is BAR")
                .go();
    }

    @Issue("JENKINS-46809")
    @Test
    public void environmentInGroup() throws Exception {
        expect("environmentInGroup")
                .logContains("[Pipeline] { (foo)",
                        "Solo: FOO is BAZ",
                        "First in group: FOO is BAR",
                        "Second in group: FOO is BAH")
                .go();
    }

    @Issue("JENKINS-41748")
    @Test
    public void environmentCrossReferences() throws Exception {
        expect("environmentCrossReferences")
                .logContains("[Pipeline] { (foo)",
                        "FOO is FOO",
                        "BAR is FOOBAR",
                        "BAZ is FOOBAZ",
                        "SPLODE is banana")
                .go();
    }

    @Issue("JENKINS-43872")
    @Test
    public void envDollarQuotes() throws Exception {
        expect("envDollarQuotes")
                .logContains("[Pipeline] { (foo)",
                        "FOO is ${FOOTHAT}",
                        "BAR is ${FOOTHAT}BAR",
                        "BAZ is ${FOOTHAT}BAZ",
                        "SPLODE is banana")
                .go();
    }

    @Test
    public void envDotCrossRef() throws Exception {
        expect("envDotCrossRef")
                .logContains("[Pipeline] { (foo)",
                        "MICROSERVICE_NAME is directory",
                        "IMAGE_NAME is quay.io/svc/directory",
                        "IMAGE_ID is quay.io/svc/directory:master_1",
                        "TAG_NAME is master_1")
                .go();
    }

    @Issue("JENKINS-43404")
    @Test
    public void envQuotesInQuotes() throws Exception {
        expect("envQuotesInQuotes")
                .logContains("[Pipeline] { (foo)",
                        "GRADLE_OPTIONS is --no-daemon --rerun-tasks -PBUILD_NUMBER=1 -PBRANCH=\"master\"",
                        "MULTILINE_SINGLE is \nLook at me 'here'",
                        "MULTILINE_DOUBLE is \nThe branch name is \"master\"")
                .go();
    }

    @Issue("JENKINS-42748")
    @Test
    public void envBackslashes() throws Exception {
        expect("envBackslashes")
                .logContains("[Pipeline] { (foo)",
                        "echo SIMPLE_BACKSLASH is C:\\hey",
                        "echo NESTED_BACKSLASH is C:\\hey\\there",
                        "echo HAS_TAB is oh\they",
                        "echo NESTED_HAS_TAB is oh\they\tthere",
                        "shell SIMPLE_BACKSLASH is C:\\hey",
                        "shell NESTED_BACKSLASH is C:\\hey\\there",
                        "shell HAS_TAB is oh\they",
                        "shell NESTED_HAS_TAB is oh\they\tthere")
                .go();
    }

    @Issue("JENKINS-41890")
    @Test
    public void environmentWithWorkspace() throws Exception {
        expect("environmentWithWorkspace")
                .logContains("[Pipeline] { (foo)",
                        "FOO is FOO",
                        "BAZ is FOOBAZ")
                .logMatches("BAR is .*?workspace" + Pattern.quote(File.separator) + "test\\d+BAR")
                .go();
    }

    @Issue("JENKINS-42753")
    @Test
    public void stmtExprInEnvironment() throws Exception {
        expect("stmtExprInEnvironment")
                .logContains("FOO is BAR",
                        "LIST_EXP is [a, BAR, c]",
                        "MAP_EXP is [a:z, b:BAR, c:x]",
                        "BOOL_EXP is false",
                        "CTOR_EXP is http://BAR",
                        "CAST_EXP is [a, BAR, c]",
                        "PTR_EXP is true",
                        "AS_EXP is class java.util.LinkedHashSet",
                        "PREFIX_EXP is 1",
                        "POSTFIX_EXP is 0",
                        "RANGE_EXP is [0, 1, 2]")
                .go();
    }

    @Test
    public void nonLiteralEnvironment() throws Exception {
        initGlobalLibrary();

        expect("nonLiteralEnvironment")
                .logContains("[Pipeline] { (foo)",
                        "FOO is BAR",
                        "BUILD_NUM_ENV is 1",
                        "ANOTHER_ENV is 1",
                        "INHERITED_ENV is 1 is inherited",
                        "ACME_FUNC is banana tada",
                        "JUST_A_CONSTANT is 3",
                        "FROM_OUTSIDE is Hi there. This comes from a function")
                .go();
    }

    @Issue("JENKINS-43486")
    @Test
    public void booleanParamAndEnv() throws Exception {
        expect("booleanParamAndEnv")
                .logContains("hello")
                .go();
    }

    @Issue("JENKINS-43486")
    @Test
    public void nullParamAndEnv() throws Exception {
        expect("nullParamAndEnv")
                .logContains("hello")
                .go();
    }

    @Issue("JENKINS-45916")
    @Test
    public void pathInEnv() throws Exception {
        expect("pathInEnv")
                .logMatches("PATH: .*tmpDir:")
                .go();
    }

    @Test
    public void undefinedEnvRef() throws Exception {
        expect("undefinedEnvRef")
                .logContains("[Pipeline] { (foo)",
                        "FOO is BAR",
                        "_UNDERSCORE is VALIDnullORNOT")
                .go();
    }

    @Issue("JENKINS-45637")
    @Test
    public void multipleEnvSubstitutions() throws Exception {
        expect("multipleEnvSubstitutions")
                .logMatches("AAA_Key: key: \\d+ \\d+",
                        "AAA_BN_ONLY: bn: \\d+",
                        "AAA_EN_ONLY: en: \\d+")
                .go();
    }

    @Issue("JENKINS-45636")
    @Test
    public void backslashReductionInEnv() throws Exception {
        expect("backslashReductionInEnv")
                .logMatches("AAA_Key1: a\\\\b \\d+",
                        "AAA_Key2: a\\\\\\\\b",
                        "AAA_Key3: a\\\\b",
                        "AAA_Key4: a\\\\\\\\b \\d+")
                .go();
    }

    @Issue("JENKINS-44603")
    @Test
    public void variableToMethodToEnvVal() throws Exception {
        initGlobalLibrary();
        expect("variableToMethodToEnvVal")
                .logMatches("TADA_VAR: 1 tada")
                .go();
    }

    @Issue("JENKINS-44482")
    @Test
    public void backslashesFromExistingEnvVar() throws Exception {
        expect("backslashesFromExistingEnvVar")
                .logContains("FOO is C:\\Windows\\BAR")
                .go();
    }

    @Issue("JENKINS-45991")
    @Test
    public void defaultEnvValue() throws Exception {
        expect("defaultEnvValue")
                .logContains("FOO is OTHER", "BAZ is BAR")
                .go();
    }

    @Issue("JENKINS-42702")
    @Test
    public void readFileInEnv() throws Exception {
        expect("readFileInEnv")
                .otherResource("readFileInEnv-data.txt", "Version")
                .logContains("Version is BANANA")
                .go();
    }

    @Issue("JENKINS-47600")
    @Test
    public void environmentOverwriteReference() throws Exception {
        expect("environmentOverwriteReference")
                .logContains("value: first second", "value: first third")
                .go();
    }
}
