package org.jenkinsci.plugins.pipeline.modeldefinition.agent.impl;

import hudson.ExtensionPoint;
import hudson.model.Describable;

/**
 * Provider for ContainerAgent, to adapt agent description to actual container launcher on infrastructure using adequate API.
 * {@link DockerContainerAgentProvider} provides a default implementation for this based on docker-plugin, but other
 * docker-related plugins (like kubernetes-plugin) can also implement this extension point and offer equivalent
 * containerized agent provisioning using specific API.
 *
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
public abstract class ContainerAgentProvider implements Describable<ContainerAgentProvider>, ExtensionPoint {


    public abstract String getScriptClass();


}

