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
 *
 */

package org.jenkinsci.plugins.pipeline.modeldefinition.when.impl;

import hudson.Extension;
import hudson.model.Result;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.pipeline.modeldefinition.Messages;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTArgumentList;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTNamedArgumentList;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTPositionalArgumentList;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTSingleArgument;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTValue;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTWhenCondition;
import org.jenkinsci.plugins.pipeline.modeldefinition.validator.DeclarativeValidatorContributor;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import static hudson.model.Result.ABORTED;
import static hudson.model.Result.FAILURE;
import static hudson.model.Result.NOT_BUILT;
import static hudson.model.Result.SUCCESS;
import static hudson.model.Result.UNSTABLE;

@Extension
public class WhenConditionalValidator extends DeclarativeValidatorContributor {
    // TODO: This exists in Result#getNames() but isn't public, which is annoying.
    private static final List<String> allResultNames = Arrays.asList(SUCCESS.toString(),
            UNSTABLE.toString(),
            FAILURE.toString(),
            NOT_BUILT.toString(),
            ABORTED.toString());

    @CheckForNull
    @Override
    public String validateElement(@Nonnull ModelASTWhenCondition condition, @CheckForNull FlowExecution execution) {
        if (condition.getName().equals("changelog")) {
            String pattern = getPatternArgument(condition.getArgs());
            if (pattern == null) {
                return Messages.WhenConditionalValidator_changelog_missingParameter();
            } else {
                try {
                    Pattern.compile(pattern);
                    Pattern.compile(ChangeLogConditional.expandForMultiLine(pattern), Pattern.MULTILINE | Pattern.DOTALL);
                } catch (PatternSyntaxException e) {
                    return Messages.WhenConditionalValidator_changelog_badPattern(pattern, e.getMessage());
                }
            }
        } else if (condition.getName().equals("status")) {
            if (condition.getArgs() instanceof ModelASTNamedArgumentList) {
                ModelASTNamedArgumentList args = (ModelASTNamedArgumentList) condition.getArgs();
                if (args.getArguments().size() > 1) {
                    return Messages.WhenConditionalValidator_StatusCondition_TooManyParameters();
                }

                ModelASTValue status = args.valueForName("status");
                if (status != null) {
                    if (!status.isLiteral() ||
                            !(status.getValue() instanceof String) ||
                            !(allResultNames.contains(status.getValue()))) {
                        return Messages.WhenConditionalValidator_StatusCondition_InvalidStatusValue(status.getValue(),
                                StringUtils.join(allResultNames, ", "));
                    }
                }
            } else {
                return Messages.WhenConditionalValidator_StatusCondition_TooFewParameters();
            }
        }

        return null;
    }

    private String getPatternArgument(ModelASTArgumentList args) {
        if (args instanceof ModelASTSingleArgument) {
            return (String) ((ModelASTSingleArgument) args).getValue().getValue();
        } else if (args instanceof ModelASTPositionalArgumentList) {
            final List<ModelASTValue> arguments = ((ModelASTPositionalArgumentList) args).getArguments();
            if (!arguments.isEmpty()) {
                return (String) arguments.get(0).getValue();
            }
        } else if (args instanceof ModelASTNamedArgumentList) {
            return (String) ((ModelASTNamedArgumentList) args).argListToMap().get("pattern");
        }

        return null;
    }
}
