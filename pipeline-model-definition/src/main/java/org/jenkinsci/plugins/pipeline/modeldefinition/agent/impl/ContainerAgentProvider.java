package org.jenkinsci.plugins.pipeline.modeldefinition.agent.impl;

import hudson.model.Describable;

/**
 * Provider for ContainerAgent, to adapt agent description to actual container launcher on infrastructure using adequate API
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
public abstract class ContainerAgentProvider implements Describable<ContainerAgentProvider> {


    public abstract String getScriptClass();


}

