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

import com.cloudbees.hudson.plugins.folder.Folder;
import com.google.common.base.Predicate;
import htmlpublisher.HtmlPublisherTarget;
import hudson.model.Result;
import hudson.model.Slave;
import hudson.slaves.EnvironmentVariablesNodeProperty;
import hudson.tasks.LogRotator;
import jenkins.model.BuildDiscarder;
import jenkins.model.BuildDiscarderProperty;
import jenkins.plugins.git.GitSCMSource;
import org.apache.commons.io.FileUtils;
import org.jenkinsci.plugins.pipeline.StageStatus;
import org.jenkinsci.plugins.pipeline.modeldefinition.actions.DeclarativeJobAction;
import org.jenkinsci.plugins.pipeline.modeldefinition.actions.ExecutionModelAction;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTBranch;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTKey;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTNamedArgumentList;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTScriptBlock;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTSingleArgument;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTStage;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTStages;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTStep;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTTreeStep;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTValue;
import org.jenkinsci.plugins.workflow.actions.LogAction;
import org.jenkinsci.plugins.workflow.actions.TagsAction;
import org.jenkinsci.plugins.workflow.actions.ThreadNameAction;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.cps.nodes.StepAtomNode;
import org.jenkinsci.plugins.workflow.cps.nodes.StepStartNode;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.graph.BlockStartNode;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.graphanalysis.DepthFirstScanner;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.libs.FolderLibraries;
import org.jenkinsci.plugins.workflow.libs.GlobalLibraries;
import org.jenkinsci.plugins.workflow.libs.LibraryConfiguration;
import org.jenkinsci.plugins.workflow.libs.SCMSourceRetriever;
import org.jenkinsci.plugins.workflow.pipelinegraphanalysis.GenericStatus;
import org.jenkinsci.plugins.workflow.pipelinegraphanalysis.StatusAndTiming;
import org.jenkinsci.plugins.workflow.steps.ErrorStep;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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
        s.setNumExecutors(10);
        s.setLabelString("some-label docker");
    }

    @Test
    public void simplePipeline() throws Exception {
        expect("simplePipeline")
                .logContains("[Pipeline] { (foo)", "hello")
                .logNotContains("[Pipeline] { (" + SyntheticStageNames.postBuild() + ")")
                .go();
    }

    @Test
    public void failingPipeline() throws Exception {
        expect(Result.FAILURE, "failingPipeline")
                .logContains("[Pipeline] { (foo)",
                        "hello",
                        "goodbye",
                        "[Pipeline] { (" + SyntheticStageNames.postBuild() + ")")
                .hasFailureCase()
                .go();
    }

    @Test
    public void failingPostBuild() throws Exception {
        expect(Result.FAILURE, "failingPostBuild")
                .logContains("[Pipeline] { (foo)",
                        "hello",
                        "goodbye",
                        "[Pipeline] { (" + SyntheticStageNames.postBuild() + ")")
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
        assertNotNull(execution);
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
        TagsAction parentTags = startFoo.getAction(TagsAction.class);
        assertNotNull(parentTags);
        assertNotNull(parentTags.getTags());
        assertFalse(parentTags.getTags().isEmpty());
        assertTrue(parentTags.getTags().containsKey(Utils.getStageStatusMetadata().getTagName()));
        assertEquals(Utils.getStageStatusMetadata().getFailedAndContinued(),
                parentTags.getTags().get(Utils.getStageStatusMetadata().getTagName()));

    }

    @Issue("JENKINS-41334")
    @Test
    public void parallelStagesHaveStatusAndPost() throws Exception {
        WorkflowRun b = expect(Result.FAILURE, "parallelStagesHaveStatusAndPost")
                .logContains("[Pipeline] { (foo)",
                        "[first] { (Branch: first)",
                        "[Pipeline] [first] { (first)",
                        "[second] { (Branch: second)",
                        "[Pipeline] [second] { (second)",
                        "FIRST BRANCH FAILED",
                        "SECOND BRANCH POST",
                        "FOO STAGE FAILED")
                .hasFailureCase()
                .go();

        FlowExecution execution = b.getExecution();
        assertNotNull(execution);
        List<FlowNode> heads = execution.getCurrentHeads();
        DepthFirstScanner scanner = new DepthFirstScanner();
        FlowNode startFoo = scanner.findFirstMatch(heads, null, Utils.isStageWithOptionalName("foo"));
        assertNotNull(startFoo);
        assertTrue(startFoo instanceof BlockStartNode);
        FlowNode endFoo = scanner.findFirstMatch(heads, null, Utils.endNodeForStage((BlockStartNode)startFoo));
        assertNotNull(endFoo);
        assertEquals(GenericStatus.FAILURE, StatusAndTiming.computeChunkStatus(b, null, startFoo, endFoo, null));
        assertNotNull(endFoo.getError());

        FlowNode startFirst = scanner.findFirstMatch(heads, null, Utils.isStageWithOptionalName("first"));
        assertNotNull(startFirst);
        assertTrue(startFirst instanceof BlockStartNode);
        FlowNode endFirst = scanner.findFirstMatch(heads, null, Utils.endNodeForStage((BlockStartNode)startFirst));
        assertNotNull(endFirst);
        assertEquals(GenericStatus.FAILURE, StatusAndTiming.computeChunkStatus(b, null, startFirst, endFirst, null));
        assertNotNull(endFirst.getError());

        FlowNode startThird = scanner.findFirstMatch(heads, null, Utils.isStageWithOptionalName("third"));
        assertNotNull(startThird);
        assertTrue(startThird instanceof BlockStartNode);
        FlowNode endThird = scanner.findFirstMatch(heads, null, Utils.endNodeForStage((BlockStartNode)startThird));
        assertNotNull(endThird);
        assertEquals(GenericStatus.NOT_EXECUTED, StatusAndTiming.computeChunkStatus(b, null, startThird, endThird, null));

        TagsAction thirdTags = startThird.getAction(TagsAction.class);
        assertNotNull(thirdTags);
        assertNotNull(thirdTags.getTags());
        assertFalse(thirdTags.getTags().isEmpty());
        assertTrue(thirdTags.getTags().containsKey(Utils.getStageStatusMetadata().getTagName()));
        assertEquals(StageStatus.getSkippedForConditional(),
                thirdTags.getTags().get(Utils.getStageStatusMetadata().getTagName()));

        TagsAction nestedTags = startFirst.getAction(TagsAction.class);
        assertNotNull(nestedTags);
        assertNotNull(nestedTags.getTags());
        assertFalse(nestedTags.getTags().isEmpty());
        assertTrue(nestedTags.getTags().containsKey(Utils.getStageStatusMetadata().getTagName()));
        assertEquals(StageStatus.getFailedAndContinued(),
                nestedTags.getTags().get(Utils.getStageStatusMetadata().getTagName()));

        TagsAction parentTags = startFoo.getAction(TagsAction.class);
        assertNotNull(parentTags);
        assertNotNull(parentTags.getTags());
        assertFalse(parentTags.getTags().isEmpty());
        assertTrue(parentTags.getTags().containsKey(Utils.getStageStatusMetadata().getTagName()));
        assertEquals(StageStatus.getFailedAndContinued(),
                parentTags.getTags().get(Utils.getStageStatusMetadata().getTagName()));

        // Was originally testing to see if the last-but-one node in the failed block was the failure but that's
        // actually a bogus test, particularly when running post stuff.
    }

    @Issue("JENKINS-42039")
    @Test
    public void skipAfterUnstableWithOption() throws Exception {
        WorkflowRun b = expect(Result.UNSTABLE, "skipAfterUnstableIfOption")
                .logContains("[Pipeline] { (foo)", "hello", "[Pipeline] { (bar)")
                .logNotContains("goodbye")
                .go();

        FlowExecution execution = b.getExecution();
        assertNotNull(execution);
        List<FlowNode> heads = execution.getCurrentHeads();
        DepthFirstScanner scanner = new DepthFirstScanner();
        FlowNode startFoo = scanner.findFirstMatch(heads, null, Utils.isStageWithOptionalName("foo"));
        assertNotNull(startFoo);
        assertTrue(startFoo instanceof StepStartNode);
        FlowNode endFoo = scanner.findFirstMatch(heads, null, Utils.endNodeForStage((StepStartNode)startFoo));
        assertNotNull(endFoo);
        assertEquals(GenericStatus.UNSTABLE, StatusAndTiming.computeChunkStatus(b, null, startFoo, endFoo, null));
    }

    @Issue("JENKINS-42039")
    @Test
    public void dontSkipAfterUnstableByDefault() throws Exception {
        expect(Result.UNSTABLE, "dontSkipAfterUnstableByDefault")
                .logContains("[Pipeline] { (foo)", "hello", "[Pipeline] { (bar)", "goodbye")
                .go();
    }

    @Test
    public void validStepParameters() throws Exception {
        expect("validStepParameters")
                .logContains("[Pipeline] { (foo)", "[Pipeline] timeout", "hello")
                .go();
    }

    @Test
    public void nestedTreeSteps() throws Exception {
        expect("nestedTreeSteps")
                .logContains("[Pipeline] { (foo)", "[Pipeline] timeout", "[Pipeline] retry", "hello")
                .go();
    }

    @Test
    public void metaStepSyntax() throws Exception {
        env(s).set();
        expect("metaStepSyntax")
                .logContains("[Pipeline] { (foo)", "ONAGENT=true")
                .archives("msg.out", "hello world")
                .go();
    }

    @Test
    public void legacyMetaStepSyntax() throws Exception {
        env(s).set();
        expect("legacyMetaStepSyntax")
                .logContains("[Pipeline] { (foo)", "ONAGENT=true")
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
    public void parallelPipelineQuoteEscaping() throws Exception {
        expect("parallelPipelineQuoteEscaping")
                .logContains("[Pipeline] { (foo)", "[first] { (Branch: first)", "[\"second\"] { (Branch: \"second\")")
                .go();
    }

    @Test
    public void parallelPipelineWithSpaceInBranch() throws Exception {
        expect("parallelPipelineWithSpaceInBranch")
                .logContains("[Pipeline] { (foo)", "[first one] { (Branch: first one)", "[second one] { (Branch: second one)")
                .go();
    }

    @Test
    public void parallelPipelineWithFailFast() throws Exception {
        expect("parallelPipelineWithFailFast")
                .logContains("[Pipeline] { (foo)", "[first] { (Branch: first)", "[second] { (Branch: second)")
                .go();
    }

    @Issue("JENKINS-41456")
    @Test
    public void htmlPublisher() throws Exception {
        WorkflowRun b = expect("htmlPublisher")
                .logContains("[Pipeline] { (foo)")
                .go();

        HtmlPublisherTarget.HTMLBuildAction buildReport = b.getAction(HtmlPublisherTarget.HTMLBuildAction.class);
        assertNotNull(buildReport);
        assertEquals("Test Report", buildReport.getHTMLTarget().getReportName());
    }

    @Test
    public void executionModelAction() throws Exception {
        WorkflowRun b = expect("executionModelAction").go();

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
        assertNotNull(firstBranch);
        assertNull(firstBranch.getSourceLocation());
        assertNotNull(firstBranch);
        assertEquals(1, firstBranch.getSteps().size());
        ModelASTStep firstStep = firstBranch.getSteps().get(0);
        assertNull(firstStep.getSourceLocation());
        assertEquals("echo", firstStep.getName());
        ModelASTValue val = null;
        if (firstStep.getArgs() instanceof ModelASTSingleArgument) {
            val = ((ModelASTSingleArgument) firstStep.getArgs()).getValue();
        } else if (firstStep.getArgs() instanceof ModelASTNamedArgumentList && ((ModelASTNamedArgumentList) firstStep.getArgs()).getArguments().size() == 1) {
            val = ((ModelASTNamedArgumentList) firstStep.getArgs()).valueForName("message");
        }
        assertNotNull(val);
        assertEquals("First branch", val.getValue());

        assertNull(firstStep.getArgs().getSourceLocation());
        assertNull(val.getSourceLocation());

        ModelASTBranch secondBranch = branchForName("second", stage.getBranches());
        assertNotNull(secondBranch);
        assertNull(secondBranch.getSourceLocation());
        assertEquals(2, secondBranch.getSteps().size());
        ModelASTStep scriptStep = secondBranch.getSteps().get(0);
        assertNull(scriptStep.getSourceLocation());
        assertTrue(scriptStep instanceof ModelASTScriptBlock);
        assertNull(scriptStep.getArgs().getSourceLocation());
        ModelASTValue scriptVal = null;
        if (scriptStep.getArgs() instanceof ModelASTSingleArgument) {
            scriptVal = ((ModelASTSingleArgument) scriptStep.getArgs()).getValue();
        } else if (scriptStep.getArgs() instanceof ModelASTNamedArgumentList && ((ModelASTNamedArgumentList) scriptStep.getArgs()).getArguments().size() == 1) {
            scriptVal = ((ModelASTNamedArgumentList) scriptStep.getArgs()).valueForName("scriptBlock");
        }
        assertNotNull(scriptVal);
        assertNull(scriptVal.getSourceLocation());

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

    @Issue("JENKINS-45081")
    @Test
    public void objectMethodPipelineCall() throws Exception {
        initGlobalLibrary();

        expect("objectMethodPipelineCall")
                .logContains("Hi there")
                .go();
    }

    @Test
    public void basicWhen() throws Exception {
        expect("basicWhen")
                .logContains("[Pipeline] { (One)", "[Pipeline] { (Two)", "World")
                .go();
    }

    @Test
    public void whenExprUsingOutsideVarAndFunc() throws Exception {
        expect("whenExprUsingOutsideVarAndFunc")
                .logContains("[Pipeline] { (One)", "[Pipeline] { (Two)", "World")
                .go();
    }

    @Test
    public void skippedWhen() throws Exception {
        expect("skippedWhen")
                .logContains("[Pipeline] { (One)", "[Pipeline] { (Two)")
                .logNotContains("World")
                .go();
    }

    @Test
    public void whenLaterStages() throws Exception {
        expect("whenLaterStages")
                .logContains("[Pipeline] { (One)", "[Pipeline] { (Two)", "I'm running anyway", "And I run last of all")
                .logNotContains("World")
                .go();
    }

    @Test
    public void whenBranchFalse() throws Exception {
        expect("whenBranchFalse")
                .logContains("[Pipeline] { (One)", "[Pipeline] { (Two)")
                .logNotContains("World")
                .go();
    }

    @Test
    public void whenBranchTrue() throws Exception {
        expect("whenBranchTrue")
                .logContains("[Pipeline] { (One)", "[Pipeline] { (Two)", "World")
                .go();
    }

    @Issue("JENKINS-42226")
    @Test
    public void whenBranchNull() throws Exception {
        expect("whenBranchNull")
                .logContains("[Pipeline] { (One)", "[Pipeline] { (Two)")
                .logNotContains("World")
                .go();
    }

    @Test
    public void whenNot() throws Exception {
        expect("whenNot")
                .logContains("[Pipeline] { (One)", "[Pipeline] { (Two)", "World")
                .go();
    }

    @Test
    public void whenOr() throws Exception {
        expect("whenOr")
                .logContains("[Pipeline] { (One)", "[Pipeline] { (Two)", "World")
                .go();
    }

    @Test
    public void whenAnd() throws Exception {
        expect("whenAnd")
                .logContains("[Pipeline] { (One)", "[Pipeline] { (Two)")
                .logNotContains("World")
                .go();
    }

    @Issue("JENKINS-42762")
    @Test
    public void whenMultiple() throws Exception {
        expect("whenMultiple")
                .logContains("[Pipeline] { (One)", "[Pipeline] { (Two)")
                .logNotContains("World")
                .go();
    }

    @Test
    public void whenAndOrSingle() throws Exception {
        expect("whenAndOrSingle")
                .logContains("[Pipeline] { (One)", "[Pipeline] { (Two)")
                .logNotContains("World")
                .go();
    }

    @Test
    public void whenNestedCombinations() throws Exception {
        expect("whenNestedCombinations")
                .logContains("First stage has no condition",
                        "Second stage meets condition",
                        "Fourth stage meets condition")
                .logNotContains("Third stage meets condition")
                .go();
    }

    @Test
    public void whenEnvTrue() throws Exception {
        expect("whenEnvTrue")
                .logContains("[Pipeline] { (One)", "[Pipeline] { (Two)", "World")
                .go();
    }

    @Test
    public void whenEnvIgnoreCase() throws Exception {
        expect("whenEnvIgnoreCase")
                .logContains("[Pipeline] { (One)", "[Pipeline] { (Two)", "World")
                .go();
    }

    @Test
    public void whenEnvFalse() throws Exception {
        expect("whenEnvFalse")
                .logContains("[Pipeline] { (One)", "[Pipeline] { (Two)")
                .logNotContains("World")
                .go();
    }

    @Test
    public void syntheticStages() throws Exception {
        WorkflowRun b = expect("syntheticStages")
                .logContains("[Pipeline] { (" + SyntheticStageNames.toolInstall() + ")",
                        "[Pipeline] { (" + SyntheticStageNames.checkout() + ")",
                        "[Pipeline] { (foo)",
                        "hello",
                        "[Pipeline] { (" + SyntheticStageNames.postBuild() + ")",
                        "I AM A POST-BUILD")
                .go();

        FlowExecution execution = b.getExecution();

        assertNotNull(execution);
        Collection<FlowNode> heads = execution.getCurrentHeads();

        DepthFirstScanner scanner = new DepthFirstScanner();

        assertNotNull(scanner.findFirstMatch(heads, null, syntheticStagePredicate(SyntheticStageNames.toolInstall(), Utils.getSyntheticStageMetadata().getPre())));
        assertNotNull(scanner.findFirstMatch(heads, null, syntheticStagePredicate(SyntheticStageNames.checkout(), Utils.getSyntheticStageMetadata().getPre())));
        assertNotNull(scanner.findFirstMatch(heads, null, syntheticStagePredicate(SyntheticStageNames.postBuild(), Utils.getSyntheticStageMetadata().getPost())));
        assertNull(scanner.findFirstMatch(heads, null, syntheticStagePredicate(SyntheticStageNames.agentSetup(), Utils.getSyntheticStageMetadata().getPre())));
    }

    @Test
    public void noToolSyntheticStage() throws Exception {
        WorkflowRun b = expect("noToolSyntheticStage")
                .logContains("[Pipeline] { (" + SyntheticStageNames.checkout() + ")",
                        "[Pipeline] { (foo)",
                        "hello",
                        "[Pipeline] { (" + SyntheticStageNames.postBuild() + ")",
                        "I AM A POST-BUILD")
                .go();

        FlowExecution execution = b.getExecution();

        assertNotNull(execution);
        Collection<FlowNode> heads = execution.getCurrentHeads();

        DepthFirstScanner scanner = new DepthFirstScanner();

        assertNull(scanner.findFirstMatch(heads, null, syntheticStagePredicate(SyntheticStageNames.toolInstall(), Utils.getSyntheticStageMetadata().getPre())));
        assertNotNull(scanner.findFirstMatch(heads, null, syntheticStagePredicate(SyntheticStageNames.checkout(), Utils.getSyntheticStageMetadata().getPre())));
        assertNotNull(scanner.findFirstMatch(heads, null, syntheticStagePredicate(SyntheticStageNames.postBuild(), Utils.getSyntheticStageMetadata().getPost())));
        assertNull(scanner.findFirstMatch(heads, null, syntheticStagePredicate(SyntheticStageNames.agentSetup(), Utils.getSyntheticStageMetadata().getPre())));
    }

    @Test
    public void skippedStagesForFailure() throws Exception {
        WorkflowRun b = expect(Result.FAILURE, "skippedStagesForFailure")
                .logContains("[Pipeline] { (foo)", "hello", "I have failed")
                .logNotContains("I will be skipped", "I also will be skipped", "I have succeeded")
                .go();

        FlowExecution execution = b.getExecution();
        assertNotNull(execution);
        assertNotNull(execution.getCauseOfFailure());

        Collection<FlowNode> heads = execution.getCurrentHeads();

        DepthFirstScanner scanner = new DepthFirstScanner();

        assertNull(scanner.findFirstMatch(heads, stageStatusPredicate("foo", Utils.getStageStatusMetadata().getSkippedForFailure())));
        assertNotNull(scanner.findFirstMatch(heads, stageStatusPredicate("foo", Utils.getStageStatusMetadata().getFailedAndContinued())));
        assertNotNull(scanner.findFirstMatch(heads, stageStatusPredicate("bar", Utils.getStageStatusMetadata().getSkippedForFailure())));
        assertNotNull(scanner.findFirstMatch(heads, stageStatusPredicate("baz", Utils.getStageStatusMetadata().getSkippedForFailure())));
    }

    @Test
    public void skippedStagesInParallel() throws Exception {
        WorkflowRun b = expect(Result.FAILURE, "skippedStagesInParallel")
                .logContains("[Pipeline] { (foo)", "hello", "I will not be skipped but I will fail")
                .logNotContains("I will be skipped", "skipped for earlier failure", "I have succeeded")
                .go();

        FlowExecution execution = b.getExecution();
        assertNotNull(execution);
        assertNotNull(execution.getCauseOfFailure());

        // foo didn't fail at all.
        List<FlowNode> fooStages = Utils.findStageFlowNodes("foo", execution);
        assertNotNull(fooStages);
        assertEquals(1, fooStages.size());
        FlowNode foo = fooStages.get(0);
        assertFalse(stageStatusPredicate("foo", StageStatus.getSkippedForFailure()).apply(foo));
        assertFalse(stageStatusPredicate("foo", StageStatus.getFailedAndContinued()).apply(foo));

        // first-parallel did fail.
        List<FlowNode> firstParallelStages = Utils.findStageFlowNodes("first-parallel", execution);
        assertNotNull(firstParallelStages);
        assertEquals(1, firstParallelStages.size());
        FlowNode firstParallel = firstParallelStages.get(0);
        assertFalse(stageStatusPredicate("first-parallel", StageStatus.getSkippedForFailure()).apply(firstParallel));
        assertTrue(stageStatusPredicate("first-parallel", StageStatus.getFailedAndContinued()).apply(firstParallel));

        // bar was a parallel stage that was skipped due to conditional.
        List<FlowNode> barStages = Utils.findStageFlowNodes("bar", execution);
        assertNotNull(barStages);
        assertEquals(2, barStages.size());

        for (FlowNode bar : barStages) {
            assertTrue(stageStatusPredicate("bar", StageStatus.getSkippedForConditional()).apply(bar));
        }

        // baz was a parallel stage that failed.
        List<FlowNode> bazStages = Utils.findStageFlowNodes("baz", execution);
        assertNotNull(bazStages);
        assertEquals(2, bazStages.size());

        for (FlowNode baz : bazStages) {
            assertTrue(stageStatusPredicate("baz", StageStatus.getFailedAndContinued()).apply(baz));
        }

        // second-parallel should be skipped for failure.
        List<FlowNode> secondParallelStages = Utils.findStageFlowNodes("second-parallel", execution);
        assertNotNull(secondParallelStages);
        assertEquals(1, secondParallelStages.size());
        FlowNode secondParallel = secondParallelStages.get(0);
        assertTrue(stageStatusPredicate("second-parallel", StageStatus.getSkippedForFailure()).apply(secondParallel));

        // bar2 was a parallel stage skipped for failure
        List<FlowNode> bar2Stages = Utils.findStageFlowNodes("bar2", execution);
        assertNotNull(bar2Stages);
        assertEquals(2, bar2Stages.size());

        for (FlowNode bar2 : bar2Stages) {
            assertTrue(stageStatusPredicate("bar2", StageStatus.getSkippedForFailure()).apply(bar2));
        }

        // baz2 was a parallel stage skipped for failure
        List<FlowNode> baz2Stages = Utils.findStageFlowNodes("baz2", execution);
        assertNotNull(baz2Stages);
        assertEquals(2, baz2Stages.size());

        for (FlowNode baz2 : baz2Stages) {
            assertTrue(stageStatusPredicate("baz2", StageStatus.getSkippedForFailure()).apply(baz2));
        }
    }

    @Issue("JENKINS-40226")
    @Test
    public void failureBeforeStages() throws Exception {
        // This should fail whether we've got Docker available or not. Hopefully.
        expect(Result.FAILURE, "failureBeforeStages")
                .logContains("Dockerfile failed")
                .logNotContains("This should never happen")
                .go();
    }

    public static Predicate<FlowNode> syntheticStagePredicate(String stageName,
                                                        String context) {
        return stageTagPredicate(stageName, Utils.getSyntheticStageMetadata().getTagName(), context);
    }

    public static Predicate<FlowNode> stageStatusPredicate(String stageName,
                                                     String stageStatus) {
        return stageTagPredicate(stageName, Utils.getStageStatusMetadata().getTagName(), stageStatus);
    }

    public static Predicate<FlowNode> stageTagPredicate(final String stageName,
                                                  final String tagName,
                                                  final String tagValue) {
        return new Predicate<FlowNode>() {
            @Override
            public boolean apply(FlowNode input) {
                ThreadNameAction threadNameAction = input.getAction(ThreadNameAction.class);
                TagsAction tagsAction = input.getAction(TagsAction.class);
                if (input.getDisplayName().equals(stageName) ||
                        (threadNameAction != null && threadNameAction.getThreadName().equals(stageName))) {
                    if (tagsAction != null) {
                        String realTagVal = tagsAction.getTagValue(tagName);
                        if (realTagVal != null && realTagVal.equals(tagValue)) {
                            return true;
                        }
                    }
                }
                return false;
            }
        };
    }

    @Issue("JENKINS-40642")
    @Test
    public void libraryAnnotation() throws Exception {
        otherRepo.init();
        otherRepo.write("vars/myecho.groovy", "def call() {echo 'something special'}");
        otherRepo.write("vars/myecho.txt", "Says something very special!");
        otherRepo.git("add", "vars");
        otherRepo.git("commit", "--message=init");
        GlobalLibraries.get().setLibraries(Collections.singletonList(
                new LibraryConfiguration("echo-utils",
                        new SCMSourceRetriever(new GitSCMSource(null, otherRepo.toString(), "", "*", "", true)))));

        expect("libraryAnnotation")
                .logContains("something special")
                .go();
    }

    @Issue("JENKINS-38110")
    @Test
    public void librariesDirective() throws Exception {
        otherRepo.init();
        otherRepo.write("vars/myecho.groovy", "def call() {echo 'something special'}");
        otherRepo.write("vars/myecho.txt", "Says something very special!");
        otherRepo.git("add", "vars");
        otherRepo.git("commit", "--message=init");
        LibraryConfiguration firstLib = new LibraryConfiguration("echo-utils",
                new SCMSourceRetriever(new GitSCMSource(null, otherRepo.toString(), "", "*", "", true)));

        thirdRepo.init();
        thirdRepo.write("vars/whereFrom.groovy", "def call() {echo 'from another library'}");
        thirdRepo.write("vars/whereFrom.txt", "Says where it's from!");
        thirdRepo.git("add", "vars");
        thirdRepo.git("commit", "--message=init");
        LibraryConfiguration secondLib = new LibraryConfiguration("whereFrom",
                new SCMSourceRetriever(new GitSCMSource(null, thirdRepo.toString(), "", "*", "", true)));
        secondLib.setDefaultVersion("master");
        GlobalLibraries.get().setLibraries(Arrays.asList(firstLib, secondLib));

        expect("librariesDirective")
                .logContains("something special", "from another library")
                .go();
    }

    @Issue("JENKINS-42473")
    @Test
    public void folderLibraryParsing() throws Exception {
        otherRepo.init();
        otherRepo.git("checkout", "-b", "test");
        otherRepo.write("src/org/foo/Zot.groovy", "package org.foo;\n" +
                "\n" +
                "def echo(msg) {\n" +
                "  echo \"-> ${msg}\"\n" +
                "}\n");
        otherRepo.git("add", "src");
        otherRepo.git("commit", "--message=init");
        Folder folder = j.jenkins.createProject(Folder.class, "testFolder");
        LibraryConfiguration echoLib = new LibraryConfiguration("zot-stuff",
                new SCMSourceRetriever(new GitSCMSource(null, otherRepo.toString(), "", "*", "", true)));
        folder.getProperties().add(new FolderLibraries(Collections.singletonList(echoLib)));

        WorkflowRun firstRun = expect("folderLibraryParsing")
                .inFolder(folder)
                .logContains("Hello world")
                .go();

        WorkflowRun secondRun = j.buildAndAssertSuccess(firstRun.getParent());
        ExecutionModelAction action = secondRun.getAction(ExecutionModelAction.class);
        assertNotNull(action);
        ModelASTStages stages = action.getStages();
        assertNull(stages.getSourceLocation());
        assertNotNull(stages);
    }

    @Issue("JENKINS-40657")
    @Test
    public void libraryObjectInScript() throws Exception {
        otherRepo.init();
        otherRepo.write("src/org/foo/Zot.groovy", "package org.foo;\n" +
                "\n" +
                "class Zot implements Serializable {\n" +
                "  def steps\n" +
                "  Zot(steps){\n" +
                "    this.steps = steps\n" +
                "  }\n" +
                "  def echo(msg) {\n" +
                "    steps.echo \"${msg}\"\n" +
                "  }\n" +
                "}\n");
        otherRepo.git("add", "src");
        otherRepo.git("commit", "--message=init");
        GlobalLibraries.get().setLibraries(Collections.singletonList(
                new LibraryConfiguration("zot-stuff",
                        new SCMSourceRetriever(new GitSCMSource(null, otherRepo.toString(), "", "*", "", true)))));

        expect("libraryObjectInScript")
                .logContains("hello")
                .go();
    }

    @Issue("JENKINS-40657")
    @Test
    public void libraryObjectDefinedOutsidePipeline() throws Exception {
        otherRepo.init();
        otherRepo.write("src/org/foo/Zot.groovy", "package org.foo;\n" +
                "\n" +
                "class Zot implements Serializable {\n" +
                "  def steps\n" +
                "  Zot(steps){\n" +
                "    this.steps = steps\n" +
                "  }\n" +
                "  def echo(msg) {\n" +
                "    steps.echo \"${msg}\"\n" +
                "  }\n" +
                "}\n");
        otherRepo.git("add", "src");
        otherRepo.git("commit", "--message=init");
        GlobalLibraries.get().setLibraries(Collections.singletonList(
                new LibraryConfiguration("zot-stuff",
                        new SCMSourceRetriever(new GitSCMSource(null, otherRepo.toString(), "", "*", "", true)))));

        expect("libraryObjectDefinedOutsidePipeline")
                .logContains("hello");
    }

    @Issue("JENKINS-40188")
    @Test
    public void booleanParamBuildStep() throws Exception {
        env(s).set();
        expect("booleanParamBuildStep")
                .logContains("[Pipeline] { (promote)", "Scheduling project")
                .go();
    }

    @Issue("JENKINS-41118")
    @Test
    public void inCustomWorkspace() throws Exception {
        expect("inCustomWorkspace")
                .logMatches("Workspace dir is .*some-sub-dir")
                .go();
    }

    @Issue("JENKINS-41118")
    @Test
    public void inRelativeCustomWorkspace() throws Exception {
        onAllowedOS(PossibleOS.LINUX, PossibleOS.MAC);
        expect("inRelativeCustomWorkspace")
                .logMatches("Workspace dir is .*relative/custom2/workspace3")
                .go();
    }

    @Issue("JENKINS-41118")
    @Test
    public void inAbsoluteCustomWorkspace() throws Exception {
        // Since we're using a Unix path, only run on a Unix environment
        onAllowedOS(PossibleOS.LINUX, PossibleOS.MAC);
        try {
            expect("inAbsoluteCustomWorkspace")
                    .logContains("Workspace dir is /tmp/some-sub-dir")
                    .go();
        } finally {
            FileUtils.deleteDirectory(new File("/tmp/some-sub-dir"));
        }
    }

    @Issue("JENKINS-41118")
    @Test
    public void inCustomWorkspaceInStage() throws Exception {
        expect("inCustomWorkspaceInStage")
                .logMatches("Workspace dir is .*some-sub-dir")
                .go();
    }

    @Issue("JENKINS-43625")
    @Test
    public void parallelAndPostFailure() throws Exception {
        expect(Result.FAILURE, "parallelAndPostFailure")
                .logContains("[Pipeline] { (foo)", "I HAVE EXPLODED")
                .logNotContains("[first] { (Branch: first)", "[second] { (Branch: second)")
                .go();
    }

    @Issue("JENKINS-41334")
    @Test
    public void nestedParallelStages() throws Exception {
        expect("nestedParallelStages")
                .logContains("[Pipeline] { (foo)", "[first] { (Branch: first)", "[second] { (Branch: second)")
                .go();
    }

    @Issue("JENKINS-41334")
    @Test
    public void parallelStagesAgentEnvWhen() throws Exception {
        Slave s = j.createOnlineSlave();
        s.setLabelString("first-agent");
        s.getNodeProperties().add(new EnvironmentVariablesNodeProperty(new EnvironmentVariablesNodeProperty.Entry("WHICH_AGENT", "first agent")));

        Slave s2 = j.createOnlineSlave();
        s2.setLabelString("second-agent");
        s2.getNodeProperties().add(new EnvironmentVariablesNodeProperty(new EnvironmentVariablesNodeProperty.Entry("WHICH_AGENT", "second agent")));

        expect("parallelStagesAgentEnvWhen")
                .logContains("[Pipeline] { (foo)",
                        "[first] { (Branch: first)",
                        "[second] { (Branch: second)",
                        "First stage, first agent",
                        "First stage, do not override",
                        "First stage, overrode once and done",
                        "First stage, overrode twice, in first branch",
                        "First stage, overrode per nested, in first branch",
                        "First stage, declared per nested, in first branch",
                        "Second stage, second agent",
                        "Second stage, do not override",
                        "Second stage, overrode once and done",
                        "Second stage, overrode twice, in second branch",
                        "Second stage, overrode per nested, in second branch",
                        "Second stage, declared per nested, in second branch",
                        "[second] Apache Maven 3.0.1")
                .logNotContains("WE SHOULD NEVER GET HERE")
                .go();
    }

    @Issue("JENKINS-46809")
    @Test
    public void parallelStagesGroupsAndStages() throws Exception {
        Slave s = j.createOnlineSlave();
        s.setLabelString("first-agent");
        s.getNodeProperties().add(new EnvironmentVariablesNodeProperty(new EnvironmentVariablesNodeProperty.Entry("WHICH_AGENT", "first agent")));

        Slave s2 = j.createOnlineSlave();
        s2.setLabelString("second-agent");
        s2.getNodeProperties().add(new EnvironmentVariablesNodeProperty(new EnvironmentVariablesNodeProperty.Entry("WHICH_AGENT", "second agent")));

        WorkflowRun b = expect("parallelStagesGroupsAndStages")
                .logContains("[Pipeline] { (foo)",
                        "[first] { (Branch: first)",
                        "[second] { (Branch: second)",
                        "First stage, first agent",
                        "[Pipeline] [second] { (inner-first)",
                        "Second stage, second agent",
                        // Console log still shows [branch] (output), not [stage] (output), sadly.
                        "[second] Apache Maven 3.0.1",
                        "[Pipeline] [second] { (inner-second)")
                .logNotContains("WE SHOULD NEVER GET HERE")
                .go();

        FlowExecution execution = b.getExecution();
        List<FlowNode> heads = execution.getCurrentHeads();
        DepthFirstScanner scanner = new DepthFirstScanner();
        FlowNode startFoo = scanner.findFirstMatch(heads, null, Utils.isStageWithOptionalName("foo"));
        assertNotNull(startFoo);
        assertTrue(startFoo instanceof BlockStartNode);
        FlowNode endFoo = scanner.findFirstMatch(heads, null, Utils.endNodeForStage((BlockStartNode)startFoo));
        assertNotNull(endFoo);
        assertEquals(GenericStatus.SUCCESS, StatusAndTiming.computeChunkStatus(b, null, startFoo, endFoo, null));
        assertNull(endFoo.getError());

        FlowNode startFirst = scanner.findFirstMatch(heads, null, Utils.isStageWithOptionalName("first"));
        assertNotNull(startFirst);
        assertTrue(startFirst instanceof BlockStartNode);
        FlowNode endFirst = scanner.findFirstMatch(heads, null, Utils.endNodeForStage((BlockStartNode)startFirst));
        assertNotNull(endFirst);
        assertEquals(GenericStatus.SUCCESS, StatusAndTiming.computeChunkStatus(b, null, startFirst, endFirst, null));

        FlowNode startInnerFirst = scanner.findFirstMatch(heads, null, Utils.isStageWithOptionalName("inner-first"));
        assertNotNull(startInnerFirst);
        assertTrue(startInnerFirst instanceof BlockStartNode);
        FlowNode endInnerFirst = scanner.findFirstMatch(heads, null, Utils.endNodeForStage((BlockStartNode)startInnerFirst));
        assertNotNull(endInnerFirst);
        assertEquals(GenericStatus.SUCCESS, StatusAndTiming.computeChunkStatus(b, null, startInnerFirst, endInnerFirst, null));

        FlowNode startInnerSecond = scanner.findFirstMatch(heads, null, Utils.isStageWithOptionalName("inner-second"));
        assertNotNull(startInnerSecond);
        assertTrue(startInnerSecond instanceof BlockStartNode);
        FlowNode endInnerSecond = scanner.findFirstMatch(heads, null, Utils.endNodeForStage((BlockStartNode)startInnerSecond));
        assertNotNull(endInnerSecond);
        assertEquals(GenericStatus.NOT_EXECUTED, StatusAndTiming.computeChunkStatus(b, null, startInnerSecond, endInnerSecond, null));

        assertTrue(StageStatus.isSkippedStageForReason(startInnerSecond, StageStatus.getSkippedForConditional()));

        /*
        Relevant FlowNode ids:
        - 2:  FlowStartNode
        - 3:  foo stage start
        - 4:  foo stage body
        - 5:  parallel start
        - 7:  first branch start
        - 8:  second branch start
        - 9:  first stage start
        - 11: second stage start
        - 12: second stage body
        - 14: inner-first stage start (probably)
        - 44: inner-second stage start (probably)

        All three parallel stages should share 5,4,3,2.
        Sequential stages in the second branch should share 8,5,4,3,2.
         */
        assertEquals(Arrays.asList("7", "5", "4", "3", "2"), tailOfList(startFirst.getAllEnclosingIds()));
        assertEquals(Arrays.asList("12", "11", "8", "5", "4", "3", "2"), tailOfList(startInnerFirst.getAllEnclosingIds()));
        assertEquals(Arrays.asList("12", "11", "8", "5", "4", "3", "2"), tailOfList(startInnerSecond.getAllEnclosingIds()));
    }

    private List<String> tailOfList(List<String> l) {
        return Collections.unmodifiableList(l.subList(1, l.size()));
    }

    @Issue("JENKINS-46112")
    @Test
    public void logActionPresentForError() throws Exception {
        WorkflowRun r = expect(Result.FAILURE, "logActionPresentForError").go();
        FlowExecution execution = r.getExecution();
        assertNotNull(execution);
        Collection<FlowNode> heads = execution.getCurrentHeads();

        DepthFirstScanner scanner = new DepthFirstScanner();

        FlowNode n = scanner.findFirstMatch(heads, null, new Predicate<FlowNode>() {
            @Override
            public boolean apply(FlowNode input) {
                return input instanceof StepAtomNode && ((StepAtomNode) input).getDescriptor() instanceof ErrorStep.DescriptorImpl;
            }
        });
        assertNotNull(n);
        LogAction l = n.getAction(LogAction.class);
        assertNotNull(l);
    }

    @Test
    public void mapCallsWithMethodCallValues() throws Exception {
        WorkflowRun b = expect("mapCallsWithMethodCallValues")
                .logContains("[Pipeline] { (foo)", "hello")
                .logNotContains("[Pipeline] { (" + SyntheticStageNames.postBuild() + ")")
                .go();

        WorkflowJob p = b.getParent();

        BuildDiscarderProperty bdp = p.getProperty(BuildDiscarderProperty.class);
        assertNotNull(bdp);
        BuildDiscarder strategy = bdp.getStrategy();
        assertNotNull(strategy);
        assertEquals(LogRotator.class, strategy.getClass());
        LogRotator lr = (LogRotator) strategy;
        assertEquals(1, lr.getNumToKeep());

    }

    @Issue("JENKINS-43035")
    @Test
    public void libraryObjectImportInWhenExpr() throws Exception {
        otherRepo.init();
        otherRepo.write("src/org/foo/Zot.groovy", "package org.foo;\n" +
                "\n" +
                "class Zot implements Serializable {\n" +
                "  def steps\n" +
                "  Zot(steps){\n" +
                "    this.steps = steps\n" +
                "  }\n" +
                "  def echo(msg) {\n" +
                "    steps.echo \"${msg}\"\n" +
                "  }\n" +
                "}\n");
        otherRepo.git("add", "src");
        otherRepo.git("commit", "--message=init");
        GlobalLibraries.get().setLibraries(Collections.singletonList(
                new LibraryConfiguration("zot-stuff",
                        new SCMSourceRetriever(new GitSCMSource(null, otherRepo.toString(), "", "*", "", true)))));

        expect("libraryObjectImportInWhenExpr")
                .logContains("hello")
                .go();
    }

    @Issue("JENKINS-45198")
    @Test
    public void scmEnvVars() throws Exception {
        expect("scmEnvVars")
                // workflow-scm-step 2.6+, git 3.3.1+
                .logNotContains("GIT_COMMIT is null")
                .go();
    }

    @Issue("JENKINS-46547")
    @Test
    public void pipelineDefinedInLibrary() throws Exception {
        otherRepo.init();
        otherRepo.write("vars/fromLib.groovy", pipelineSourceFromResources("libForPipelineDefinedInLibrary"));
        otherRepo.git("add", "vars");
        otherRepo.git("commit", "--message=init");
        LibraryConfiguration firstLib = new LibraryConfiguration("from-lib",
                new SCMSourceRetriever(new GitSCMSource(null, otherRepo.toString(), "", "*", "", true)));

        GlobalLibraries.get().setLibraries(Arrays.asList(firstLib));

        expect("pipelineDefinedInLibrary")
                .logContains("[Pipeline] { (One)", "[Pipeline] { (Two)")
                .logNotContains("World")
                .go();
    }

    @Issue("JENKINS-46547")
    @Test
    public void pipelineDefinedInLibraryInFolder() throws Exception {
        otherRepo.init();
        otherRepo.write("vars/fromLib.groovy", pipelineSourceFromResources("libForPipelineDefinedInLibrary"));
        otherRepo.git("add", "vars");
        otherRepo.git("commit", "--message=init");
        LibraryConfiguration firstLib = new LibraryConfiguration("from-lib",
                new SCMSourceRetriever(new GitSCMSource(null, otherRepo.toString(), "", "*", "", true)));
        Folder folder = j.jenkins.createProject(Folder.class, "libInFolder");
        folder.getProperties().add(new FolderLibraries(Collections.singletonList(firstLib)));

        expect("pipelineDefinedInLibrary")
                .inFolder(folder)
                .logContains("[Pipeline] { (One)", "[Pipeline] { (Two)")
                .logNotContains("World")
                .go();
    }

    @Issue("JENKINS-46547")
    @Test
    public void multiplePipelinesDefinedInLibrary() throws Exception {
        otherRepo.init();
        otherRepo.write("vars/fromLib.groovy", pipelineSourceFromResources("libForMultiplePipelinesDefinedInLibrary"));
        otherRepo.git("add", "vars");
        otherRepo.git("commit", "--message=init");
        LibraryConfiguration firstLib = new LibraryConfiguration("from-lib",
                new SCMSourceRetriever(new GitSCMSource(null, otherRepo.toString(), "", "*", "", true)));

        GlobalLibraries.get().setLibraries(Arrays.asList(firstLib));

        WorkflowRun firstRun = expect("multiplePipelinesDefinedInLibraryFirst")
                .runFromRepo(false)
                .logContains("[Pipeline] { (One)", "[Pipeline] { (Two)")
                .logNotContains("World")
                .go();

        ExecutionModelAction firstAction = firstRun.getAction(ExecutionModelAction.class);
        assertNotNull(firstAction);
        ModelASTStages firstStages = firstAction.getStages();
        assertNotNull(firstStages);
        assertEquals(2, firstStages.getStages().size());

        WorkflowRun secondRun = expect("multiplePipelinesDefinedInLibrarySecond")
                .runFromRepo(false)
                .logContains("[Pipeline] { (Different)", "This is the alternative pipeline")
                .go();

        ExecutionModelAction secondAction = secondRun.getAction(ExecutionModelAction.class);
        assertNotNull(secondAction);
        ModelASTStages secondStages = secondAction.getStages();
        assertNotNull(secondStages);
        assertEquals(1, secondStages.getStages().size());
    }

    @Issue("JENKINS-46547")
    @Test
    public void multiplePipelinesExecutedInLibraryShouldFail() throws Exception {
        otherRepo.init();
        otherRepo.write("vars/fromLib.groovy", pipelineSourceFromResources("libForMultiplePipelinesExecutedInLibrary"));
        otherRepo.git("add", "vars");
        otherRepo.git("commit", "--message=init");
        LibraryConfiguration firstLib = new LibraryConfiguration("from-lib",
                new SCMSourceRetriever(new GitSCMSource(null, otherRepo.toString(), "", "*", "", true)));

        GlobalLibraries.get().setLibraries(Arrays.asList(firstLib));

        expect(Result.FAILURE, "pipelineDefinedInLibrary")
                .logContains("java.lang.IllegalStateException: Only one pipeline { ... } block can be executed in a single run")
                .go();
    }

    @Test
    public void fromEvaluate() throws Exception {
        expect("fromEvaluate")
                .otherResource("whenAnd.groovy", "whenAnd.groovy")
                .logContains("[Pipeline] { (One)", "[Pipeline] { (Two)")
                .logNotContains("World")
                .go();
    }

    @Issue("JENKINS-47193")
    @Test
    public void classInJenkinsfile() throws Exception {
        expect("classInJenkinsfile")
                .logContains("[Pipeline] { (foo)", "hello")
                .logNotContains("[Pipeline] { (" + SyntheticStageNames.postBuild() + ")")
                .go();
    }

    @Issue("JENKINS-47109")
    @Test
    public void parallelStagesFailFast() throws Exception {
        expect(Result.ABORTED, "parallelStagesFailFast")
                .logContains("[Pipeline] { (foo)",
                        "[first] { (Branch: first)",
                        "[Pipeline] [first] { (first)",
                        "[second] { (Branch: second)",
                        "[Pipeline] [second] { (second)",
                        "SECOND STAGE ABORTED")
                .hasFailureCase()
                .go();
    }

    @Issue("JENKINS-47783")
    @Test
    public void parallelStagesHaveStatusWhenSkipped() throws Exception {
        WorkflowRun b = expect(Result.FAILURE, "parallelStagesHaveStatusWhenSkipped")
                .logContains("[Pipeline] { (bar)",
                        "[Pipeline] { (foo)",
                        "[first] { (Branch: first)",
                        "[Pipeline] [first] { (first)",
                        "[second] { (Branch: second)",
                        "[Pipeline] [second] { (second)")
                .hasFailureCase()
                .go();

        FlowExecution execution = b.getExecution();
        List<FlowNode> heads = execution.getCurrentHeads();
        DepthFirstScanner scanner = new DepthFirstScanner();
        FlowNode startFoo = scanner.findFirstMatch(heads, null, Utils.isStageWithOptionalName("foo"));
        assertNotNull(startFoo);
        assertTrue(startFoo instanceof BlockStartNode);
        FlowNode endFoo = scanner.findFirstMatch(heads, null, Utils.endNodeForStage((BlockStartNode)startFoo));
        assertNotNull(endFoo);
        assertEquals(GenericStatus.FAILURE, StatusAndTiming.computeChunkStatus(b, null, startFoo, endFoo, null));
        assertNotNull(endFoo.getError());

        FlowNode startFirst = scanner.findFirstMatch(heads, null, Utils.isStageWithOptionalName("first"));
        assertNotNull(startFirst);
        assertTrue(startFirst instanceof BlockStartNode);
        FlowNode endFirst = scanner.findFirstMatch(heads, null, Utils.endNodeForStage((BlockStartNode)startFirst));
        assertNotNull(endFirst);
        assertEquals(GenericStatus.FAILURE, StatusAndTiming.computeChunkStatus(b, null, startFirst, endFirst, null));

        FlowNode startSecond = scanner.findFirstMatch(heads, null, Utils.isStageWithOptionalName("second"));
        assertNotNull(startSecond);
        assertTrue(startSecond instanceof BlockStartNode);
        FlowNode endSecond = scanner.findFirstMatch(heads, null, Utils.endNodeForStage((BlockStartNode)startSecond));
        assertNotNull(endSecond);
        assertEquals(GenericStatus.FAILURE, StatusAndTiming.computeChunkStatus(b, null, startSecond, endSecond, null));

        assertTrue(StageStatus.isSkippedStageForReason(startFirst, StageStatus.getSkippedForFailure()));
        assertTrue(StageStatus.isSkippedStageForReason(startSecond, StageStatus.getSkippedForFailure()));
        assertTrue(StageStatus.isSkippedStageForReason(startFirst, StageStatus.getSkippedForFailure()));
        }

    @Issue("JENKINS-46597")
    @Test
    public void parallelStagesShoudntTriggerNSE() throws Exception {
        expect("parallelStagesShouldntTriggerNSE")
                .logContains("ninth branch")
                .go();
    }

    @Issue("JENKINS-46252")
    @Test
    public void declarativeJobAction() throws Exception {
        WorkflowRun r1 = expect("simplePipeline").go();
        WorkflowJob j1 = r1.getParent();
        assertNotNull(j1.getAction(DeclarativeJobAction.class));

        WorkflowJob j2 = j.createProject(WorkflowJob.class, "nonDeclarative");
        j2.setDefinition(new CpsFlowDefinition("echo 'hi'", true));
        j.buildAndAssertSuccess(j2);
        assertNull(j2.getAction(DeclarativeJobAction.class));
    }

    @Issue("JENKINS-49070")
    @Test
    public void bigDecimalConverts() throws Exception {
        WorkflowRun b = expect("bigDecimalConverts").go();

        ExecutionModelAction action = b.getAction(ExecutionModelAction.class);
        assertNotNull(action);
        ModelASTStages stages = action.getStages();
        assertNull(stages.getSourceLocation());
        assertNotNull(stages);

        assertEquals(1, stages.getStages().size());

        ModelASTStage stage = stages.getStages().get(0);
        assertNull(stage.getSourceLocation());
        assertNotNull(stage);

        assertEquals(1, stage.getBranches().size());

        ModelASTBranch firstBranch = branchForName("default", stage.getBranches());
        assertNotNull(firstBranch);
        assertNull(firstBranch.getSourceLocation());
        assertNotNull(firstBranch);
        assertEquals(1, firstBranch.getSteps().size());
        ModelASTStep firstStep = firstBranch.getSteps().get(0);
        assertNull(firstStep.getSourceLocation());
        assertEquals("junit", firstStep.getName());
        assertTrue(firstStep.getArgs() instanceof ModelASTNamedArgumentList);
        ModelASTNamedArgumentList args = (ModelASTNamedArgumentList)firstStep.getArgs();

        ModelASTValue val = null;
        for (ModelASTKey k : args.getArguments().keySet()) {
            if (k.getKey().equals("healthScaleFactor")) {
                val = args.getArguments().get(k);
            }
        }

        assertNotNull(val);

        Object realVal = val.getValue();

        assertEquals(new Double("1.0"), realVal);

    }

    @Issue("JENKINS-46809")
    @Test
    public void topLevelStageGroup() throws Exception {
        expect("topLevelStageGroup")
                .logContains("[Pipeline] { (foo)",
                        "In stage bar in group foo",
                        "In stage baz in group foo")
                .go();
    }

    @Issue("JENKINS-51962")
    @Test
    public void failureInFirstOfSequential() throws Exception {
        expect(Result.FAILURE, "failureInFirstOfSequential")
                .logNotContains("Executing stage B")
                .go();
    }
}