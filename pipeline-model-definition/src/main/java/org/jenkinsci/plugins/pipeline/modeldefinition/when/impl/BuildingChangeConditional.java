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
 *
 */

package org.jenkinsci.plugins.pipeline.modeldefinition.when.impl;

import hudson.EnvVars;
import hudson.Extension;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMHeadOrigin;
import jenkins.scm.api.metadata.ContributorMetadataAction;
import jenkins.scm.api.metadata.ObjectMetadataAction;
import jenkins.scm.api.mixin.ChangeRequestSCMHead;
import jenkins.scm.api.mixin.ChangeRequestSCMHead2;
import org.apache.commons.lang.StringUtils;
import org.apache.tools.ant.types.selectors.SelectorUtils;
import org.codehaus.groovy.ast.expr.Expression;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTWhenContent;
import org.jenkinsci.plugins.pipeline.modeldefinition.parser.ASTParserUtils;
import org.jenkinsci.plugins.pipeline.modeldefinition.when.DeclarativeStageConditional;
import org.jenkinsci.plugins.pipeline.modeldefinition.when.DeclarativeStageConditionalDescriptor;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import javax.annotation.CheckForNull;
import java.io.File;

import static org.apache.commons.lang.StringUtils.defaultIfBlank;
import static org.apache.commons.lang.StringUtils.isNotEmpty;
import static org.jenkinsci.plugins.pipeline.modeldefinition.when.impl.BuildingChangeConditional.Names.*;

public class BuildingChangeConditional extends DeclarativeStageConditional<BuildingChangeConditional> {

    private String id;
    private String target;
    private String branch;
    private String fork;
    private String urlX;
    private String title;
    private String titleX;
    private String author;
    private String authorDisplayName;
    private String authorX;
    private String authorDisplayNameX;
    private String authorEmail;
    private String authorEmailX;

    @DataBoundConstructor
    public BuildingChangeConditional() {
    }

    /**
     * CHANGE_ID ant style matching.
     * @return id
     * @see ChangeRequestSCMHead#getId()
     */
    public String getId() {
        return id;
    }

    @DataBoundSetter
    public void setId(String id) {
        this.id = id;
    }

    /**
     * CHANGE_TARGET ant style matching.
     * @return target
     * @see SCMHead#getName()
     */
    public String getTarget() {
        return target;
    }

    @DataBoundSetter
    public void setTarget(String target) {
        this.target = target;
    }

    /**
     * CHANGE_BRANCH ant style matching.
     * @return target
     * @see ChangeRequestSCMHead2#getOriginName()
     */
    public String getBranch() {
        return branch;
    }

    @DataBoundSetter
    public void setBranch(String branch) {
        this.branch = branch;
    }

    /**
     * CHANGE_FORK ant style matching.
     * @return fork
     * @see SCMHeadOrigin.Fork#getName()
     */
    public String getFork() {
        return fork;
    }

    @DataBoundSetter
    public void setFork(String fork) {
        this.fork = fork;
    }

    /**
     * CHANGE_URL regular expression matching.
     *
     * Ant style matching doesn't work on url strings due to it being meant for regular paths
     * and all those extra characters conflicts.
     *
     * @return urlX
     * @see ObjectMetadataAction#getObjectUrl()
     */
    public String getUrlX() {
        return urlX;
    }

    @DataBoundSetter
    public void setUrlX(String urlX) {
        this.urlX = urlX;
    }

    /**
     * CHANGE_TITLE ant style matching.
     * @return title
     * @see ObjectMetadataAction#getObjectDisplayName()
     */
    public String getTitle() {
        return title;
    }

    @DataBoundSetter
    public void setTitle(String title) {
        this.title = title;
    }

    /**
     * CHANGE_TITLE regular expression matching.
     * @return titleX
     * @see ObjectMetadataAction#getObjectDisplayName()
     */
    public String getTitleX() {
        return titleX;
    }

    @DataBoundSetter
    public void setTitleX(String titleX) {
        this.titleX = titleX;
    }

    /**
     * CHANGE_AUTHOR ant style matching.
     * @return authorDisplayName
     * @see ContributorMetadataAction#getContributor()
     */
    public String getAuthor() {
        return author;
    }

    @DataBoundSetter
    public void setAuthor(String author) {
        this.author = author;
    }

    /**
     * CHANGE_AUTHOR_DISPLAY_NAME ant style matching.
     * @return authorDisplayName
     * @see ContributorMetadataAction#getContributorDisplayName()
     */
    public String getAuthorDisplayName() {
        return authorDisplayName;
    }

    @DataBoundSetter
    public void setAuthorDisplayName(String authorDisplayName) {
        this.authorDisplayName = authorDisplayName;
    }

    /**
     * CHANGE_AUTHOR regular expression matching.
     * @return authorDisplayName
     * @see ContributorMetadataAction#getContributor()
     */
    public String getAuthorX() {
        return authorX;
    }

    @DataBoundSetter
    public void setAuthorX(String authorX) {
        this.authorX = authorX;
    }

    /**
     * CHANGE_AUTHOR_DISPLAY_NAME regular expression matching.
     * @return authorDisplayName
     * @see ContributorMetadataAction#getContributorDisplayName()
     */
    public String getAuthorDisplayNameX() {
        return authorDisplayNameX;
    }

    @DataBoundSetter
    public void setAuthorDisplayNameX(String authorDisplayNameX) {
        this.authorDisplayNameX = authorDisplayNameX;
    }

    /**
     * CHANGE_AUTHOR_EMAIL ant style matching.
     * @return authorEmail
     * @see ContributorMetadataAction#getContributorEmail()
     */
    public String getAuthorEmail() {
        return authorEmail;
    }

    @DataBoundSetter
    public void setAuthorEmail(String authorEmail) {
        this.authorEmail = authorEmail;
    }

    /**
     * CHANGE_AUTHOR_EMAIL regular expression matching.
     * @return authorEmailX
     * @see ContributorMetadataAction#getContributorEmail()
     */
    public String getAuthorEmailX() {
        return authorEmailX;
    }

    @DataBoundSetter
    public void setAuthorEmailX(String authorEmailX) {
        this.authorEmailX = authorEmailX;
    }

    public boolean matches(EnvVars vars) {
        if (!CHANGE_ID.exists(vars)) {
            return false; //Not a change
        }
        try {
            check(id, Comparator.ANT, CHANGE_ID, vars);
            check(target, Comparator.ANT, CHANGE_TARGET, vars);
            check(branch, Comparator.ANT, CHANGE_BRANCH, vars);
            check(fork, Comparator.ANT, CHANGE_FORK, vars);
            check(urlX, Comparator.REG, CHANGE_URL, vars);
            check(title, Comparator.ANT, CHANGE_TITLE, vars);
            check(titleX, Comparator.REG, CHANGE_TITLE, vars);
            check(author, Comparator.ANT, CHANGE_AUTHOR, vars);
            check(authorX, Comparator.REG, CHANGE_AUTHOR, vars);
            check(authorDisplayName, Comparator.ANT, CHANGE_AUTHOR_DISPLAY_NAME, vars);
            check(authorDisplayNameX, Comparator.REG, CHANGE_AUTHOR_DISPLAY_NAME, vars);
            check(authorEmail, Comparator.ANT, CHANGE_AUTHOR_EMAIL, vars);
            check(authorEmailX, Comparator.REG, CHANGE_AUTHOR_EMAIL, vars);
            return true;
        } catch (AssertionError e) {
            return false;
        }
    }

    private void check(String pattern, Comparator comparator, Names varName, EnvVars vars) {
        if (isNotEmpty(pattern)) {
            if (!comparator.compare(pattern, varName.get(vars))) {
                throw new AssertionError(varName.name() + "("+varName.get(vars)+")" + " does not match " + pattern);
            }
        }
    }

    @Extension
    @Symbol("buildingChange")
    public static class DescriptorImpl extends DeclarativeStageConditionalDescriptor<BuildingChangeConditional> {
        @Override
        public Expression transformToRuntimeAST(@CheckForNull ModelASTWhenContent original) {
            return ASTParserUtils.transformWhenContentToRuntimeAST(original);
        }
    }

    enum Comparator { //TODO this should be in a utility class for all when conditions to use
        ANT {
            @Override
            boolean compare(String pattern, String actual) {
                actual = defaultIfBlank(actual, "");
                // replace with the platform specific directory separator before
                // invoking Ant's platform specific path matching.
                String safeCompare = pattern.replace('/', File.separatorChar);
                String safeName = actual.replace('/', File.separatorChar);
                return SelectorUtils.matchPath(safeCompare, safeName, false);
            }
        },
        REG {
            @Override
            boolean compare(String pattern, String actual) {
                //TODO validation for pattern compile
                return actual.matches(pattern);
            }
        };

        abstract boolean compare(String pattern, String actual);
    }

    enum Names { //TODO this should be in a utility class for all when conditions to use
        CHANGE_ID,
        CHANGE_TARGET,
        CHANGE_BRANCH,
        CHANGE_FORK,
        CHANGE_URL,
        CHANGE_TITLE,
        CHANGE_AUTHOR,
        CHANGE_AUTHOR_DISPLAY_NAME,
        CHANGE_AUTHOR_EMAIL;

        boolean exists(EnvVars vars) {
            return get(vars) != null;
        }

        boolean isEmpty(EnvVars vars) {
            return StringUtils.isEmpty(get(vars));
        }

        String get(EnvVars vars) {
            return vars.get(this.name());
        }
    }
}
