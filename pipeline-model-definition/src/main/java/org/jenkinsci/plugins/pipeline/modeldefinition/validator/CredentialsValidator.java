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

package org.jenkinsci.plugins.pipeline.modeldefinition.validator;

import hudson.Extension;
import hudson.model.Queue;
import hudson.model.Run;
import org.jenkinsci.plugins.credentialsbinding.impl.CredentialNotFoundException;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTEnvironment;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTEnvironmentValue;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTInternalFunctionCall;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTKey;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTValue;
import org.jenkinsci.plugins.pipeline.modeldefinition.model.CredentialsBindingHandler;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.flow.FlowExecutionOwner;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.Map;

@Extension
public class CredentialsValidator extends DeclarativeValidatorContributor {

    @CheckForNull
    @Override
    public String validateElement(@Nonnull ModelASTEnvironment environment, @CheckForNull FlowExecution execution) {
        final FlowExecutionOwner owner = execution != null ? execution.getOwner() : null;
        if (owner != null) {
            try {
                final Queue.Executable executable = owner.getExecutable();
                if (executable != null && executable instanceof Run) {
                    //This might show the error not exactly in the right place
                    for (Map.Entry<ModelASTKey, ModelASTEnvironmentValue> entry : environment.getVariables().entrySet()) {
                        if (entry.getValue() instanceof ModelASTInternalFunctionCall) {
                            ModelASTInternalFunctionCall func = (ModelASTInternalFunctionCall) entry.getValue();
                            if (func.getName().equals("credentials")) {
                                if (func.getArgs().isEmpty()) {
                                    return Messages.CredentialsValidator_noArgs();
                                } else {
                                    final ModelASTValue idValue = func.getArgs().get(0);
                                    if (idValue.isLiteral() && !idValue.getValue().toString().startsWith("${")) {
                                        //In this context I guess we can only evaluate literals, not runtime computed values
                                        final String id = idValue.getValue().toString();
                                        try {
                                            CredentialsBindingHandler.forId(id, (Run) executable);
                                        } catch (CredentialNotFoundException e) {
                                            return Messages.CredentialsValidator_noCredentialFound(id, ((Run) executable).getFullDisplayName(), e.getMessage());
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (IOException ignore) { }

        }
        return null;
    }
}
