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
 */

package org.jenkinsci.plugins.pipeline.modeldefinition.model;

import org.jenkinsci.plugins.pipeline.StageStatus;

import javax.annotation.Nonnull;
import java.io.Serializable;

/**
 * Used to mark why a stage was skipped for internal purposes, allowing us to abstract away handling of skipped stages
 * without needing chains of if/else or cases to get the different behaviors.
 */
public abstract class SkippedStageReason implements Serializable {
    protected String stageName;

    public SkippedStageReason(@Nonnull String stageName) {
        this.stageName = stageName;
    }

    public boolean isNotExecutedNode() {
        return false;
    }

    @Nonnull
    public abstract String getMessage();

    @Nonnull
    public abstract String getStageStatus();

    @Nonnull
    public abstract SkippedStageReason cloneWithNewStage(@Nonnull String newStage);

    @Nonnull
    public String getStageName() {
        return stageName;
    }

    public static class Failure extends SkippedStageReason {
        public Failure(@Nonnull String stageName) {
            super(stageName);
        }

        @Override
        @Nonnull
        public String getMessage() {
            return Messages.SkippedStageReason_FAILURE_Message(stageName);
        }

        @Override
        @Nonnull
        public String getStageStatus() {
            return StageStatus.getSkippedForFailure();
        }

        @Override
        @Nonnull
        public SkippedStageReason cloneWithNewStage(@Nonnull String newStage) {
            return new Failure(newStage);
        }

        private static final long serialVersionUID = 1L;
    }

    public static class Unstable extends SkippedStageReason {
        public Unstable(@Nonnull String stageName) {
            super(stageName);
        }

        @Override
        @Nonnull
        public String getMessage() {
            return Messages.SkippedStageReason_UNSTABLE_Message(stageName);
        }

        @Override
        @Nonnull
        public String getStageStatus() {
            return StageStatus.getSkippedForUnstable();
        }

        @Override
        @Nonnull
        public SkippedStageReason cloneWithNewStage(@Nonnull String newStage) {
            return new Unstable(newStage);
        }

        private static final long serialVersionUID = 1L;
    }

    public static class When extends SkippedStageReason {
        public When(@Nonnull String stageName) {
            super(stageName);
        }

        @Override
        @Nonnull
        public String getMessage() {
            return Messages.SkippedStageReason_WHEN_Message(stageName);
        }

        @Override
        @Nonnull
        public String getStageStatus() {
            return StageStatus.getSkippedForConditional();
        }

        @Override
        @Nonnull
        public SkippedStageReason cloneWithNewStage(@Nonnull String newStage) {
            return new When(newStage);
        }

        private static final long serialVersionUID = 1L;
    }

    public static class Restart extends SkippedStageReason {
        private String restartedStage;

        public Restart(@Nonnull String stageName, @Nonnull String restartedStage) {
            super(stageName);
            this.restartedStage = restartedStage;
        }

        public String getRestartedStage() {
            return restartedStage;
        }

        @Override
        public boolean isNotExecutedNode() {
            return true;
        }

        @Override
        @Nonnull
        public String getMessage() {
            return Messages.SkippedStageReason_RESTART_Message(stageName, restartedStage);
        }

        @Override
        @Nonnull
        public String getStageStatus() {
            return StageStatus.getSkippedForRestart();
        }

        @Override
        @Nonnull
        public SkippedStageReason cloneWithNewStage(@Nonnull String newStage) {
            return new Restart(newStage, restartedStage);
        }

        private static final long serialVersionUID = 1L;
    }
}
