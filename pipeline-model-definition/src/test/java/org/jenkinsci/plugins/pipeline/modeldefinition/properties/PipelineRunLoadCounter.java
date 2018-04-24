/*
 * The MIT License
 *
 * Copyright (c) 2018, CloudBees, Inc.
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

package org.jenkinsci.plugins.pipeline.modeldefinition.properties;

import hudson.Extension;
import hudson.model.Run;
import hudson.model.RunAction;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.jenkinsci.plugins.workflow.steps.SynchronousStepExecution;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

@SuppressWarnings({"unchecked", "rawtypes"}) // API design mistakes
public final class PipelineRunLoadCounter {

    private static final ThreadLocal<String> currProject = new ThreadLocal<String>();
    private static final ThreadLocal<AtomicInteger> currCount = new ThreadLocal<AtomicInteger>();

    /**
     * Prepares a new project to be measured.
     * Usually called before starting builds, but may also be called retroactively.
     * @param project a project of any kind
     * @throws IOException if preparations fail
     */
    public static void prepare(WorkflowJob project) throws IOException {
        for (WorkflowRun build : project.getLazyBuildMixIn()._getRuns()) {
            Marker.add(build);
            build.save();
        }
    }

    /**
     * Counts how many build records are loaded as a result of some task.
     * @param project a project on which {@link #prepare} was called prior to creating builds
     * @param thunk a task which is expected to load some build records
     * @return how many build records were actually {@linkplain Run#onLoad loaded} as a result
     */
    public static int countLoads(WorkflowJob project, Runnable thunk) {
        project.getLazyBuildMixIn()._getRuns().purgeCache();
        currProject.set(project.getFullName());
        currCount.set(new AtomicInteger());
        thunk.run();
        return currCount.get().get();
    }

    /**
     * Asserts that at most a certain number of build records are loaded as a result of some task.
     * @param project a project on which {@link #prepare} was called prior to creating builds
     * @param max the maximum number of build records we expect to load
     * @param thunk a task which is expected to load some build records
     * @return the result of the task, if any
     * @throws Exception if the task failed
     * @throws AssertionError if one more than max build record is loaded
     * @param <T> the return value type
     */
    public static <T> T assertMaxLoads(WorkflowJob project, int max, Callable<T> thunk) throws Exception {
        project.getLazyBuildMixIn()._getRuns().purgeCache();
        currProject.set(project.getFullName());
        currCount.set(new AtomicInteger(-(max + 1)));
        return thunk.call();
    }

    private PipelineRunLoadCounter() {}

    /**
     * Used internally.
     */
    @Restricted(NoExternalUse.class)
    public static final class Marker implements RunAction {

        static void add(Run build) {
            build.addAction(new Marker(build.getParent().getFullName(), build.getNumber()));
        }

        private final String project;
        private final int build;

        Marker(String project, int build) {
            this.project = project;
            this.build = build;
        }

        @Override public void onLoad() {
            if (project.equals(currProject.get())) {
                System.err.println("loaded " + project + " #" + build);
                assert currCount.get().incrementAndGet() != 0 : "too many build records loaded from " + project;
            }
        }

        @Override public void onAttached(Run r) {}

        @Override public void onBuildComplete() {}

        @Override public String getIconFileName() {
            return null;
        }

        @Override public String getDisplayName() {
            return null;
        }

        @Override public String getUrlName() {
            return null;
        }

    }

    @Restricted(NoExternalUse.class)
    public static final class MarkerAdderStep extends Step {
        @DataBoundConstructor
        public MarkerAdderStep() {
        }

        @Override
        public StepExecution start(StepContext context) throws Exception {
            return new MarkerAdderStepExecution(context);
        }


        @Extension
        public static final class MarkerAdderStepDescriptorImpl extends StepDescriptor {

            @Override
            public String getFunctionName() {
                return "markerAdder";
            }

            @Override
            public String getDisplayName() {
                return "Add marker for run load count";
            }

            @Override
            public Set<? extends Class<?>> getRequiredContext() {
                return Collections.singleton(Run.class);
            }
        }
    }

    public static final class MarkerAdderStepExecution extends SynchronousStepExecution<Void> {

        MarkerAdderStepExecution(StepContext context) {
            super(context);
        }

        @Override protected Void run() throws Exception {
            Run<?,?> r = getContext().get(Run.class);
            if (r != null) {
                Marker.add(r);
            }
            return null;
        }

        private static final long serialVersionUID = 1L;
    }
}