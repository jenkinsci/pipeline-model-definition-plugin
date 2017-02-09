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

import hudson.Extension;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.credentialsbinding.impl.CredentialNotFoundException;
import org.jenkinsci.plugins.pipeline.modeldefinition.environment.DeclarativeEnvironmentContributor;
import org.jenkinsci.plugins.pipeline.modeldefinition.environment.DeclarativeEnvironmentContributorDescriptor;
import org.jenkinsci.plugins.pipeline.modeldefinition.model.CredentialsBindingHandler;
import org.jenkinsci.plugins.scriptsecurity.sandbox.whitelists.Whitelisted;
import org.jenkinsci.plugins.workflow.cps.CpsScript;
import org.jenkinsci.plugins.workflow.support.steps.build.RunWrapper;
import org.kohsuke.stapler.DataBoundConstructor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Provides credentials function.
 */
public class Credentials extends DeclarativeEnvironmentContributor<Credentials> implements DeclarativeEnvironmentContributor.MutedGenerator {

    private final String credentialsId;
    private List<Map<String, Object>> withCredentialsParameters;

    @DataBoundConstructor
    public Credentials(String credentialsId) {
        this.credentialsId = credentialsId;
    }

    public String getCredentialsId() {
        return credentialsId;
    }

    @Whitelisted
    public void addParameters(CpsScript script, String envVarName, List<Map<String, Object>> list) throws CredentialNotFoundException {
        RunWrapper currentBuild = (RunWrapper) script.getProperty("currentBuild");
        prepare(currentBuild);
        list.addAll(resolveParameters(envVarName));
    }

    private void prepare(RunWrapper currentBuild) throws CredentialNotFoundException {
        CredentialsBindingHandler handler = CredentialsBindingHandler.forId(credentialsId, currentBuild.getRawBuild());
        withCredentialsParameters = handler.getWithCredentialsParameters(credentialsId);
    }

    @Whitelisted
    public List<Map<String, Object>> resolveParameters(String envVarName) {
        List<Map<String, Object>> newList = new ArrayList<>(withCredentialsParameters.size());
        for (Map<String, Object> params : withCredentialsParameters) {
            Map<String, Object> newP = new HashMap<>();
            for (Map.Entry<String, Object> p : params.entrySet()) {
                Object value = p.getValue();
                if (value instanceof CredentialsBindingHandler.EnvVarResolver) {
                    newP.put(p.getKey(), ((CredentialsBindingHandler.EnvVarResolver)value).resolve(envVarName));
                } else {
                    newP.put(p.getKey(), p.getValue());
                }
            }
            newList.add(newP);
        }
        return newList;
    }

    @Extension @Symbol("credentials")
    public static class DescriptorImpl extends DeclarativeEnvironmentContributorDescriptor<Credentials> {

    }
}
