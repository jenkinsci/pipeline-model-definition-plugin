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

import com.cloudbees.hudson.plugins.folder.Folder;
import hudson.ExtensionList;
import hudson.model.Slave;
import org.jenkinsci.plugins.pipeline.modeldefinition.AbstractModelDefTest;
import org.jenkinsci.plugins.pipeline.modeldefinition.config.DockerLabelProvider;
import org.jenkinsci.plugins.pipeline.modeldefinition.config.FolderConfig;
import org.jenkinsci.plugins.pipeline.modeldefinition.config.GlobalConfig;
import org.junit.Test;

import static org.hamcrest.collection.IsCollectionWithSize.hasSize;
import static org.hamcrest.core.IsInstanceOf.instanceOf;
import static org.junit.Assert.assertThat;

/**
 * Tests {@link DockerLabelStep}.
 *
 * And related configurations like {@link DockerLabelProvider}.
 */
public class DockerLabelStepTest extends AbstractModelDefTest {

    @Test
    public void plainSystemConfig() throws Exception {
        GlobalConfig.get().setDockerLabel("config_docker");
        expect("dockerLabel").logContains("Docker Label is: config_docker").go();
    }

    @Test
    public void testExtensionOrdinal() {
        ExtensionList<DockerLabelProvider> all = DockerLabelProvider.all();
        assertThat(all, hasSize(2));
        assertThat(all.get(0), instanceOf(FolderConfig.FolderDockerLabelProvider.class));
        assertThat(all.get(1), instanceOf(GlobalConfig.GlobalConfigDockerLabelProvider.class));
    }

    @Test
    public void directParent() throws Exception {
        Folder folder = j.createProject(Folder.class);
        folder.addProperty(new FolderConfig("folder_docker"));
        expect("dockerLabel").inFolder(folder).runFromRepo(false).logContains("Docker Label is: folder_docker").go();
    }

    @Test
    public void directParentNotSystem() throws Exception {
        GlobalConfig.get().setDockerLabel("config_docker");
        Folder folder = j.createProject(Folder.class);
        folder.addProperty(new FolderConfig("folder_docker"));
        expect("dockerLabel").inFolder(folder).runFromRepo(false)
                .logContains("Docker Label is: folder_docker").logNotContains("config_docker").go();
    }

    @Test
    public void grandParent() throws Exception {
        Folder grandParent = j.createProject(Folder.class);
        grandParent.addProperty(new FolderConfig("parent_docker"));
        Folder parent = grandParent.createProject(Folder.class, "testParent"); //Can be static since grandParent should be unique
        expect("dockerLabel").inFolder(parent).runFromRepo(false).logContains("Docker Label is: parent_docker").go();
    }

    @Test
    public void grandParentOverride() throws Exception {
        Folder grandParent = j.createProject(Folder.class);
        grandParent.addProperty(new FolderConfig("grand_parent_docker"));
        Folder parent = grandParent.createProject(Folder.class, "testParent"); //Can be static since grandParent should be unique
        parent.addProperty(new FolderConfig("parent_docker"));
        expect("dockerLabel").inFolder(parent).runFromRepo(false)
                .logContains("Docker Label is: parent_docker")
                .logNotContains("grand_parent_docker").go();
    }

    @Test
    public void runsOnCorrectSlave() throws Exception {
        assumeDocker();
        Slave s = j.createOnlineSlave();
        s.setLabelString("notthis");
        env(s).put("DOCKER_INDICATOR", "WRONG").set();
        s = j.createOnlineSlave();
        s.setLabelString("thisone");
        env(s).put("DOCKER_INDICATOR", "CORRECT").set();
        GlobalConfig.get().setDockerLabel("thisone");

        expect("agentDockerEnvTest").runFromRepo(false).logContains("Running on assumed Docker agent").go();

    }

    @Test
    public void runsOnSpecifiedSlave() throws Exception {
        assumeDocker();
        Slave s = j.createOnlineSlave();
        s.setLabelString("thisspec");
        env(s).put("DOCKER_INDICATOR", "SPECIFIED").set();
        s = j.createOnlineSlave();
        s.setLabelString("thisone");
        env(s).put("DOCKER_INDICATOR", "CORRECT").set();
        GlobalConfig.get().setDockerLabel("thisone");

        expect("agentDockerEnvSpecLabel").runFromRepo(false).logContains("Running on assumed Docker agent").go();

    }

}