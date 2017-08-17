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

package org.jenkinsci.plugins.pipeline.modeldefinition;

import hudson.model.BooleanParameterDefinition;
import hudson.model.ParameterDefinition;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.StringParameterDefinition;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Ignore;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ParametersTest extends AbstractModelDefTest {
    @Test
    public void simpleParameters() throws Exception {
        WorkflowRun b = expect("simpleParameters")
                .logContains("[Pipeline] { (foo)", "hello")
                .logNotContains("[Pipeline] { (" + SyntheticStageNames.postBuild() + ")")
                .go();

        WorkflowJob p = b.getParent();

        ParametersDefinitionProperty pdp = p.getProperty(ParametersDefinitionProperty.class);
        assertNotNull(pdp);

        assertEquals(1, pdp.getParameterDefinitions().size());
        assertEquals(BooleanParameterDefinition.class, pdp.getParameterDefinitions().get(0).getClass());
        BooleanParameterDefinition bpd = (BooleanParameterDefinition) pdp.getParameterDefinitions().get(0);
        assertEquals("flag", bpd.getName());
        assertTrue(bpd.isDefaultValue());
    }

    @Ignore("Parameters are set before withEnv is called.")
    @Test
    public void envVarInParameters() throws Exception {
        WorkflowRun b = expect("envVarInParameters")
                .logContains("[Pipeline] { (foo)", "hello")
                .logNotContains("[Pipeline] { (" + SyntheticStageNames.postBuild() + ")")
                .go();

        WorkflowJob p = b.getParent();

        ParametersDefinitionProperty pdp = p.getProperty(ParametersDefinitionProperty.class);
        assertNotNull(pdp);

        assertEquals(1, pdp.getParameterDefinitions().size());
        assertEquals(BooleanParameterDefinition.class, pdp.getParameterDefinitions().get(0).getClass());
        BooleanParameterDefinition bpd = (BooleanParameterDefinition) pdp.getParameterDefinitions().get(0);
        assertEquals("flag", bpd.getName());
        assertTrue(bpd.isDefaultValue());
    }

    @Issue("JENKINS-44149")
    @Test
    public void paramsRemoved() throws Exception {
        WorkflowRun b = getAndStartNonRepoBuild("simpleParameters");
        j.assertBuildStatusSuccess(j.waitForCompletion(b));

        WorkflowJob job = b.getParent();
        ParametersDefinitionProperty paramProp = job.getProperty(ParametersDefinitionProperty.class);
        assertNotNull(paramProp);
        assertEquals(1, paramProp.getParameterDefinitions().size());

        job.setDefinition(new CpsFlowDefinition(pipelineSourceFromResources("propsTriggersParamsRemoved"), true));
        j.buildAndAssertSuccess(job);

        assertNull(job.getProperty(ParametersDefinitionProperty.class));
    }

    @Issue("JENKINS-44621")
    @Test
    public void externalParamsNotRemoved() throws Exception {
        WorkflowRun b = getAndStartNonRepoBuild("simpleParameters");
        j.assertBuildStatusSuccess(j.waitForCompletion(b));

        WorkflowJob job = b.getParent();
        ParametersDefinitionProperty paramProp = job.getProperty(ParametersDefinitionProperty.class);
        assertNotNull(paramProp);
        assertEquals(1, paramProp.getParameterDefinitions().size());

        List<ParameterDefinition> newParams = new ArrayList<>();
        newParams.addAll(paramProp.getParameterDefinitions());
        newParams.add(new StringParameterDefinition("DO_NOT_DELETE", "something"));
        job.removeProperty(paramProp);
        job.addProperty(new ParametersDefinitionProperty(newParams));

        job.setDefinition(new CpsFlowDefinition(pipelineSourceFromResources("propsTriggersParamsRemoved"), true));
        j.buildAndAssertSuccess(job);

        ParametersDefinitionProperty newProp = job.getProperty(ParametersDefinitionProperty.class);
        assertNotNull(newProp);
        assertEquals(1, newProp.getParameterDefinitions().size());
        ParameterDefinition paramDef = newProp.getParameterDefinition("DO_NOT_DELETE");
        assertNotNull(paramDef);
    }
}
