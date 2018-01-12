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


package org.jenkinsci.plugins.pipeline.modeldefinition.validator;

import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTAgent;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTBranch;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTBuildCondition;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTBuildConditionsContainer;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTBuildParameter;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTBuildParameters;
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
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTStageInput;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTStages;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTStep;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTTools;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTTrigger;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTTriggers;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTValue;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTWhen;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTWhenCondition;


public interface ModelValidator {
    boolean validateElement(ModelASTAgent agent);

    boolean validateElement(ModelASTBranch branch);

    boolean validateElement(ModelASTBuildConditionsContainer container);

    boolean validateElement(ModelASTPostBuild postBuild);

    boolean validateElement(ModelASTPostStage post);

    boolean validateElement(ModelASTBuildCondition buildCondition);

    boolean validateElement(ModelASTEnvironment environment);

    boolean validateElement(ModelASTTools tools);

    boolean validateElement(ModelASTStep step);

    boolean validateElement(ModelASTWhen when);

    boolean validateElement(ModelASTMethodCall methodCall);

    boolean validateElement(ModelASTOptions properties);

    boolean validateElement(ModelASTTriggers triggers);

    boolean validateElement(ModelASTBuildParameters buildParameters);

    boolean validateElement(ModelASTOption jobProperty);

    boolean validateElement(ModelASTTrigger trigger);

    boolean validateElement(ModelASTBuildParameter buildParameter);

    boolean validateElement(ModelASTPipelineDef pipelineDef);

    boolean validateElement(ModelASTStage stage, boolean isNested);

    boolean validateElement(ModelASTStages stages);

    boolean validateElement(ModelASTLibraries libraries);

    boolean validateElement(ModelASTWhenCondition condition);

    boolean validateElement(ModelASTInternalFunctionCall call);

    boolean validateElement(ModelASTStageInput input);

    boolean validateElement(ModelASTValue value);
}
