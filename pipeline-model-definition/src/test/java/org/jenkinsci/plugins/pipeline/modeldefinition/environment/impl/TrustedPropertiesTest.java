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
 *
 */

package org.jenkinsci.plugins.pipeline.modeldefinition.environment.impl;

import jenkins.plugins.git.GitSCMSource;
import org.jenkinsci.plugins.pipeline.modeldefinition.AbstractModelDefTest;
import org.jenkinsci.plugins.workflow.libs.GlobalLibraries;
import org.jenkinsci.plugins.workflow.libs.LibraryConfiguration;
import org.jenkinsci.plugins.workflow.libs.SCMSourceRetriever;
import org.junit.Before;
import org.junit.Test;

import java.util.Collections;

/**
 * Tests {@link TrustedProperties}
 */
public class TrustedPropertiesTest extends AbstractModelDefTest {

    @Before
    public void writeMarkers() throws Exception {
        sampleRepo.write("marker.properties", "NAME=Bobby\nNUM=1\n");
        sampleRepo.write("stage/marker.properties", "NAME=Andrew\nNUM=0\n");
        sampleRepo.git("init");
        sampleRepo.git("add", "marker.properties");
        sampleRepo.git("add", "stage/marker.properties");
        sampleRepo.git("commit", "--message=Markers");
    }

    @Test
    public void globalAndStage() throws Exception {
        expect("properties", "environmentFromProperties")
                .logContains(
                        "FOO is BAR",
                        "PROP_NAME is Bobby",
                        "PROP_NUM is 1",
                        "P_NAME is Andrew",
                        "P_NUM is 0").go();
    }

    @Test
    public void fromScmUrl() throws Exception {
        expect("properties", "environmentFromSCM")
                .logContains(
                        "FOO is BAR",
                        "PROP_NAME is Bobby",
                        "PROP_NUM is 1",
                        "P_NAME is Andrew",
                        "P_NUM is 0").go();
    }

    @Test
    public void emptyPrefixStageOverride() throws Exception {
        expect("properties", "environmentFromPropertiesEmptyPrefix")
                .logContains(
                        "FOO is BAR",
                        "NAME is Andrew",
                        "NUM is 0").go();
    }

    @Test
    public void fromMap() throws Exception {
        expect("properties", "environmentFromCodedMap")
                .logContains(
                        "FOO is BAR",
                        "PROP_NAME is Bobby",
                        "PROP_NUM is 5").go();
    }

    @Test
    public void fromLibrary() throws Exception {
        initGlobalLibraryResource();

        expect("properties", "environmentFromLibraryResource")
                .logContains(
                        "FOO is BAR",
                        "PROP_NAME is Liam",
                        "PROP_NUM is 10").go();
    }

    @Test
    public void fromLibraryUrl() throws Exception {
        initGlobalLibraryResource();

        expect("properties", "environmentFromLibraryResourceUrl")
                .logContains(
                        "FOO is BAR",
                        "PROP_NAME is Liam",
                        "PROP_NUM is 10").go();
    }

    private void initGlobalLibraryResource() throws Exception {
        otherRepo.init();
        otherRepo.write("vars/bar.groovy", "void call() { echo \"Hello\" } ");
        otherRepo.write("resources/foo/bar.properties", "#Project build properties for ACME Inc.\n" +
                "\n" +
                "NAME=Liam\n" +
                "NUM=10\n");
        otherRepo.git("add", "vars");
        otherRepo.git("add", "resources");
        otherRepo.git("commit", "--message=init");
        GlobalLibraries.get().setLibraries(Collections.singletonList(
                new LibraryConfiguration("resources-stuff",
                        new SCMSourceRetriever(new GitSCMSource(null, otherRepo.toString(), "", "*", "", true)))));
    }
}