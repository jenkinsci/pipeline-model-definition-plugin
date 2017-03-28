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
import com.cloudbees.hudson.plugins.folder.properties.FolderCredentialsProvider;
import com.cloudbees.jenkins.plugins.sshcredentials.SSHUserPrivateKey;
import com.cloudbees.jenkins.plugins.sshcredentials.impl.BasicSSHUserPrivateKey;
import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.CredentialsStore;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.domains.DomainCredentials;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import hudson.model.ModelObject;
import hudson.model.Result;
import hudson.util.Secret;
import org.jenkinsci.plugins.pipeline.modeldefinition.AbstractModelDefTest;
import org.jenkinsci.plugins.plaincredentials.impl.StringCredentialsImpl;
import org.junit.BeforeClass;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;

import java.io.IOException;
import java.util.Collections;
import java.util.Iterator;

import static org.hamcrest.core.AllOf.allOf;
import static org.hamcrest.core.StringContains.containsString;

/**
 * Tests the "fake" {@code credentials} step in {@code environment}.
 */
public class CredentialWrapperStepTest extends AbstractModelDefTest {

    public static final String usernamePasswordUsername = "bobby";
    public static final String usernamePasswordPassword = "s3cr37";
    private static final String mixedEnvCred1Id = "cred1";
    private static final String mixedEnvCred2Id = "cred2";
    private static final String mixedEnvCred1Secret = "Some secret text for 1";
    private static final String mixedEnvCred2U = "bobby";
    private static final String mixedEnvCred2P = "supersecretpassword+mydogsname";
    private static Folder folder;
    private static final String mixedEnvInFolderCred1Secret = "Some secret text for 1 folder";
    private static final String mixedEnvInFoldercred2U = "bobby-in-folder";
    private static final String mixedEnvInFolderCred2P = "folder-supersecretpassword+mydogsname";

    @BeforeClass
    public static void setup() throws Exception {
        CredentialsStore store = CredentialsProvider.lookupStores(j.jenkins).iterator().next();

        String usernamePasswordCredentialsId = "FOOcredentials";
        UsernamePasswordCredentialsImpl usernamePassword = new UsernamePasswordCredentialsImpl(CredentialsScope.GLOBAL, usernamePasswordCredentialsId, "sample", usernamePasswordUsername, usernamePasswordPassword);
        store.addCredentials(Domain.global(), usernamePassword);

        StringCredentialsImpl mixedEnvCred1 = new StringCredentialsImpl(CredentialsScope.GLOBAL, mixedEnvCred1Id, "test", Secret.fromString(mixedEnvCred1Secret));
        store.addCredentials(Domain.global(), mixedEnvCred1);
        UsernamePasswordCredentialsImpl mixedEnvCred2 = new UsernamePasswordCredentialsImpl(CredentialsScope.GLOBAL, mixedEnvCred2Id, "sample", mixedEnvCred2U, mixedEnvCred2P);
        store.addCredentials(Domain.global(), mixedEnvCred2);

        folder = j.jenkins.createProject(Folder.class, "testFolder");
        folder.addProperty(new FolderCredentialsProvider.FolderCredentialsProperty(new DomainCredentials[0]));
        j.configRoundtrip(folder);
        CredentialsStore folderStore = folder.getProperties().get(FolderCredentialsProvider.FolderCredentialsProperty.class).getStore();
        StringCredentialsImpl sc = new StringCredentialsImpl(CredentialsScope.GLOBAL, mixedEnvCred1Id, "test", Secret.fromString(mixedEnvInFolderCred1Secret));
        folderStore.addCredentials(Domain.global(), sc);
        UsernamePasswordCredentialsImpl c = new UsernamePasswordCredentialsImpl(CredentialsScope.GLOBAL, mixedEnvCred2Id, "sample", mixedEnvInFoldercred2U, mixedEnvInFolderCred2P);
        folderStore.addCredentials(Domain.global(), c);

        SSHUserPrivateKey k = new BasicSSHUserPrivateKey(CredentialsScope.GLOBAL, "sshCred1", "bobby", new BasicSSHUserPrivateKey.DirectEntryPrivateKeySource("abc123"), null, "sample");
        store.addCredentials(Domain.global(), k);
    }

    @Test
    public void usernamePassword() throws Exception {
        expect("usernamePassword").runFromRepo(false)
                .logNotContains(usernamePasswordPassword, "FOO_USR is " + usernamePasswordUsername)
                .logContains("FOO_USR is *")
                .archives("combined/foo.txt", allOf(containsString(usernamePasswordUsername), containsString(usernamePasswordPassword)))
                .archives("foo_usr.txt", usernamePasswordUsername).archives("foo_psw.txt", usernamePasswordPassword).go();
    }


    @Issue("JENKINS-43143")
    @Test
    public void paramsInCreds() throws Exception {
        expect("paramsInCreds").runFromRepo(false)
                .logNotContains(usernamePasswordPassword, "FOO_USR is " + usernamePasswordUsername)
                .logContains("FOO_USR is *")
                .logContains("CONTAINS_CREDS is FOOcredentials")
                .archives("combined/foo.txt", allOf(containsString(usernamePasswordUsername), containsString(usernamePasswordPassword)))
                .archives("foo_usr.txt", usernamePasswordUsername).archives("foo_psw.txt", usernamePasswordPassword).go();
    }

    @Test
    public void mixedEnv() throws Exception {
        expect("mixedEnv")
                .logContains("SOME_VAR is SOME VALUE",
                             "INBETWEEN is Something in between",
                             "OTHER_VAR is OTHER VALUE")
                .archives("cred1.txt", mixedEnvCred1Secret)
                .archives("cred2.txt", mixedEnvCred2U + ":" + mixedEnvCred2P).go();
    }

    /* TODO: Re-enable once https://issues.jenkins-ci.org/browse/JENKINS-41004 is resolved
    @Test
    public void mixedEnvInFolder() throws Exception {

        expect("credentials", "mixedEnv").runFromRepo(false).inFolder(folder)
                .logContains("SOME_VAR is SOME VALUE",
                             "INBETWEEN is Something in between",
                             "OTHER_VAR is OTHER VALUE")
                .archives("cred1.txt", mixedEnvInFolderCred1Secret)
                .archives("cred2.txt", mixedEnvInFoldercred2U + ":" + mixedEnvInFolderCred2P).go();
    }
    */

    @Test
    public void noBindingAvailable() throws Exception {
        expect(Result.FAILURE, "noBinding").runFromRepo(false)
                .logNotContains("Hello")
                .logContains("No suitable binding handler could be found for type com.cloudbees.jenkins.plugins.sshcredentials.impl.BasicSSHUserPrivateKey")
                .go();
    }
}