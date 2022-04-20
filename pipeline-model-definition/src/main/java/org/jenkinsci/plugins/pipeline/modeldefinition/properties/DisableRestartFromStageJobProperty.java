package org.jenkinsci.plugins.pipeline.modeldefinition.properties;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import jenkins.model.OptionalJobProperty;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

public class DisableRestartFromStageJobProperty extends OptionalJobProperty<WorkflowJob> {

    private boolean disableRestartFromStage = false;

    @DataBoundConstructor
    public DisableRestartFromStageJobProperty(){}

    public boolean isDisableRestartFromStage() {
        return disableRestartFromStage;
    }

    @DataBoundSetter
    public void setDisableRestartFromStage(boolean disableRestartFromStage) {
        this.disableRestartFromStage = disableRestartFromStage;
    }

    @Extension
    @Symbol("disableRestartFromStage")
    public static class DescriptorImpl extends OptionalJobPropertyDescriptor {
        @Override
        @NonNull
        public String getDisplayName() {
            return "Sta god";
        }
    }
}
