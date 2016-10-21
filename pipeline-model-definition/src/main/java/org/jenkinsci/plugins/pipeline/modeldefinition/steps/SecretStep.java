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

import com.google.inject.Inject;
import com.trilead.ssh2.crypto.Base64;
import hudson.Extension;
import hudson.model.Descriptor;
import jenkins.security.ConfidentialKey;
import jenkins.security.ConfidentialStore;
import jenkins.security.CryptoConfidentialKey;
import net.sf.json.JSONObject;
import org.apache.commons.lang.ArrayUtils;
import org.jenkinsci.plugins.workflow.cps.Snippetizer;
import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractSynchronousNonBlockingStepExecution;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import java.io.IOException;
import java.io.Serializable;
import java.io.StreamCorruptedException;
import java.util.Map;

/**
 * Provides a step to decrypt an encrypted string literal
 */
public class SecretStep extends AbstractStepImpl implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String text;

    @DataBoundConstructor
    public SecretStep(String text) {
        this.text = text;
    }

    public String getText() {
        return text;
    }

    @Extension
    public static final class DescriptorImpl extends AbstractStepDescriptorImpl {

        private static final int SALT_LENGTH = 8;
        private static final CryptoConfidentialKey KEY = new CryptoConfidentialKey(SecretStep.class.getName());

        public DescriptorImpl() {
            super(StepExecutionImpl.class);
        }

        @Override
        public String getFunctionName() {
            return "secret";
        }

        @Nonnull
        @Override
        public String getDisplayName() {
            return "Provide an encrypted secret that will be decrypted in runtime";
        }

        @Override
        public Step newInstance(@CheckForNull StaplerRequest req, @Nonnull JSONObject formData) throws FormException {
            if (req.findAncestor(Snippetizer.class) != null) {
                String text = formData.optString("text");
                if (text != null) {
                    try {
                        formData.put("text", encrypt(text));
                    } catch (Exception e) {
                        throw new FormException("Could not encrypt the text: " + e.getMessage(), e, "text");
                    }
                }
            }
            return super.newInstance(req, formData);
        }

        @Restricted(NoExternalUse.class)
        public static String decrypt(String secret) throws IOException, BadPaddingException, IllegalBlockSizeException {
            byte[] bytes = Base64.decode(secret.toCharArray());
            byte[] both = KEY.decrypt().doFinal(bytes);
            byte[] prefix = new byte[SALT_LENGTH];
            System.arraycopy(both, 0, prefix, 0, prefix.length);
            byte[] suffix = new byte[SALT_LENGTH];
            System.arraycopy(both, both.length - suffix.length, suffix, 0, suffix.length);
            byte[] actual = new byte[both.length - (SALT_LENGTH*2) - 6];
            System.arraycopy(both, SALT_LENGTH + 3, actual, 0, actual.length);
            ArrayUtils.reverse(suffix);
            if (!ArrayUtils.isEquals(prefix, suffix)) {
                throw new StreamCorruptedException("Salt check differs! Are you using the original key?");
            }
            return new String(actual, "UTF-8");
        }

        @Restricted(NoExternalUse.class)
        public static String encrypt(String plainText) throws IOException, BadPaddingException, IllegalBlockSizeException {
            byte[] bytes = (":::" + plainText + ":::").getBytes("UTF-8");
            byte[] salt = ConfidentialStore.get().randomBytes(SALT_LENGTH);
            byte[] both = new byte[bytes.length + salt.length + salt.length];
            System.arraycopy(salt, 0, both, 0, salt.length);
            System.arraycopy(bytes, 0, both, salt.length, bytes.length);
            ArrayUtils.reverse(salt);
            System.arraycopy(salt, 0, both, salt.length + bytes.length, salt.length);

            return new String(Base64.encode(KEY.encrypt().doFinal(both)));
        }
    }

    public static final class StepExecutionImpl extends AbstractSynchronousNonBlockingStepExecution<String> {
        private static final long serialVersionUID = 1L;

        @Inject
        private SecretStep step;

        @Override
        public String run() throws Exception {
            return DescriptorImpl.decrypt(step.getText());
        }
    }
}
