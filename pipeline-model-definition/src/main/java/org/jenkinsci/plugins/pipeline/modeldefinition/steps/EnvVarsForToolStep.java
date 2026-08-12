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
package org.jenkinsci.plugins.pipeline.modeldefinition.steps;

import hudson.AbortException;
import hudson.EnvVars;
import hudson.Extension;
import hudson.model.EnvironmentSpecific;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.slaves.NodeSpecific;
import hudson.tools.ToolDescriptor;
import hudson.tools.ToolInstallation;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.jenkinsci.plugins.workflow.steps.SynchronousNonBlockingStepExecution;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * An internal step used to take a tool descriptor ID and tool version and get the environment variables that
 * tool installation would normally contribute, in a {@code List<String>} of {@code "VAR=VALUE"}s suitable
 * for passing to {@code withEnv}.
 *
 * Necessary to do this as a {@link Step} so as to have access to the node, listener and environment.
 *
 * @author Andrew Bayer
 */
public final class EnvVarsForToolStep extends Step implements Serializable {

    private static final long serialVersionUID = 1L;

    private String toolId;
    private String toolVersion;

    @DataBoundConstructor
    public EnvVarsForToolStep(String toolId, String toolVersion) {
        this.toolId = toolId;
        this.toolVersion = toolVersion;
    }

    public String getToolVersion() {
        return toolVersion;
    }

    public String getToolId() {
        return toolId;
    }

    @Override
    public StepExecution start(StepContext context) throws Exception {
        return new EnvVarsForToolStepExecution(this, context);
    }

    @Extension
    public static final class DescriptorImpl extends StepDescriptor {

        @Override
        public boolean isAdvanced() {
            return true;
        }

        @Override public String getFunctionName() {
            return "envVarsForTool";
        }

        @Override public String getDisplayName() {
            return "Fetches the environment variables for a given tool in a list of 'FOO=bar' strings suitable for the withEnv step.";
        }

        @Override public boolean takesImplicitBlockArgument() {
            return false;
        }

        @Override
        public Set<? extends Class<?>> getRequiredContext() {
            return Set.of(Node.class, TaskListener.class, EnvVars.class);
        }
    }

    public static final class EnvVarsForToolStepExecution extends SynchronousNonBlockingStepExecution<List<String>> {
        private final transient EnvVarsForToolStep step;

        EnvVarsForToolStepExecution(EnvVarsForToolStep step, StepContext context) {
            super(context);
            this.step = step;
        }

        @Override protected List<String> run() throws Exception {
            String toolVersion = step.getToolVersion();
            String toolId = step.getToolId();

            for (ToolDescriptor<?> desc : ToolInstallation.all()) {
                if (toolId != null && !desc.getId().equals(toolId)) {
                    continue;
                }
                for (ToolInstallation tool : desc.getInstallations()) {
                    if (tool.getName().equals(toolVersion)) {
                        if (tool instanceof NodeSpecific) {
                            Node node = getContext().get(Node.class);
                            TaskListener listener = getContext().get(TaskListener.class);
                            tool = (ToolInstallation) ((NodeSpecific<?>) tool).forNode(node, listener);
                        }
                        if (tool instanceof EnvironmentSpecific) {
                            EnvVars env = getContext().get(EnvVars.class);
                            tool = (ToolInstallation) ((EnvironmentSpecific<?>) tool).forEnvironment(env);
                        }

                        List<String> toolEnvList = new ArrayList<>();
                        EnvVars toolEnv = new EnvVars();
                        tool.buildEnvVars(toolEnv);
                        toolEnv.forEach((key, value) -> toolEnvList.add(key + "=" + value));
                        return toolEnvList;
                    }
                }
            }
            throw new AbortException("No tool of type " + toolId + " named " + toolVersion + " found");
        }

        private static final long serialVersionUID = 1L;
    }
}
