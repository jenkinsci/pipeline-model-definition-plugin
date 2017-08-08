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
import org.jenkinsci.plugins.workflow.cps.CpsScript

class ChangelogConditionalScript extends AbstractChangelogConditionalScript<ChangelogConditional> {

    Class<?> gitChangeSetClass

    ChangelogConditionalScript(CpsScript s, ChangelogConditional c) {
        super(s, c)
    }

    @Override
    void initializeEval() {
        //Probably running with git plugin
        try {
            gitChangeSetClass = Class.forName("hudson.plugins.git.GitChangeSet")
        } catch (ClassNotFoundException cnfe) {
            gitChangeSetClass = null;
        }
    }

    @Override
    boolean matches(ChangeLogSet.Entry change) {
        //Future enhancement could be to somehow return the capture groups as env vars or something
        //But it's probably simpler to make a build step that recaptures that information

        if (gitChangeSetClass != null && change?.getClass()?.isAssignableFrom(gitChangeSetClass)) {
            script.echo "We are running git"
            script.echo "Evaluating " + change.title
            script.echo "And " + change.comment
            return describable.pattern.matcher(change.title).matches() || describable.multiLinePattern.matcher(change.comment).matches()
        } else {
            //Something generic
            script.echo "We are running something generic"
            script.echo "Evaluating " + change.msg
            return describable.pattern.matcher(change.msg).matches()
        }
    }
}
