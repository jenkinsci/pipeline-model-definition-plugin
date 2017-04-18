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
import jenkins.plugins.git.GitSCMSource;
import org.apache.commons.io.FileUtils;
import org.jenkinsci.plugins.pipeline.modeldefinition.actions.ExecutionModelAction;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTBranch;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTNamedArgumentList;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTScriptBlock;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTSingleArgument;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTStage;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTStages;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTStep;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTTreeStep;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTValue;
import org.jenkinsci.plugins.workflow.actions.ErrorAction;
import org.jenkinsci.plugins.workflow.actions.TagsAction;
import org.jenkinsci.plugins.workflow.cps.nodes.StepStartNode;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.graph.BlockStartNode;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.graphanalysis.DepthFirstScanner;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.libs.FolderLibraries;
import org.jenkinsci.plugins.workflow.libs.GlobalLibraries;
import org.jenkinsci.plugins.workflow.libs.LibraryConfiguration;
import org.jenkinsci.plugins.workflow.libs.SCMSourceRetriever;
import org.jenkinsci.plugins.workflow.pipelinegraphanalysis.GenericStatus;
import org.jenkinsci.plugins.workflow.pipelinegraphanalysis.StatusAndTiming;
import org.junit.BeforeClass;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;

import java.util.Arrays;
import java.io.File;
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
    public void parallelStagesHaveStatus() throws Exception {
        WorkflowRun b = expect(Result.FAILURE, "parallelStagesHaveStatus")
                .logContains("[Pipeline] { (foo)",
                        "[first] { (Branch: first)",
                        "[second] { (Branch: second)")
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
        assertNotNull(endFirst.getError());

        TagsAction nestedTags = startFirst.getAction(TagsAction.class);
        assertNotNull(nestedTags);
        assertNotNull(nestedTags.getTags());
        assertFalse(nestedTags.getTags().isEmpty());
        assertTrue(nestedTags.getTags().containsKey(Utils.getStageStatusMetadata().getTagName()));
        assertEquals(Utils.getStageStatusMetadata().getFailedAndContinued(),
                nestedTags.getTags().get(Utils.getStageStatusMetadata().getTagName()));

        TagsAction parentTags = startFoo.getAction(TagsAction.class);
        assertNotNull(parentTags);
        assertNotNull(parentTags.getTags());
        assertFalse(parentTags.getTags().isEmpty());
        assertTrue(parentTags.getTags().containsKey(Utils.getStageStatusMetadata().getTagName()));
        assertEquals(Utils.getStageStatusMetadata().getFailedAndContinued(),
                parentTags.getTags().get(Utils.getStageStatusMetadata().getTagName()));

        FlowNode shouldBeFailedNode = execution.getNode("" + (Integer.valueOf(endFirst.getId()) - 1));
        assertNotNull(shouldBeFailedNode);
        assertNotNull(shouldBeFailedNode.getError());
    }

    @Issue("JENKINS-42039")
    @Test
    public void skipAfterUnstableWithOption() throws Exception {
        WorkflowRun b = expect(Result.UNSTABLE, "skipAfterUnstableIfOption")
                .logContains("[Pipeline] { (foo)", "hello", "[Pipeline] { (bar)")
                .logNotContains("goodbye")
                .go();

        FlowExecution execution = b.getExecution();
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

        assertTrue(b.getExecution().getCauseOfFailure() != null);

        FlowExecution execution = b.getExecution();

        Collection<FlowNode> heads = execution.getCurrentHeads();

        DepthFirstScanner scanner = new DepthFirstScanner();

        assertNull(scanner.findFirstMatch(heads, stageStatusPredicate("foo", Utils.getStageStatusMetadata().getSkippedForFailure())));
        assertNotNull(scanner.findFirstMatch(heads, stageStatusPredicate("foo", Utils.getStageStatusMetadata().getFailedAndContinued())));
        assertNotNull(scanner.findFirstMatch(heads, stageStatusPredicate("bar", Utils.getStageStatusMetadata().getSkippedForFailure())));
        assertNotNull(scanner.findFirstMatch(heads, stageStatusPredicate("baz", Utils.getStageStatusMetadata().getSkippedForFailure())));
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
                return input.getDisplayName().equals(stageName) &&
                        input.getAction(TagsAction.class) != null &&
                        input.getAction(TagsAction.class).getTagValue(tagName) != null &&
                        input.getAction(TagsAction.class).getTagValue(tagName).equals(tagValue);
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

        WorkflowRun secondRun = firstRun.getParent().scheduleBuild2(0).waitForStart();
        j.assertBuildStatusSuccess(j.waitForCompletion(secondRun));
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
                "    steps.sh \"echo ${msg}\"\n" +
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
                "    steps.sh \"echo ${msg}\"\n" +
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
}
