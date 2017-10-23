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

package org.jenkinsci.plugins.pipeline.modeldefinition.actions;

import hudson.model.InvisibleAction;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTStages;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ExecutionModelAction extends InvisibleAction {
    private ModelASTStages stages;
    private String stagesUUID;
    private List<ModelASTStages> stagesList = new ArrayList<>();

    public ExecutionModelAction(ModelASTStages s) {
        this.stagesList.add(s);
        this.stages = null;
    }

    public ExecutionModelAction(List<ModelASTStages> s) {
        this.stagesList.addAll(s);
        this.stages = null;
    }

    protected Object readResolve() throws IOException {
        if (this.stages != null) {
            if (this.stagesList == null) {
                this.stagesList = new ArrayList<>();
            }
            this.stagesList.add(stages);
            
            this.stages = null;
        }
        return this;
    }

    public ModelASTStages getStages() {
        for (ModelASTStages s : stagesList) {
            if (s.getUuid().toString().equals(stagesUUID)) {
                return s;
            }
        }
        return null;
    }

    public String getStagesUUID() {
        return stagesUUID;
    }

    public void setStagesUUID(String s) {
        this.stagesUUID = s;
    }

    public List<ModelASTStages> getStagesList() {
        return Collections.unmodifiableList(stagesList);
    }

    public void addStages(ModelASTStages s) {
        this.stagesList.add(s);
    }
}
