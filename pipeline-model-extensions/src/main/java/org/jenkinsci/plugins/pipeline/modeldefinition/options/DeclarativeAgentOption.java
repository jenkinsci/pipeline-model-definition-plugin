package org.jenkinsci.plugins.pipeline.modeldefinition.options;

import org.jenkinsci.plugins.pipeline.modeldefinition.agent.DeclarativeAgentDescriptor;

import javax.annotation.CheckForNull;

/**
 * Allows a {@link DeclarativeOption} to override standard resolution for a ${@link DeclarativeAgentDescriptor} based on symbol name.
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
public interface DeclarativeAgentOption {

    @CheckForNull DeclarativeAgentDescriptor getDeclarativeAgentDescriptor(String name);
}
