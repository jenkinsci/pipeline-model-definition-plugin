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
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import hudson.util.Secret;
import org.jenkinsci.plugins.pipeline.modeldefinition.AbstractModelDefTest;
import org.jenkinsci.plugins.plaincredentials.impl.StringCredentialsImpl;
import org.junit.Test;

import java.io.IOException;

import static org.hamcrest.core.AllOf.allOf;
import static org.hamcrest.core.StringContains.containsString;
import static org.junit.Assert.*;

/**
 * Tests {@link CredentialWrapperStep}.
 */
public class CredentialWrapperStepTest extends AbstractModelDefTest {


    @Test
    public void usernamePassword() throws Exception {
        final String credentialsId = "FOOcredentials";
        final String username = "bobby";
        final String password = "s3cr37";
        UsernamePasswordCredentialsImpl c = new UsernamePasswordCredentialsImpl(CredentialsScope.GLOBAL, credentialsId, "sample", username, password);
        CredentialsProvider.lookupStores(j.jenkins).iterator().next().addCredentials(Domain.global(), c);

        expect("credentials", "usernamePassword").runFromRepo(false)
                .logNotContains(password, "FOO_USR is " + username)
                .logContains("FOO_USR is *")
                .archives("combined/foo.txt", allOf(containsString(username), containsString(password)))
                .archives("foo_usr.txt", username).archives("foo_psw.txt", password).go();
    }

    @Test
    public void mixedEnv() throws Exception {
        final String cred1Id = "cred1";
        final String cred2Id = "cred2";

        final String cred1Secret = "Some secret text for 1";
        final String cred2U = "bobby";
        final String cred2P = "supersecretpassword+mydogsname";

        StringCredentialsImpl sc = new StringCredentialsImpl(CredentialsScope.GLOBAL, cred1Id, "test", Secret.fromString(cred1Secret));
        CredentialsProvider.lookupStores(j.jenkins).iterator().next().addCredentials(Domain.global(), sc);
        UsernamePasswordCredentialsImpl c = new UsernamePasswordCredentialsImpl(CredentialsScope.GLOBAL, cred2Id, "sample", cred2U, cred2P);
        CredentialsProvider.lookupStores(j.jenkins).iterator().next().addCredentials(Domain.global(), c);

        expect("credentials", "mixedEnv").runFromRepo(false)
                .logContains("SOME_VAR is SOME VALUE",
                             "INBETWEEN is Something in between",
                             "OTHER_VAR is OTHER VALUE")
                .archives("cred1.txt", cred1Secret)
                .archives("cred2.txt", cred2U + ":" + cred2P).go();
    }

    @Test
    public void mixedEnvInFolder() throws Exception {
        Folder folder = j.jenkins.createProject(Folder.class, "testFolder");
        final String cred1Id = "cred1";
        final String cred2Id = "cred2";

        final String cred1Secret = "Some secret text for 1";
        final String cred2U = "bobby";
        final String cred2P = "supersecretpassword+mydogsname";

        StringCredentialsImpl sc = new StringCredentialsImpl(CredentialsScope.GLOBAL, cred1Id, "test", Secret.fromString(cred1Secret));
        CredentialsProvider.lookupStores(folder).iterator().next().addCredentials(Domain.global(), sc);
        UsernamePasswordCredentialsImpl c = new UsernamePasswordCredentialsImpl(CredentialsScope.GLOBAL, cred2Id, "sample", cred2U, cred2P);
        CredentialsProvider.lookupStores(folder).iterator().next().addCredentials(Domain.global(), c);

        expect("credentials", "mixedEnv").runFromRepo(false).inFolder(folder)
                .logContains("SOME_VAR is SOME VALUE",
                             "INBETWEEN is Something in between",
                             "OTHER_VAR is OTHER VALUE")
                .archives("cred1.txt", cred1Secret)
                .archives("cred2.txt", cred2U + ":" + cred2P).go();
    }
}