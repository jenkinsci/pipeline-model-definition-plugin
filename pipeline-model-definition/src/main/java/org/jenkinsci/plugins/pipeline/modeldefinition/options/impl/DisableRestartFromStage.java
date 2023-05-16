package org.jenkinsci.plugins.pipeline.modeldefinition.options.impl;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.pipeline.modeldefinition.options.DeclarativeOption;
import org.jenkinsci.plugins.pipeline.modeldefinition.options.DeclarativeOptionDescriptor;
import org.kohsuke.stapler.DataBoundConstructor;

public class DisableRestartFromStage extends DeclarativeOption {

    @DataBoundConstructor
    public DisableRestartFromStage() {}

    @Extension @Symbol("disableRestartFromStage")
    public static class DescriptorImpl extends DeclarativeOptionDescriptor {
        @Override
        @NonNull
        public String getDisplayName() {
            return "Disable the ability to restart this Pipeline from a specific stage";
        }

        @Override
        public boolean canUseInStage() {
            return false;
        }
    }
}
