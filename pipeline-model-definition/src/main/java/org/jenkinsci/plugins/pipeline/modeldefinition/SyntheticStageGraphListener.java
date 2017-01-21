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

import org.jenkinsci.plugins.pipeline.SyntheticStage;
import org.jenkinsci.plugins.workflow.actions.TagsAction;
import org.jenkinsci.plugins.workflow.cps.nodes.StepStartNode;
import org.jenkinsci.plugins.workflow.flow.GraphListener;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.support.steps.StageStep;

import java.io.IOException;

public final class SyntheticStageGraphListener implements GraphListener {
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
        TagsAction tagsAction = currentNode.getAction(TagsAction.class);

        if (tagsAction == null) {
            tagsAction = new TagsAction();
            tagsAction.addTag(SyntheticStage.TAG_NAME, syntheticContext);
            currentNode.addAction(tagsAction);
        } else if (tagsAction.getTagValue(SyntheticStage.TAG_NAME) == null) {
            tagsAction.addTag(SyntheticStage.TAG_NAME, syntheticContext);
            try {
                currentNode.save();
            } catch (IOException e) {
                // TODO: should probably do something here? Would prefer to be calling FlowNode.persistSafe() but it's private
            }
        }
    }
}
