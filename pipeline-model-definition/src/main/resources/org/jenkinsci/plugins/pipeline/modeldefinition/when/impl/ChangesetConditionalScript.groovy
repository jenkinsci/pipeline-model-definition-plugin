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
import org.apache.tools.ant.DirectoryScanner
import org.jenkinsci.plugins.pipeline.modeldefinition.Utils
import org.jenkinsci.plugins.workflow.cps.CpsScript

class ChangesetConditionalScript extends AbstractChangelogConditionalScript<ChangesetConditional> {
    String glob

    ChangesetConditionalScript(CpsScript s, ChangesetConditional c) {
        super(s, c)
    }

    @Override
    void initializeEval() {
        glob = (String)script.evaluate(Utils.prepareForEvalToString(describable.glob)) //TODO change when #174 lands
        glob = glob.replace('\\', '/')
    }

    @Override
    boolean matches(ChangeLogSet.Entry change) {
        def iterator = change.affectedPaths.iterator()
        while (iterator.hasNext()) { //TODO switch to .any when #174 lands
            String path = iterator.next();
            path = path.replace('\\', '/')
            if (DirectoryScanner.match(glob, path, describable.isCaseSensitive())) {
                return true
            }
        }
        return false
    }
}
