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


package org.jenkinsci.plugins.pipeline.modeldefinition.validator

import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTAgent
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTBranch
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTBuildCondition
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTBuildParameter
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTBuildParameters
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTEnvironment
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTJobProperties
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTJobProperty
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTMethodCall
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTNotifications
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTPipelineDef
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTPostBuild
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTStage
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTStages
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTStep
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTTools
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTTrigger
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTTriggers


public interface ModelValidator {
    public boolean validateElement(ModelASTAgent agent)

    public boolean validateElement(ModelASTBranch branch)

    public boolean validateElement(ModelASTPostBuild postBuild)

    public boolean validateElement(ModelASTNotifications notifications)

    public boolean validateElement(ModelASTBuildCondition buildCondition)

    public boolean validateElement(ModelASTEnvironment environment)

    public boolean validateElement(ModelASTTools tools)

    public boolean validateElement(ModelASTStep step)

    public boolean validateElement(ModelASTMethodCall methodCall)

    public boolean validateElement(ModelASTJobProperties jobProperties)

    public boolean validateElement(ModelASTTriggers triggers)

    public boolean validateElement(ModelASTBuildParameters buildParameters)

    public boolean validateElement(ModelASTJobProperty jobProperty)

    public boolean validateElement(ModelASTTrigger trigger)

    public boolean validateElement(ModelASTBuildParameter buildParameter)

    public boolean validateElement(ModelASTPipelineDef pipelineDef)

    public boolean validateElement(ModelASTStage stage)

    public boolean validateElement(ModelASTStages stages)
}
