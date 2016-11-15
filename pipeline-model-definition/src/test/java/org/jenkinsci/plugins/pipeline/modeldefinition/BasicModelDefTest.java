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

import hudson.model.Result;
import hudson.model.Slave;
import hudson.slaves.DumbSlave;
import hudson.slaves.EnvironmentVariablesNodeProperty;
import jenkins.util.VirtualFile;
import org.apache.commons.io.IOUtils;
import org.jenkinsci.plugins.pipeline.modeldefinition.actions.ExecutionModelAction;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTBranch;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTScriptBlock;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTSingleArgument;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTStage;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTStages;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTStep;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTTreeStep;
import org.jenkinsci.plugins.workflow.cps.nodes.StepStartNode;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.graphanalysis.DepthFirstScanner;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.pipelinegraphanalysis.GenericStatus;
import org.jenkinsci.plugins.workflow.pipelinegraphanalysis.StatusAndTiming;
import org.junit.BeforeClass;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * @author Andrew Bayer
 */
public class BasicModelDefTest extends AbstractModelDefTest {

    private static Slave s;

    @BeforeClass
    public static void setUpAgent() throws Exception {
        s = j.createOnlineSlave();
        s.setLabelString("some-label docker");
    }

    @Test
    public void simplePipeline() throws Exception {
        expect("simplePipeline")
                .logContains("[Pipeline] { (foo)", "hello")
                .logNotContains("[Pipeline] { (Post Build Actions)", "[Pipeline] { (Notifications)")
                .go();
    }

    @Test
    public void failingPipeline() throws Exception {
        expect(Result.FAILURE, "failingPipeline")
                .logContains("[Pipeline] { (foo)",
                        "hello",
                        "goodbye",
                        "farewell",
                        "[Pipeline] { (Post Build Actions)",
                        "[Pipeline] { (Notifications)")
                .hasFailureCase()
                .go();
    }

    @Test
    public void failingPostBuild() throws Exception {
        expect(Result.FAILURE, "failingPostBuild")
                .logContains("[Pipeline] { (foo)",
                        "hello",
                        "goodbye",
                        "farewell",
                        "[Pipeline] { (Post Build Actions)",
                        "[Pipeline] { (Notifications)")
                .hasFailureCase()
                .go();
    }

    @Test
    public void failingNotifications() throws Exception {
        expect(Result.FAILURE, "failingNotifications")
                .logContains("[Pipeline] { (foo)",
                        "hello",
                        "goodbye",
                        "farewell",
                        "[Pipeline] { (Post Build Actions)",
                        "[Pipeline] { (Notifications)")
                .hasFailureCase()
                .go();
    }

    @Test
    public void twoStagePipeline() throws Exception {
        expect("twoStagePipeline")
                .logContains("[Pipeline] { (foo)", "hello", "[Pipeline] { (bar)", "goodbye")
                .go();
    }

    @Issue("JENKINS-38097")
    @Test
    public void allStagesExist() throws Exception {
        WorkflowRun b = expect(Result.FAILURE, "allStagesExist")
                .logContains("[Pipeline] { (foo)", "hello", "[Pipeline] { (bar)")
                .hasFailureCase()
                .go();

        FlowExecution execution = b.getExecution();
        List<FlowNode> heads = execution.getCurrentHeads();
        DepthFirstScanner scanner = new DepthFirstScanner();
        FlowNode startFoo = scanner.findFirstMatch(heads, null, Utils.isStageWithOptionalName("foo"));
        assertNotNull(startFoo);
        assertTrue(startFoo instanceof StepStartNode);
        FlowNode endFoo = scanner.findFirstMatch(heads, null, Utils.endNodeForStage((StepStartNode)startFoo));
        assertNotNull(endFoo);
        assertEquals(GenericStatus.FAILURE, StatusAndTiming.computeChunkStatus(b, null, startFoo, endFoo, null));
        assertNotNull(endFoo.getError());

        FlowNode shouldBeFailedNode = execution.getNode("" + (Integer.valueOf(endFoo.getId()) - 1));
        assertNotNull(shouldBeFailedNode);
        assertNotNull(shouldBeFailedNode.getError());
    }

    @Test
    public void validStepParameters() throws Exception {
        expect("validStepParameters")
                .logContains("[Pipeline] { (foo)", "[Pipeline] timeout", "hello")
                .go();
    }

    @Test
    public void metaStepSyntax() throws Exception {
        env(s).set();
        expect("metaStepSyntax")
                .logContains("[Pipeline] { (foo)", "ONSLAVE=true")
                .archives("msg.out", "hello world")
                .go();
    }

    @Test
    public void legacyMetaStepSyntax() throws Exception {
        env(s).set();
        expect("legacyMetaStepSyntax")
                .logContains("[Pipeline] { (foo)", "ONSLAVE=true")
                .archives("msg.out", "hello world")
                .go();
    }

    @Test
    public void parallelPipeline() throws Exception {
        expect("parallelPipeline")
                .logContains("[Pipeline] { (foo)", "[first] { (Branch: first)", "[second] { (Branch: second)")
                .go();
    }

    @Test
    public void executionModelAction() throws Exception {
        prepRepoWithJenkinsfile("executionModelAction");

        WorkflowRun b = getAndStartBuild();
        j.assertBuildStatusSuccess(j.waitForCompletion(b));
        ExecutionModelAction action = b.getAction(ExecutionModelAction.class);
        assertNotNull(action);
        ModelASTStages stages = action.getStages();
        assertNull(stages.getSourceLocation());
        assertNotNull(stages);

        assertEquals(1, stages.getStages().size());

        ModelASTStage stage = stages.getStages().get(0);
        assertNull(stage.getSourceLocation());
        assertNotNull(stage);

        assertEquals(2, stage.getBranches().size());

        ModelASTBranch firstBranch = branchForName("first", stage.getBranches());
        assertNull(firstBranch.getSourceLocation());
        assertNotNull(firstBranch);
        assertEquals(1, firstBranch.getSteps().size());
        ModelASTStep firstStep = firstBranch.getSteps().get(0);
        assertNull(firstStep.getSourceLocation());
        assertEquals("echo", firstStep.getName());
        assertEquals("First branch", ((ModelASTSingleArgument) firstStep.getArgs()).getValue().getValue());
        assertNull(firstStep.getArgs().getSourceLocation());
        assertNull(((ModelASTSingleArgument) firstStep.getArgs()).getValue().getSourceLocation());

        ModelASTBranch secondBranch = branchForName("second", stage.getBranches());
        assertNotNull(secondBranch);
        assertNull(secondBranch.getSourceLocation());
        assertEquals(2, secondBranch.getSteps().size());
        ModelASTStep scriptStep = secondBranch.getSteps().get(0);
        assertNull(scriptStep.getSourceLocation());
        assertTrue(scriptStep instanceof ModelASTScriptBlock);
        assertNull(scriptStep.getArgs().getSourceLocation());
        assertNull(((ModelASTSingleArgument) scriptStep.getArgs()).getValue().getSourceLocation());

        ModelASTStep timeoutStep = secondBranch.getSteps().get(1);
        assertNull(timeoutStep.getSourceLocation());
        assertTrue(timeoutStep instanceof ModelASTTreeStep);
        assertEquals("timeout", timeoutStep.getName());

        ModelASTTreeStep treeStep = (ModelASTTreeStep)timeoutStep;
        assertEquals(1, treeStep.getChildren().size());
        assertEquals("echo", treeStep.getChildren().get(0).getName());
        assertNull(treeStep.getChildren().get(0).getSourceLocation());
        
        j.assertLogContains("[Pipeline] { (foo)", b);
        j.assertLogContains("[first] { (Branch: first)", b);
        j.assertLogContains("[second] { (Branch: second)", b);
    }

    private ModelASTBranch branchForName(String name, List<ModelASTBranch> branches) {
        for (ModelASTBranch branch : branches) {
            if (branch.getName().equals(name)) {
                return branch;
            }
        }

        return null;
    }

    @Test
    public void dockerGlobalVariable() throws Exception {
        assumeDocker();
        // Bind mounting /var on OS X doesn't work at the moment
        onAllowedOS(PossibleOS.LINUX);

        expect("dockerGlobalVariable")
                .logContains("[Pipeline] { (foo)", "image: ubuntu")
                .go();
    }

    @Test
    public void globalLibrarySuccess() throws Exception {

        initGlobalLibrary();

        // Test the successful, albeit limited, case.
        expect("globalLibrarySuccess")
                .logContains("[nothing here]",
                        "map call(1,2)",
                        "closure1(1)",
                        "running inside closure1",
                        "closure2(1, 2)",
                        "running inside closure2")
                .go();
    }
}
