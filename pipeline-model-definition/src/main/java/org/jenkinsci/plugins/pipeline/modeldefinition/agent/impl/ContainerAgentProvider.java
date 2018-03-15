package org.jenkinsci.plugins.pipeline.modeldefinition.agent.impl;

import hudson.ExtensionPoint;
import hudson.model.Describable;

/**
 * Provider for ContainerAgent, to adapt agent description to actual container launcher on infrastructure using adequate API.
 * a thrid-party plugins (docker-plugin or kubernetes-plugin) can implement this extension point and offer containerized
 * agent provisioning using infrastructure specific API.
 *
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
public abstract class ContainerAgentProvider implements Describable<ContainerAgentProvider>, ExtensionPoint {


    public abstract String getScriptClass();


}

