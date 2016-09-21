package org.jenkinsci.plugins.pipeline.modeldefinition.steps;

import hudson.Extension;
import org.jenkinsci.plugins.workflow.cps.steps.ingroovy.GroovyStep;
import org.jenkinsci.plugins.workflow.cps.steps.ingroovy.GroovyStepDescriptor;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * @author Kohsuke Kawaguchi
 */
public class PipelineStep extends GroovyStep {
    @DataBoundConstructor
    public PipelineStep() {}

    // no parameter

    @Extension
    public static class DescriptorImpl extends GroovyStepDescriptor {
        @Override
        public String getFunctionName() {
            return "pipeline";
        }

        @Override
        public boolean takesImplicitBlockArgument() {
            return true;
        }
    }
}
