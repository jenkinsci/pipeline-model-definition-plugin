package org.jenkinsci.plugins.pipeline.modeldefinition.agent;

import org.jenkinsci.plugins.pipeline.modeldefinition.AbstractModelDefTest;
import org.jenkinsci.plugins.pipeline.modeldefinition.config.GlobalConfig;
import org.junit.Test;

/**
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
public class ContainerAgentTest extends AbstractModelDefTest {

    @Test
    public void none() throws Exception {

        final GlobalConfig config = j.jenkins.getExtensionList(GlobalConfig.class).get(0);
        config.setProvider(null);

        expect("agentContainer")
                .logContains("require an implementationx")
                .go();
    }
}
