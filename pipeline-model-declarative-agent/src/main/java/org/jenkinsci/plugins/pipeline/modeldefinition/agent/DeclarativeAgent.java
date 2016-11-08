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

package org.jenkinsci.plugins.pipeline.modeldefinition.agent;

import hudson.ExtensionPoint;
import hudson.model.AbstractDescribableImpl;
import org.jenkinsci.plugins.workflow.cps.CpsScript;
import org.jenkinsci.plugins.workflow.cps.CpsThread;

import java.io.Serializable;

/**
 * Implementations for {@link DeclarativeAgentDescriptor} - pluggable agent backends for Declarative Pipelines.
 *
 * @author Andrew Bayer
 */
public abstract class DeclarativeAgent extends AbstractDescribableImpl<DeclarativeAgent> implements Serializable, ExtensionPoint {

    /**
     * ONLY TO BE RUN FROM WITHIN A CPS THREAD. Parses the script source and loads it.
     * TODO: Decide if we want to cache the resulting objects or just *shrug* and re-parse them every time.
     *
     * @return The script object for this declarative agent.
     * @throws Exception if the script source cannot be loaded or we're called from outside a CpsThread.
     */
    @SuppressWarnings("unchecked")
    public DeclarativeAgentScript getScript(CpsScript cpsScript) throws Exception {
        CpsThread c = CpsThread.current();
        if (c == null)
            throw new IllegalStateException("Expected to be called from CpsThread");

        return (DeclarativeAgentScript) cpsScript.getClass()
                .getClassLoader()
                .loadClass(getDescriptor().getDeclarativeAgentScriptClass())
                .getConstructor(CpsScript.class, DeclarativeAgent.class)
                .newInstance(cpsScript, this);
    }

    @Override
    public DeclarativeAgentDescriptor getDescriptor() {
        return (DeclarativeAgentDescriptor) super.getDescriptor();
    }
}
