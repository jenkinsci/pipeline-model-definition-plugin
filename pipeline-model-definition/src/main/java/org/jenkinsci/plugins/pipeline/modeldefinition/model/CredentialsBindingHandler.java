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

package org.jenkinsci.plugins.pipeline.modeldefinition.model;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.IdCredentials;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.model.Run;
import org.apache.commons.jexl.context.HashMapContext;
import org.apache.commons.lang.ObjectUtils;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.credentialsbinding.MultiBinding;
import org.jenkinsci.plugins.credentialsbinding.impl.CredentialNotFoundException;
import org.jenkinsci.plugins.credentialsbinding.impl.FileBinding;
import org.jenkinsci.plugins.credentialsbinding.impl.StringBinding;
import org.jenkinsci.plugins.credentialsbinding.impl.UsernamePasswordBinding;
import org.jenkinsci.plugins.credentialsbinding.impl.UsernamePasswordMultiBinding;
import org.jenkinsci.plugins.plaincredentials.FileCredentials;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Simplified {@link org.jenkinsci.plugins.credentialsbinding.Binding} handler for use in {@code environment {} }
 */
public abstract class CredentialsBindingHandler<C extends StandardCredentials> implements ExtensionPoint {

    public boolean handles(Class<? extends StandardCredentials> c) {
        return type().isAssignableFrom(c);
    }

    public boolean handles(StandardCredentials c) {
        return handles(c.getClass());
    }

    @Nonnull
    public List<MultiBinding<C>> toBindings(String varName, String credentialsId) {
        return Collections.emptyList();
    }

    @Nonnull
    public abstract Class<? extends StandardCredentials> type();

    @Nonnull
    public abstract List<Map<String, Object>> getWithCredentialsParameters(String credentialsId);

    @Nonnull
    public static ExtensionList<CredentialsBindingHandler> all() {
        return ExtensionList.lookup(CredentialsBindingHandler.class);
    }

    @Nonnull
    public static Set<Class<? extends StandardCredentials>> supportedTypes() {
        Set<Class<? extends StandardCredentials>> set = new HashSet<>();
        for (CredentialsBindingHandler handler : all()) {
            set.add(handler.type());
        }
        return set;
    }

    @Nonnull
    public static Set<String> supportedTypeNames() {
        Set<String> set = new HashSet<>();
        for (Class<? extends StandardCredentials> c : supportedTypes()) {
            set.add(c.getSimpleName());
        }
        return set;
    }

    @CheckForNull
    public static CredentialsBindingHandler forCredential(StandardCredentials c) {
        for (CredentialsBindingHandler handler : all()) {
            if (handler.handles(c)) {
                return handler;
            }
        }
        return null;
    }

    @Nonnull
    public static CredentialsBindingHandler forId(String id, Run context) throws CredentialNotFoundException {
        IdCredentials cred = CredentialsProvider.findCredentialById(id, IdCredentials.class, context);
        if (cred==null) {
            throw new CredentialNotFoundException(id);
        }
        if (cred instanceof StandardCredentials) {
            CredentialsBindingHandler handler = forCredential((StandardCredentials)cred);
            if (handler == null) {
                throw new CredentialNotFoundException(String.format("No suitable binding handler could be found for type %s. " +
                                                                            "Supported types are %s.",
                                                                    cred.getClass().getName(),
                                                                    StringUtils.join(supportedTypeNames(), ',')));
            }
            return handler;
        } else {
            throw new CredentialNotFoundException(String.format("Credentials %s is of type %s where " +
                                                                "StandardCredentials is the expected type.",
                                                                id, cred.getClass().getName()));
        }
    }

    public static class EnvVarResolver implements Serializable {
        private static final long serialVersionUID = 1L;

        private final String value;


        public EnvVarResolver() {
            this.value = "%s";
        }

        public EnvVarResolver(String value) {
            this.value = value;
        }

        public String resolve(String varName) {
            return String.format(this.value, varName);
        }
    }

    @Extension
    public static class UsernamePasswordHandler extends CredentialsBindingHandler<StandardUsernamePasswordCredentials> {
        @Nonnull
        @Override
        public List<MultiBinding<StandardUsernamePasswordCredentials>> toBindings(String varName, String credentialsId) {
            List<MultiBinding<StandardUsernamePasswordCredentials>> bindings = new ArrayList<>();
            bindings.add(new UsernamePasswordBinding(varName, credentialsId));
            bindings.add(new UsernamePasswordMultiBinding(varName + "_USR",
                    varName + "_PSW",
                    credentialsId));
            return bindings;
        }

        @Nonnull
        @Override
        public Class<? extends StandardCredentials> type() {
            return StandardUsernamePasswordCredentials.class;
        }

        @Nonnull
        @Override
        public List<Map<String, Object>> getWithCredentialsParameters(String credentialsId) {
            Map<String, Object> map = new HashMap<>();
            map.put("$class", UsernamePasswordBinding.class.getName());
            map.put("variable", new EnvVarResolver());
            map.put("credentialsId", credentialsId);
            Map<String, Object> map2 = new HashMap<>();
            map2.put("$class", UsernamePasswordMultiBinding.class.getName());
            map2.put("usernameVariable", new EnvVarResolver("%s_USR"));
            map2.put("passwordVariable", new EnvVarResolver("%s_PSW"));
            map2.put("credentialsId", credentialsId);
            return Arrays.asList(map, map2);
        }

    }

    @Extension
    public static class FileCredentialsHandler extends CredentialsBindingHandler<FileCredentials> {

        @Nonnull
        @Override
        public List<MultiBinding<FileCredentials>> toBindings(String varName, String credentialsId) {
            return Collections.<MultiBinding<FileCredentials>>singletonList(new FileBinding(varName, credentialsId));
        }

        @Nonnull
        @Override
        public Class<? extends StandardCredentials> type() {
            return FileCredentials.class;
        }

        @Nonnull
        @Override
        public List<Map<String, Object>> getWithCredentialsParameters(String credentialsId) {
            Map<String, Object> map = new HashMap<>();
            map.put("$class", FileBinding.class.getName());
            map.put("variable", new EnvVarResolver());
            map.put("credentialsId", credentialsId);
            return Collections.singletonList(map);
        }
    }

    @Extension
    public static class StringCredentialsHandler extends CredentialsBindingHandler<StringCredentials> {

        @Nonnull
        @Override
        public List<MultiBinding<StringCredentials>> toBindings(String varName, String credentialsId) {
            return Collections.<MultiBinding<StringCredentials>>singletonList(new StringBinding(varName, credentialsId));
        }

        @Nonnull
        @Override
        public Class<? extends StandardCredentials> type() {
            return StringCredentials.class;
        }

        @Nonnull
        @Override
        public List<Map<String, Object>> getWithCredentialsParameters(String credentialsId) {
            Map<String, Object> map = new HashMap<>();
            map.put("$class", StringBinding.class.getName());
            map.put("variable", new EnvVarResolver());
            map.put("credentialsId", credentialsId);
            return Collections.singletonList(map);
        }
    }
}
