package org.jenkinsci.plugins.pipeline.modeldefinition.credentials.impl;

import com.cloudbees.jenkins.plugins.sshcredentials.SSHUserPrivateKey;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import hudson.Extension;
import org.jenkinsci.plugins.credentialsbinding.MultiBinding;
import org.jenkinsci.plugins.credentialsbinding.impl.SSHUserPrivateKeyBinding;
import org.jenkinsci.plugins.pipeline.modeldefinition.model.CredentialsBindingHandler;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Extension
public class SSHUserPrivateKeyHandler extends CredentialsBindingHandler<SSHUserPrivateKey> {

    @Nonnull
    @Override
    public List<MultiBinding<SSHUserPrivateKey>> toBindings(String varName, String credentialsId) {
        SSHUserPrivateKeyBinding keyBinding = new SSHUserPrivateKeyBinding(varName + "_KEY_FILE", credentialsId);
        keyBinding.setPassphraseVariable(varName + "_PASS");
        keyBinding.setUsernameVariable(varName + "_USR");
        return Collections.singletonList(keyBinding);
    }
    
    @Nonnull
    @Override
    public Class<? extends StandardCredentials> type() {
        return SSHUserPrivateKey.class;
    }

    @Nonnull
    @Override
    public List<Map<String, Object>> getWithCredentialsParameters(String credentialsId) {
        Map<String, Object> map = new HashMap<>();
        map.put("$class", SSHUserPrivateKeyBinding.class.getName());
        map.put("keyFileVariable", new EnvVarResolver("%s_KEY_FILE"));
        map.put("usernameVariable", new EnvVarResolver("%s_USR"));
        map.put("passphraseVariable", new EnvVarResolver("%s_PASS"));
        map.put("credentialsId", credentialsId);
        return Collections.singletonList(map);
    }
}
