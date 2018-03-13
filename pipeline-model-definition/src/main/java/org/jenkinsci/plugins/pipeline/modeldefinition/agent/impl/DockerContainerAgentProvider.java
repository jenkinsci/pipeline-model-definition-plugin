package org.jenkinsci.plugins.pipeline.modeldefinition.agent.impl;

import hudson.Extension;
import hudson.model.Descriptor;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.DataBoundConstructor;

import javax.annotation.Nonnull;

/**
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
public class DockerContainerAgentProvider extends ContainerAgentProvider {

    public static String SCRIPT = "org.jenkinsci.plugins.pipeline.modeldefinition.agent.impl.DockerAgentScript";

    @DataBoundConstructor
    public DockerContainerAgentProvider() {
    }

    @Override
    public String getScriptClass() {
        return SCRIPT;
    }

    @Override
    public Descriptor<ContainerAgentProvider> getDescriptor() {
        return Jenkins.getInstance().getDescriptor(DockerContainerAgentProvider.class);
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<ContainerAgentProvider> {

        @Override
        public String getDisplayName() {
            return "Docker";
        }
    }

}
