/*
 * The MIT License
 *
 * Copyright (c) 2017, CloudBees, Inc.
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

import hudson.model.InvisibleAction;
import hudson.model.Run;
import jenkins.model.RunAction2;
import org.jenkinsci.plugins.pipeline.SyntheticStage;
import org.jenkinsci.plugins.workflow.actions.TagsAction;
import org.jenkinsci.plugins.workflow.cps.nodes.StepStartNode;
import org.jenkinsci.plugins.workflow.flow.GraphListener;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.support.steps.StageStep;

import java.io.IOException;
import java.util.logging.Logger;

import static java.util.logging.Level.WARNING;

public final class SyntheticStageGraphListener implements GraphListener {
    private static final Logger LOGGER = Logger.getLogger(SyntheticStageGraphListener.class.getName());

    @Override
    public void onNewHead(FlowNode node) {
        if (node != null && node instanceof StepStartNode &&
                ((StepStartNode) node).getDescriptor() instanceof StageStep.DescriptorImpl) {
            if (SyntheticStageNames.preStages().contains(node.getDisplayName()) ||
                    SyntheticStageNames.postStages().contains(node.getDisplayName())) {

                if (SyntheticStageNames.preStages().contains(node.getDisplayName())) {
                    attachTag(node, SyntheticStage.getPre());
                }
                if (SyntheticStageNames.postStages().contains(node.getDisplayName())) {
                    attachTag(node, SyntheticStage.getPost());
                }
            }
        }
    }

    private void attachTag(FlowNode currentNode, String syntheticContext) {
        TagsAction tagsAction = currentNode.getPersistentAction(TagsAction.class);
        if (tagsAction == null) {
            tagsAction = new TagsAction();
            tagsAction.addTag(SyntheticStage.TAG_NAME, syntheticContext);
            currentNode.addAction(tagsAction);
        } else if (tagsAction.getTagValue(SyntheticStage.TAG_NAME) == null) {
            tagsAction.addTag(SyntheticStage.TAG_NAME, syntheticContext);
            try {
                currentNode.save();
            } catch (IOException e) {
                LOGGER.log(WARNING, "failed to save actions for FlowNode id=" + currentNode.getId(), e);
            }
        }
    }

    public static class GraphListenerAction extends InvisibleAction implements RunAction2 {
        @Override
        public void onLoad(Run<?, ?> r) {
            if (r instanceof WorkflowRun) {
                WorkflowRun run = (WorkflowRun) r;
                attachListener(run);
            }
        }

        @Override
        public void onAttached(Run<?, ?> r) {
            if (r instanceof WorkflowRun) {
                WorkflowRun run = (WorkflowRun) r;
                attachListener(run);
            }
        }

        private void attachListener(WorkflowRun run) {
            if (run != null && run.getExecution() != null && !run.getExecution().isComplete()) {
                run.getExecution().addListener(new SyntheticStageGraphListener());
            }
        }
    }
}
