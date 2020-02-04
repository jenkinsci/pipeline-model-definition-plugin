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

import hudson.model.Slave;
import org.jenkinsci.plugins.workflow.actions.ArgumentsAction;
import org.jenkinsci.plugins.workflow.graph.FlowGraphWalker;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertThat;

/**
 * @author Jose Taboada
 */
public class ProvisioningTimeoutModelDefTest extends AbstractModelDefTest {

    private static Slave s;

    @BeforeClass
    public static void setUpAgent() throws Exception {
        s = j.createOnlineSlave();
        s.setNumExecutors(10);
        s.setLabelString("some-label docker");
    }

    @Test
    public void provisioningTimeoutFail() throws Exception {

        WorkflowRun run = expect("options/provisioningTimeoutFail")
                .go();
        log(run, "node");
    }
    @Test
    public void provisioningTimeoutWithAgentLabel() throws Exception {

        WorkflowRun run = expect("options/provisioningTimeoutWithAgentLabel")
                .go();
        log(run, "timeout");
    }

    @Test
    public void provisioningTimeoutHappyPath() throws Exception {

        WorkflowRun run = expect("options/provisioningTimeout")
                .go();
        log(run, "timeout");
    }

    @Test
    public void provisioningTimeoutNoTopLevelAgent() throws Exception {

        WorkflowRun run = expect("options/provisioningTimeoutWithNoTopLevelAgent")
                .go();
        log(run, "timeout");

    }

    private void log(WorkflowRun run, String firstCommand){
        FlowGraphWalker walker = new FlowGraphWalker(run.getExecution());
        Iterator<FlowNode> iterator = walker.iterator();
        List<String> flow = new ArrayList<>();
        while (iterator.hasNext()){
            FlowNode next = iterator.next();
            ArgumentsAction action = next.getAction(ArgumentsAction.class);
            flow.add(String.format("%s %s " , next.getId() , next.getDisplayFunctionName(), action != null ? action.getArguments() : ""));
        }

        int indent = 0;
        for(int i = flow.size()-1; i >= 0; i --){
            String instruction = flow.get(i);
            if(instruction.contains("{")) indent++;
            if(instruction.contains("}")) indent--;
            System.out.println( build(indent, instruction));
        }

        assertThat(flow.get(1), containsString("// "+firstCommand));
    }

    private String build(int indent, String instruction) {
        if( indent > 0)
            return " " + build(--indent,instruction);
        return instruction;
    }
}
