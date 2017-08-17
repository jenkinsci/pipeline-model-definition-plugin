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
 */

package org.jenkinsci.plugins.pipeline.modeldefinition.agent;

import com.cloudbees.hudson.plugins.folder.Folder;
import com.cloudbees.hudson.plugins.folder.properties.FolderCredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.CredentialsStore;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import hudson.ExtensionList;
import hudson.model.Slave;
import org.jenkinsci.plugins.docker.commons.credentials.DockerRegistryEndpoint;
import org.jenkinsci.plugins.pipeline.modeldefinition.AbstractModelDefTest;
import org.jenkinsci.plugins.pipeline.modeldefinition.config.DockerPropertiesProvider;
import org.jenkinsci.plugins.pipeline.modeldefinition.config.FolderConfig;
import org.jenkinsci.plugins.pipeline.modeldefinition.config.GlobalConfig;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.hamcrest.collection.IsCollectionWithSize.hasSize;
import static org.hamcrest.core.IsInstanceOf.instanceOf;
import static org.junit.Assert.assertThat;

/**
 * Tests {@link DeclarativeDockerUtils}.
 *
 * And related configurations like {@link DockerPropertiesProvider}.
 */
public class DeclarativeDockerUtilsTest extends AbstractModelDefTest {
    private static final UsernamePasswordCredentialsImpl globalCred = new UsernamePasswordCredentialsImpl(CredentialsScope.GLOBAL,
            "globalCreds", "sample", "bobby", "s3cr37");
    private static final UsernamePasswordCredentialsImpl folderCred = new UsernamePasswordCredentialsImpl(CredentialsScope.GLOBAL,
            "folderCreds", "other sample", "andrew", "s0mething");
    private static final UsernamePasswordCredentialsImpl grandParentCred = new UsernamePasswordCredentialsImpl(CredentialsScope.GLOBAL,
            "grandParentCreds", "yet another sample", "leopold", "idunno");

    @BeforeClass
    public static void setup() throws Exception {
        CredentialsStore store = CredentialsProvider.lookupStores(j.jenkins).iterator().next();

        store.addCredentials(Domain.global(), globalCred);
    }

    @Test
    public void plainSystemConfig() throws Exception {
        GlobalConfig.get().setDockerLabel("config_docker");
        GlobalConfig.get().setRegistry(new DockerRegistryEndpoint("https://docker.registry", globalCred.getId()));
        expect("declarativeDockerConfig")
                .logContains("Docker Label is: config_docker",
                        "Registry URL is: https://docker.registry",
                        "Registry Creds ID is: " + globalCred.getId()).go();
    }

    @Test
    public void testExtensionOrdinal() {
        ExtensionList<DockerPropertiesProvider> all = DockerPropertiesProvider.all();
        assertThat(all, hasSize(2));
        assertThat(all.get(0), instanceOf(FolderConfig.FolderDockerPropertiesProvider.class));
        assertThat(all.get(1), instanceOf(GlobalConfig.GlobalConfigDockerPropertiesProvider.class));
    }

    @Test
    public void directParent() throws Exception {
        Folder folder = j.createProject(Folder.class);
        getFolderStore(folder).addCredentials(Domain.global(), folderCred);
        folder.addProperty(new FolderConfig("folder_docker", "https://folder.registry", folderCred.getId()));
        expect("declarativeDockerConfig")
                .inFolder(folder)
                .runFromRepo(false)
                .logContains("Docker Label is: folder_docker",
                        "Registry URL is: https://folder.registry",
                        "Registry Creds ID is: " + folderCred.getId()).go();
    }

    @Test
    public void withDefaults() throws Exception {
        Folder folder = j.createProject(Folder.class);
        getFolderStore(folder).addCredentials(Domain.global(), folderCred);
        getFolderStore(folder).addCredentials(Domain.global(), grandParentCred);
        folder.addProperty(new FolderConfig("folder_docker", "https://folder.registry", folderCred.getId()));
        expect("declarativeDockerConfigWithOverride")
                .inFolder(folder)
                .runFromRepo(false)
                .logContains("Docker Label is: other-label",
                        "Registry URL is: https://other.registry",
                        "Registry Creds ID is: " + grandParentCred.getId()).go();
    }

    @Test
    public void directParentNotSystem() throws Exception {
        GlobalConfig.get().setDockerLabel("config_docker");
        GlobalConfig.get().setRegistry(new DockerRegistryEndpoint("https://docker.registry", globalCred.getId()));
        Folder folder = j.createProject(Folder.class);
        getFolderStore(folder).addCredentials(Domain.global(), folderCred);
        folder.addProperty(new FolderConfig("folder_docker", "https://folder.registry", folderCred.getId()));
        expect("declarativeDockerConfig")
                .inFolder(folder)
                .runFromRepo(false)
                .logContains("Docker Label is: folder_docker",
                        "Registry URL is: https://folder.registry",
                        "Registry Creds ID is: " + folderCred.getId())
                .logNotContains("Docker Label is: config_docker",
                        "Registry URL is: https://docker.registry",
                        "Registry Creds ID is: " + globalCred.getId()).go();
    }

    @Test
    public void grandParent() throws Exception {
        Folder grandParent = j.createProject(Folder.class);
        getFolderStore(grandParent).addCredentials(Domain.global(), grandParentCred);
        grandParent.addProperty(new FolderConfig("parent_docker", "https://parent.registry", grandParentCred.getId()));
        Folder parent = grandParent.createProject(Folder.class, "testParent"); //Can be static since grandParent should be unique
        expect("declarativeDockerConfig")
                .inFolder(parent)
                .runFromRepo(false)
                .logContains("Docker Label is: parent_docker",
                        "Registry URL is: https://parent.registry",
                        "Registry Creds ID is: " + grandParentCred.getId()).go();
    }

    @Test
    public void grandParentOverride() throws Exception {
        Folder grandParent = j.createProject(Folder.class);
        getFolderStore(grandParent).addCredentials(Domain.global(), grandParentCred);
        grandParent.addProperty(new FolderConfig("parent_docker", "https://parent.registry", grandParentCred.getId()));
        Folder parent = grandParent.createProject(Folder.class, "testParent"); //Can be static since grandParent should be unique
        getFolderStore(parent).addCredentials(Domain.global(), folderCred);
        parent.addProperty(new FolderConfig("folder_docker", "https://folder.registry", folderCred.getId()));

        expect("declarativeDockerConfig")
                .inFolder(parent)
                .runFromRepo(false)
                .logContains("Docker Label is: folder_docker",
                        "Registry URL is: https://folder.registry",
                        "Registry Creds ID is: " + folderCred.getId())
                .logNotContains("Docker Label is: parent_docker",
                        "Registry URL is: https://parent.registry",
                        "Registry Creds ID is: " + grandParentCred.getId()).go();
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
        GlobalConfig.get().setRegistry(null);

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
        GlobalConfig.get().setRegistry(null);

        expect("agentDockerEnvSpecLabel").runFromRepo(false).logContains("Running on assumed Docker agent").go();

    }

    private CredentialsStore getFolderStore(Folder f) {
        Iterable<CredentialsStore> stores = CredentialsProvider.lookupStores(f);
        CredentialsStore folderStore = null;
        for (CredentialsStore s : stores) {
            if (s.getProvider() instanceof FolderCredentialsProvider && s.getContext() == f) {
                folderStore = s;
                break;
            }
        }
        return folderStore;
    }


}