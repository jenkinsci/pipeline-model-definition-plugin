package org.jenkinsci.plugins.pipeline.modeldefinition.options.impl;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import hudson.Extension;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.pipeline.modeldefinition.actions.RestartDeclarativePipelineAction;
import org.jenkinsci.plugins.pipeline.modeldefinition.options.DeclarativeOption;
import org.jenkinsci.plugins.pipeline.modeldefinition.options.DeclarativeOptionDescriptor;
import org.kohsuke.stapler.DataBoundConstructor;

import java.util.logging.Logger;

public class DisableRestartFromStage extends DeclarativeOption {

    private Boolean disableRestartFromStage;

    @DataBoundConstructor
    public DisableRestartFromStage(@Nullable Boolean disableRestartFromStage) {
        if(disableRestartFromStage == null || disableRestartFromStage){
            this.disableRestartFromStage = Boolean.TRUE;
        } else {
            this.disableRestartFromStage = Boolean.FALSE;
        }
    }

    public boolean isDisableRestartFromStage() { return disableRestartFromStage; }

    @Extension @Symbol("disableRestartFromStage")
    public static class DescriptorImpl extends DeclarativeOptionDescriptor {
        @Override
        @NonNull
        public String getDisplayName() {
            return "Disable Restart From Stage button for single pipeline";
        }

        @Override
        public boolean canUseInStage() {
            return false;
        }
    }
}
