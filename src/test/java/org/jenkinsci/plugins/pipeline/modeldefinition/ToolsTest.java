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

import hudson.model.JDK;
import hudson.slaves.DumbSlave;
import hudson.slaves.EnvironmentVariablesNodeProperty;
import hudson.tasks.Maven;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import static org.junit.Assert.assertNotNull;
import static org.jvnet.hudson.test.ToolInstallations.configureDefaultMaven;

/**
 * @author Andrew Bayer
 */
public class ToolsTest extends AbstractModelDefTest {

    @Test
    public void simpleTools() throws Exception {
        prepRepoWithJenkinsfile("simpleTools");

        DumbSlave s = j.createOnlineSlave();
        s.setLabelString("some-label");
        s.getNodeProperties().add(new EnvironmentVariablesNodeProperty(new EnvironmentVariablesNodeProperty.Entry("ONSLAVE", "true")));

        WorkflowRun b = getAndStartBuild();
        j.assertBuildStatusSuccess(j.waitForCompletion(b));
        j.assertLogContains("Entering stage foo", b);
        j.assertLogContains("Apache Maven 3.0.1", b);
    }

    @Test
    public void buildPluginParentPOM() throws Exception {
        prepRepoWithJenkinsfile("buildPluginParentPOM");

        Maven.MavenInstallation mvn = configureDefaultMaven("apache-maven-3.1.0", Maven.MavenInstallation.MAVEN_30);

        Maven.MavenInstallation m3 = new Maven.MavenInstallation("apache-maven-3.1.0", mvn.getHome(), JenkinsRule.NO_PROPERTIES);
        j.jenkins.getDescriptorByType(Maven.DescriptorImpl.class).setInstallations(m3);
        JDK[] jdks = j.jenkins.getDescriptorByType(JDK.DescriptorImpl.class).getInstallations();
        JDK thisJdk = null;
        for (JDK j : jdks) {
            if (j.getName().equals("default")) {
                thisJdk = j;
            }
        }
        assertNotNull("Couldn't find JDK named 'default'", thisJdk);

        WorkflowRun b = getAndStartBuild();
        j.assertBuildStatusSuccess(j.waitForCompletion(b));
        j.assertLogContains("Entering stage build", b);
        j.assertLogContains("[INFO] BUILD SUCCESS", b);
        j.assertLogContains("M2_HOME: " + m3.getHome(), b);
        j.assertLogContains("JAVA_HOME: " + thisJdk.getHome(), b);
    }
}
