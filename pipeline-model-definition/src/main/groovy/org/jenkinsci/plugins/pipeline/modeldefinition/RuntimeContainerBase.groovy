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

package org.jenkinsci.plugins.pipeline.modeldefinition

import com.google.common.base.Predicate
import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.common.cache.LoadingCache
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings
import hudson.BulkChange
import hudson.Extension
import hudson.ExtensionList
import hudson.model.*
import hudson.triggers.Trigger
import jenkins.model.Jenkins
import org.apache.commons.codec.digest.DigestUtils
import org.codehaus.groovy.ast.ASTNode
import org.codehaus.groovy.ast.stmt.BlockStatement
import org.codehaus.groovy.ast.stmt.Statement
import org.codehaus.groovy.control.SourceUnit
import org.codehaus.groovy.runtime.InvokerHelper
import org.jenkinsci.plugins.pipeline.StageStatus
import org.jenkinsci.plugins.pipeline.StageTagsMetadata
import org.jenkinsci.plugins.pipeline.SyntheticStage
import org.jenkinsci.plugins.pipeline.modeldefinition.actions.DeclarativeJobAction
import org.jenkinsci.plugins.pipeline.modeldefinition.actions.DeclarativeJobPropertyTrackerAction
import org.jenkinsci.plugins.pipeline.modeldefinition.actions.ExecutionModelAction
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTBuildParameter
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTMethodCall
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTOption
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTTrigger
import org.jenkinsci.plugins.pipeline.modeldefinition.causes.RestartDeclarativePipelineCause
import org.jenkinsci.plugins.pipeline.modeldefinition.model.Environment
import org.jenkinsci.plugins.pipeline.modeldefinition.model.StepsBlock
import org.jenkinsci.plugins.pipeline.modeldefinition.options.DeclarativeOption
import org.jenkinsci.plugins.pipeline.modeldefinition.options.impl.QuietPeriod
import org.jenkinsci.plugins.pipeline.modeldefinition.steps.CredentialWrapper
import org.jenkinsci.plugins.scriptsecurity.sandbox.whitelists.Whitelisted
import org.jenkinsci.plugins.structs.SymbolLookup
import org.jenkinsci.plugins.structs.describable.DescribableModel
import org.jenkinsci.plugins.structs.describable.UninstantiatedDescribable
import org.jenkinsci.plugins.workflow.actions.LabelAction
import org.jenkinsci.plugins.workflow.actions.NotExecutedNodeAction
import org.jenkinsci.plugins.workflow.actions.TagsAction
import org.jenkinsci.plugins.workflow.actions.ThreadNameAction
import org.jenkinsci.plugins.workflow.cps.CpsFlowExecution
import org.jenkinsci.plugins.workflow.cps.CpsScript
import org.jenkinsci.plugins.workflow.cps.CpsThread
import org.jenkinsci.plugins.workflow.flow.FlowExecution
import org.jenkinsci.plugins.workflow.graph.BlockEndNode
import org.jenkinsci.plugins.workflow.graph.BlockStartNode
import org.jenkinsci.plugins.workflow.graph.FlowNode
import org.jenkinsci.plugins.workflow.graphanalysis.*
import org.jenkinsci.plugins.workflow.job.WorkflowJob
import org.jenkinsci.plugins.workflow.job.WorkflowRun
import org.jenkinsci.plugins.workflow.job.properties.PipelineTriggersJobProperty
import org.jenkinsci.plugins.workflow.steps.FlowInterruptedException
import org.jenkinsci.plugins.workflow.steps.Step
import org.jenkinsci.plugins.workflow.steps.StepDescriptor
import org.kohsuke.accmod.Restricted
import org.kohsuke.accmod.restrictions.NoExternalUse

import javax.annotation.CheckForNull
import javax.annotation.Nonnull
import javax.annotation.Nullable
import javax.lang.model.SourceVersion
import java.util.concurrent.TimeUnit

/**
 *
 *
 * @author Liam Newman
 */
@SuppressFBWarnings(value="SE_NO_SERIALVERSIONID")
class RuntimeContainerBase {

    protected static CpsScript that
    private static Map<Class,RuntimeContainerBase> classMap = new HashMap<>()

    @Whitelisted
    RuntimeContainerBase() {
    }

    @Whitelisted
    static void initialize(CpsScript script) {
        that = script
    }

    @Whitelisted
    static Object getInstance(Class instanceClass) {
        if (!classMap.containsKey(instanceClass)) {
            classMap[instanceClass] = instanceClass.getConstructor().newInstance()
        }
        return classMap[instanceClass]
    }

    /**
     * @see CpsScript#sleep(long)
     */
    @Whitelisted
    Object sleep(long arg) {
        return InvokerHelper.invokeMethod(that, "sleep", arg)
    }


/* Overriding methods defined in DefaultGroovyMethods
        if we don't do this, definitions in DefaultGroovyMethods get called. One problem
        is that most of them are not whitelisted, and the other problem is that they don't
        always forward the call to the closure owner.

        In CpsScript we override these methods and redefine them as variants of the 'echo' step,
        so for this to work the same from closure body, we need to redefine them.
 */
    @Whitelisted
    void println(Object arg) {
        InvokerHelper.invokeMethod(that, "println", [arg])
    }

    @Whitelisted
    void println() {
        InvokerHelper.invokeMethod(that, "println",[])
    }

    @Whitelisted
    void print(Object arg) {
        InvokerHelper.invokeMethod(that, "print", [arg]);
    }

    @Whitelisted
    void printf(String format, Object value) {
        InvokerHelper.invokeMethod(that, "printf", [])
    }
}
