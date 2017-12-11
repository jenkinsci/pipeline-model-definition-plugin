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

package org.jenkinsci.plugins.pipeline.modeldefinition.actions;

import hudson.Extension;
import hudson.Util;
import hudson.model.Action;
import hudson.model.Cause;
import hudson.model.CauseAction;
import hudson.model.Item;
import hudson.model.Queue;
import hudson.model.Run;
import hudson.util.FormValidation;
import hudson.util.HttpResponses;
import jenkins.model.Jenkins;
import jenkins.model.ParameterizedJobMixIn;
import jenkins.model.TransientActionFactory;
import net.sf.json.JSONObject;
import org.acegisecurity.AccessDeniedException;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTStage;
import org.jenkinsci.plugins.pipeline.modeldefinition.causes.RestartDeclarativePipelineCause;
import org.jenkinsci.plugins.workflow.cps.CpsFlowExecution;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.flow.FlowExecutionOwner;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.DoNotUse;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.interceptor.RequirePOST;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.servlet.ServletException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static javax.servlet.http.HttpServletResponse.SC_CONFLICT;

public class RestartDeclarativePipelineAction implements Action {

    private final Run run;

    private RestartDeclarativePipelineAction(Run run) {
        this.run = run;
    }

    @Override public String getDisplayName() {
        return "Restart from Stage";
    }

    @Override public String getIconFileName() {
        // TODO: Needs an icon.
        return null;
    }

    @Override public String getUrlName() {
        return isRestartEnabled() ? "restart" : null;
    }

    /* accessible to Jelly */ public Run getOwner() {
        return run;
    }

    @CheckForNull
    private CpsFlowExecution getExecution() {
        FlowExecutionOwner owner = ((FlowExecutionOwner.Executable) run).asFlowExecutionOwner();
        if (owner == null) {
            return null;
        }
        FlowExecution exec = owner.getOrNull();
        return exec instanceof CpsFlowExecution ? (CpsFlowExecution) exec : null;
    }

    public boolean isRestartEnabled() {
        ExecutionModelAction executionModelAction = run.getAction(ExecutionModelAction.class);

        return executionModelAction != null &&
                !run.isBuilding() &&
                run.hasPermission(Item.BUILD) &&
                run.getParent().isBuildable() &&
                getExecution() != null;
    }

    @Restricted(DoNotUse.class)
    @RequirePOST
    public void doRestart(StaplerRequest req, StaplerResponse rsp) throws ServletException, IOException {
        if (!isRestartEnabled()) {
            throw new AccessDeniedException("not allowed to restart"); // AccessDeniedException2 requires us to look up the specific Permission
        }
        JSONObject form = req.getSubmittedForm();
        String stageName = Util.fixEmpty(form.getString("stageName"));

        try {
            run(stageName);
        } catch (IllegalStateException ise) {
            throw HttpResponses.error(SC_CONFLICT, ise);
        }

        rsp.sendRedirect("../.."); // back to WorkflowJob; new build might not start instantly so cannot redirect to it
    }

    public List<String> getRebuildableStages() {
        List<String> stages = new ArrayList<>();
        ExecutionModelAction execAction = run.getAction(ExecutionModelAction.class);
        if (execAction != null) {
            if (execAction.getStages() != null) {
                for (ModelASTStage s : execAction.getStages().getStages()) {
                    stages.add(s.getName());
                }
            }
        }
        return stages;
    }

    public String getCheckUrl() {
        return Jenkins.getInstance().getRootUrl() + run.getUrl() + getUrlName() + "/" + "checkStageName";
    }

    public FormValidation doCheckStageName(@QueryParameter String value) {
        String s = Util.fixEmptyAndTrim(value);
        if (s == null || s.equals("")) {
            return FormValidation.error(Messages.RestartDeclarativePipelineAction_NullStageName());
        }
        if (!getRebuildableStages().contains(s)) {
            return FormValidation.error(Messages.RestartDeclarativePipelineAction_StageNameNotPresent(s, run.getFullDisplayName()));
        }
        return FormValidation.ok();
    }

    public Queue.Item run(String stageName) {
        if (stageName == null || stageName.equals("")) {
            throw new IllegalStateException(Messages.RestartDeclarativePipelineAction_NullStageName());
        }

        if (!run.hasPermission(Item.BUILD) || !run.getParent().isBuildable()) {
            throw new IllegalStateException(Messages.RestartDeclarativePipelineAction_ProjectNotBuildable(run.getParent().getFullName()));
        }

        if (run.isBuilding()) {
            throw new IllegalStateException(Messages.RestartDeclarativePipelineAction_OriginRunIncomplete(run.getFullDisplayName()));
        }

        ExecutionModelAction execAction = run.getAction(ExecutionModelAction.class);
        if (execAction == null) {
            throw new IllegalStateException(Messages.RestartDeclarativePipelineAction_OriginWasNotDeclarative(run.getFullDisplayName()));
        }

        if (!getRebuildableStages().contains(stageName)) {
            throw new IllegalStateException(Messages.RestartDeclarativePipelineAction_StageNameNotPresent(stageName, run.getFullDisplayName()));
        }
        List<Action> actions = new ArrayList<>();
        CpsFlowExecution execution = getExecution();
        if (execution == null) {
            throw new IllegalStateException(Messages.RestartDeclarativePipelineAction_OriginRunMissingExecution(run.getFullDisplayName()));
        }

        actions.add(new RestartFlowFactoryAction(run.getExternalizableId()));
        actions.add(new CauseAction(new Cause.UserIdCause(), new RestartDeclarativePipelineCause(run, stageName)));
        return ParameterizedJobMixIn.scheduleBuild2(run.getParent(), 0, actions.toArray(new Action[actions.size()]));
    }

    @Extension
    public static class Factory extends TransientActionFactory<WorkflowRun> {

        @Override
        public Class<WorkflowRun> type() {
            return WorkflowRun.class;
        }

        @Override
        @Nonnull
        public Collection<? extends Action> createFor(@Nonnull WorkflowRun run) {
            // TODO: Can't actually check if any other actions are present, on the run or on its parent, so I can't verify
            // this is a Declarative run here. If I try, I end up in an infinite loop. Need to think on this.
            return Collections.<Action>singleton(new RestartDeclarativePipelineAction(run));
        }
    }

}
