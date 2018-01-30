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

package org.jenkinsci.plugins.pipeline.modeldefinition.validator;

import hudson.ExtensionList;
import hudson.ExtensionPoint;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTAgent;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTBranch;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTBuildCondition;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTBuildConditionsContainer;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTBuildParameter;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTBuildParameters;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTElement;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTEnvironment;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTInternalFunctionCall;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTLibraries;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTMethodCall;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTOption;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTOptions;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTPipelineDef;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTPostBuild;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTPostStage;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTStage;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTStages;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTStep;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTTools;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTTrigger;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTTriggers;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTValue;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTWhen;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTWhenCondition;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Extension point for contributing additional {@link ModelASTElement} validation checks to {@link ModelValidator} runs.
 *
 * Override a {@code validateElement} method in your extension to have that check run at the end of normal validation.
 * The new method will take both the relevant {@link ModelASTElement} and a possibly null {@link FlowExecution} for the
 * current run, if there is one. Since validation can be performed outside of the context of a run, such as from the CLI
 * or via the REST endpoints (as used by the editor, e.g.), you must handle a null execution cleanly.
 * 
 * Each method should return a string containing the error message if validation fails, and null otherwise.
 */
public abstract class DeclarativeValidatorContributor implements ExtensionPoint {

    /**
     * Implementations default to not optional. Can be overridden.
     */
    public boolean isOptional() {
        return false;
    }

    /**
     * Fallback for any unknown element type. Always returns true, cannot be overridden.
     */
    @CheckForNull
    public final String validateElement(@Nonnull ModelASTElement element, @CheckForNull FlowExecution execution) {
        return null;
    }
    
    @Nonnull
    public final List<String> validateElementAll(@Nonnull ModelASTElement element, @CheckForNull FlowExecution execution) {
        String r = validateElement(element, execution);
        List<String> result = new ArrayList<>();
        if (r != null) {
            result.add(r);
        }
        return result;
    }

    @CheckForNull
    public String validateElement(@Nonnull ModelASTAgent agent, @CheckForNull FlowExecution execution) {
        return null;
    }

    @Nonnull
    public List<String> validateElementAll(@Nonnull ModelASTAgent element, @CheckForNull FlowExecution execution) {
        String r = validateElement(element, execution);
        List<String> result = new ArrayList<>();
        if (r != null) {
            result.add(r);
        }
        return result;
    }

    @CheckForNull
    public String validateElement(@Nonnull ModelASTBranch branch, @CheckForNull FlowExecution execution) {
        return null;
    }

    @Nonnull
    public List<String> validateElementAll(@Nonnull ModelASTBranch element, @CheckForNull FlowExecution execution) {
        String r = validateElement(element, execution);
        List<String> result = new ArrayList<>();
        if (r != null) {
            result.add(r);
        }
        return result;
    }

    @CheckForNull
    public String validateElement(@Nonnull ModelASTBuildConditionsContainer container, @CheckForNull FlowExecution execution) {
        return null;
    }

    @Nonnull
    public List<String> validateElementAll(@Nonnull ModelASTBuildConditionsContainer element, @CheckForNull FlowExecution execution) {
        String r = validateElement(element, execution);
        List<String> result = new ArrayList<>();
        if (r != null) {
            result.add(r);
        }
        return result;
    }

    @CheckForNull
    public String validateElement(@Nonnull ModelASTPostBuild postBuild, @CheckForNull FlowExecution execution) {
        return validateElement((ModelASTBuildConditionsContainer) postBuild, execution);
    }

    @Nonnull
    public List<String> validateElementAll(@Nonnull ModelASTPostBuild element, @CheckForNull FlowExecution execution) {
        String r = validateElement(element, execution);
        List<String> result = new ArrayList<>();
        if (r != null) {
            result.add(r);
        }
        return result;
    }

    @CheckForNull
    public String validateElement(@Nonnull ModelASTPostStage post, @CheckForNull FlowExecution execution) {
        return validateElement((ModelASTBuildConditionsContainer) post, execution);
    }

    @Nonnull
    public List<String> validateElementAll(@Nonnull ModelASTPostStage element, @CheckForNull FlowExecution execution) {
        String r = validateElement(element, execution);
        List<String> result = new ArrayList<>();
        if (r != null) {
            result.add(r);
        }
        return result;
    }

    @CheckForNull
    public String validateElement(@Nonnull ModelASTBuildCondition buildCondition, @CheckForNull FlowExecution execution) {
        return null;
    }

    @Nonnull
    public List<String> validateElementAll(@Nonnull ModelASTBuildCondition element, @CheckForNull FlowExecution execution) {
        String r = validateElement(element, execution);
        List<String> result = new ArrayList<>();
        if (r != null) {
            result.add(r);
        }
        return result;
    }

    @CheckForNull
    public String validateElement(@Nonnull ModelASTEnvironment environment, @CheckForNull FlowExecution execution) {
        return null;
    }

    @Nonnull
    public List<String> validateElementAll(@Nonnull ModelASTEnvironment element, @CheckForNull FlowExecution execution) {
        String r = validateElement(element, execution);
        List<String> result = new ArrayList<>();
        if (r != null) {
            result.add(r);
        }
        return result;
    }

    @CheckForNull
    public String validateElement(@Nonnull ModelASTTools tools, @CheckForNull FlowExecution execution) {
        return null;
    }

    @Nonnull
    public List<String> validateElementAll(@Nonnull ModelASTTools element, @CheckForNull FlowExecution execution) {
        String r = validateElement(element, execution);
        List<String> result = new ArrayList<>();
        if (r != null) {
            result.add(r);
        }
        return result;
    }

    @CheckForNull
    public String validateElement(@Nonnull ModelASTStep step, @CheckForNull FlowExecution execution) {
        return null;
    }

    @Nonnull
    public List<String> validateElementAll(@Nonnull ModelASTStep element, @CheckForNull FlowExecution execution) {
        String r = validateElement(element, execution);
        List<String> result = new ArrayList<>();
        if (r != null) {
            result.add(r);
        }
        return result;
    }

    @CheckForNull
    public String validateElement(@Nonnull ModelASTWhen when, @CheckForNull FlowExecution execution) {
        return null;
    }

    @Nonnull
    public List<String> validateElementAll(@Nonnull ModelASTWhen element, @CheckForNull FlowExecution execution) {
        String r = validateElement(element, execution);
        List<String> result = new ArrayList<>();
        if (r != null) {
            result.add(r);
        }
        return result;
    }

    @CheckForNull
    public String validateElement(@Nonnull ModelASTMethodCall methodCall, @CheckForNull FlowExecution execution) {
        return null;
    }

    @Nonnull
    public List<String> validateElementAll(@Nonnull ModelASTMethodCall element, @CheckForNull FlowExecution execution) {
        String r = validateElement(element, execution);
        List<String> result = new ArrayList<>();
        if (r != null) {
            result.add(r);
        }
        return result;
    }

    @CheckForNull
    public String validateElement(@Nonnull ModelASTOptions properties, @CheckForNull FlowExecution execution) {
        return null;
    }

    @Nonnull
    public List<String> validateElementAll(@Nonnull ModelASTOptions element, @CheckForNull FlowExecution execution) {
        String r = validateElement(element, execution);
        List<String> result = new ArrayList<>();
        if (r != null) {
            result.add(r);
        }
        return result;
    }

    @CheckForNull
    public String validateElement(@Nonnull ModelASTTriggers triggers, @CheckForNull FlowExecution execution) {
        return null;
    }

    @Nonnull
    public List<String> validateElementAll(@Nonnull ModelASTTriggers element, @CheckForNull FlowExecution execution) {
        String r = validateElement(element, execution);
        List<String> result = new ArrayList<>();
        if (r != null) {
            result.add(r);
        }
        return result;
    }

    @CheckForNull
    public String validateElement(@Nonnull ModelASTBuildParameters buildParameters, @CheckForNull FlowExecution execution) {
        return null;
    }

    @Nonnull
    public List<String> validateElementAll(@Nonnull ModelASTBuildParameters element, @CheckForNull FlowExecution execution) {
        String r = validateElement(element, execution);
        List<String> result = new ArrayList<>();
        if (r != null) {
            result.add(r);
        }
        return result;
    }

    @CheckForNull
    public String validateElement(@Nonnull ModelASTOption jobProperty, @CheckForNull FlowExecution execution) {
        return validateElement((ModelASTMethodCall) jobProperty, execution);
    }

    @Nonnull
    public List<String> validateElementAll(@Nonnull ModelASTOption element, @CheckForNull FlowExecution execution) {
        String r = validateElement(element, execution);
        List<String> result = new ArrayList<>();
        if (r != null) {
            result.add(r);
        }
        return result;
    }

    @CheckForNull
    public String validateElement(@Nonnull ModelASTTrigger trigger, @CheckForNull FlowExecution execution) {
        return validateElement((ModelASTMethodCall) trigger, execution);
    }

    @Nonnull
    public List<String> validateElementAll(@Nonnull ModelASTTrigger element, @CheckForNull FlowExecution execution) {
        String r = validateElement(element, execution);
        List<String> result = new ArrayList<>();
        if (r != null) {
            result.add(r);
        }
        return result;
    }

    @CheckForNull
    public String validateElement(@Nonnull ModelASTBuildParameter buildParameter, @CheckForNull FlowExecution execution) {
        return validateElement((ModelASTMethodCall) buildParameter, execution);
    }

    @Nonnull
    public List<String> validateElementAll(@Nonnull ModelASTBuildParameter element, @CheckForNull FlowExecution execution) {
        String r = validateElement(element, execution);
        List<String> result = new ArrayList<>();
        if (r != null) {
            result.add(r);
        }
        return result;
    }
        
    @CheckForNull
    public String validateElement(@Nonnull ModelASTPipelineDef pipelineDef, @CheckForNull FlowExecution execution) {
        return null;
    }

    @Nonnull
    public List<String> validateElementAll(@Nonnull ModelASTPipelineDef element, @CheckForNull FlowExecution execution) {
        String r = validateElement(element, execution);
        List<String> result = new ArrayList<>();
        if (r != null) {
            result.add(r);
        }
        return result;
    }

    @CheckForNull
    public String validateElement(@Nonnull ModelASTStage stage, boolean isNested, @CheckForNull FlowExecution execution) {
        return null;
    }

    @Nonnull
    public List<String> validateElementAll(@Nonnull ModelASTStage element, boolean isNested,
                                                 @CheckForNull FlowExecution execution) {
        String r = validateElement(element, isNested, execution);
        List<String> result = new ArrayList<>();
        if (r != null) {
            result.add(r);
        }
        return result;
    }
    @CheckForNull
    public String validateElement(@Nonnull ModelASTStages stages, @CheckForNull FlowExecution execution) {
        return null;
    }

    @Nonnull
    public List<String> validateElementAll(@Nonnull ModelASTStages element, @CheckForNull FlowExecution execution) {
        String r = validateElement(element, execution);
        List<String> result = new ArrayList<>();
        if (r != null) {
            result.add(r);
        }
        return result;
    }

    @CheckForNull
    public String validateElement(@Nonnull ModelASTLibraries libraries, @CheckForNull FlowExecution execution) {
        return null;
    }

    @Nonnull
    public List<String> validateElementAll(@Nonnull ModelASTLibraries element, @CheckForNull FlowExecution execution) {
        String r = validateElement(element, execution);
        List<String> result = new ArrayList<>();
        if (r != null) {
            result.add(r);
        }
        return result;
    }

    @CheckForNull
    public String validateElement(@Nonnull ModelASTWhenCondition condition, @CheckForNull FlowExecution execution) {
        return null;
    }

    @Nonnull
    public List<String> validateElementAll(@Nonnull ModelASTWhenCondition element, @CheckForNull FlowExecution execution) {
        String r = validateElement(element, execution);
        List<String> result = new ArrayList<>();
        if (r != null) {
            result.add(r);
        }
        return result;
    }

    @CheckForNull
    public String validateElement(@Nonnull ModelASTInternalFunctionCall call, @CheckForNull FlowExecution execution) {
        return null;
    }

    @Nonnull
    public List<String> validateElementAll(@Nonnull ModelASTInternalFunctionCall element, @CheckForNull FlowExecution execution) {
        String r = validateElement(element, execution);
        List<String> result = new ArrayList<>();
        if (r != null) {
            result.add(r);
        }
        return result;
    }

    @CheckForNull
    public String validateElement(@Nonnull ModelASTValue value, @CheckForNull FlowExecution execution) {
        return null;
    }

    @Nonnull
    public List<String> validateElementAll(@Nonnull ModelASTValue element, @CheckForNull FlowExecution execution) {
        String r = validateElement(element, execution);
        List<String> result = new ArrayList<>();
        if (r != null) {
            result.add(r);
        }
        return result;
    }

    /**
     * Get all {@link DeclarativeValidatorContributor}s.
     *
     * @return a list of all {@link DeclarativeValidatorContributor}s registered.`
     */
    public static ExtensionList<DeclarativeValidatorContributor> all() {
        return ExtensionList.lookup(DeclarativeValidatorContributor.class);
    }

}
