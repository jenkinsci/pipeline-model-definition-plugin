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
 *
 */

package org.jenkinsci.plugins.pipeline.modeldefinition.when.impl

import hudson.scm.ChangeLogSet
import org.jenkinsci.plugins.pipeline.modeldefinition.when.DeclarativeStageConditional
import org.jenkinsci.plugins.pipeline.modeldefinition.when.DeclarativeStageConditionalScript
import org.jenkinsci.plugins.workflow.cps.CpsScript
import org.jenkinsci.plugins.workflow.support.steps.build.RunWrapper

abstract class AbstractChangelogConditionalScript<S extends DeclarativeStageConditional<S>> extends DeclarativeStageConditionalScript<S> {

    AbstractChangelogConditionalScript(CpsScript s, S c) {
        super(s, c)
    }

    @Override
    boolean evaluate() {
        initializeEval()
        RunWrapper run = this.script.getProperty("currentBuild")
        if (run != null) {
            List<ChangeLogSet<? extends ChangeLogSet.Entry>> changeSets = run.getChangeSets()
            if (changeSets.isEmpty()) {
                if (run.number <= 1) {
                    script.echo "Warning, empty changelog. Probably because this is the first build." //TODO JENKINS-46086
                } else {
                    script.echo "Warning, empty changelog. Have you run checkout?"
                }
                return false
            }
            for (int i = 0; i < changeSets.size(); i++) { //TODO switch to .any when #174 lands.
                def set = changeSets.get(i)
                def iterator = set.iterator()
                while (iterator.hasNext()) {
                    def change = iterator.next()
                    if (matches(change)) {
                        return true
                    }
                }
            }
        }
        return false
    }

    abstract boolean matches(ChangeLogSet.Entry change)
    void initializeEval() {}
}
