package org.jenkinsci.plugins.pipeline.modeldefinition.agent;

import org.jenkinsci.plugins.pipeline.modeldefinition.AbstractModelDefTest;
import org.junit.Test;

/**
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
public class ContainerAgentTest extends AbstractModelDefTest {

    @Test
    public void agent() throws Exception {
        assumeDocker();
        expect("agentContainer")
                .logContains("Apache Maven 3.5.0")
                .go();
    }
}
