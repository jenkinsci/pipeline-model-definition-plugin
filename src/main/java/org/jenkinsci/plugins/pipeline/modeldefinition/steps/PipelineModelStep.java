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

package org.jenkinsci.plugins.pipeline.modeldefinition.steps;

import groovy.lang.Closure;
import hudson.Extension;
import org.jenkinsci.plugins.workflow.cps.CpsThread;
import org.jenkinsci.plugins.workflow.cps.steps.ingroovy.GroovyStep;
import org.jenkinsci.plugins.workflow.cps.steps.ingroovy.GroovyStepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.kohsuke.stapler.DataBoundConstructor;

public class PipelineModelStep extends GroovyStep {
    private final Closure closure;

    @DataBoundConstructor
    public PipelineModelStep(Closure closure) {
        this.closure = closure;
    }

    public Closure getClosure() {
        return closure;
    }

    @Override
    public StepExecution start(StepContext context) throws Exception {
        CpsThread t = CpsThread.current();
        if (t==null)    throw new IllegalStateException("Cannot be used outside CPS pipeline engine");
        t.getExecution().getTrustedShell().getClassLoader().loadClass("org.jenkinsci.plugins.pipeline.modeldefinition.ClosureModelTranslator");
        t.getExecution().getTrustedShell().getClassLoader().loadClass("org.jenkinsci.plugins.pipeline.modeldefinition.PropertiesToMapTranslator");

        return super.start(context);
    }

    @Extension
    public static class DescriptorImpl extends GroovyStepDescriptor {
        @Override
        public String getFunctionName() {
            return "pipeline";
        }

        @Override
        public String getDisplayName() {
            return "Declarative Pipeline configuration and invocation";
        }

        @Override
        public boolean takesImplicitBlockArgument() {
            return false;
        }

    }
}
