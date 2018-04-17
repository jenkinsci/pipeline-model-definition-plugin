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

package org.jenkinsci.plugins.pipeline.modeldefinition.model;

import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.Run;
import jenkins.model.Jenkins;
import org.codehaus.groovy.ast.expr.Expression;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTElement;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTPipelineDef;
import org.jenkinsci.plugins.pipeline.modeldefinition.validator.ErrorCollector;
import org.jenkinsci.plugins.pipeline.modeldefinition.validator.ModelValidator;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

public abstract class DeclarativeDirective<D extends DeclarativeDirective<D>> extends ModelASTElement implements Describable<D> {

    public DeclarativeDirective(Object sourceLocation) {
        super(sourceLocation);
    }

    public abstract Expression transformDirective();

    @Override
    public DeclarativeDirectiveDescriptor<D> getDescriptor() {
        Descriptor desc = Jenkins.getActiveInstance().getDescriptor(getClass());
        if (!(desc instanceof DeclarativeDirectiveDescriptor)) {
            throw new AssertionError(getClass()+" is missing its descriptor");
        } else {
            return (DeclarativeDirectiveDescriptor<D>)desc;
        }
    }

    @Override
    public void validate(@Nonnull ModelValidator validator) {
        validate(validator, false);
    }

    public void validate(@Nonnull ModelValidator validator, boolean inStage) {
        validator.validateElement(this, inStage);
    }

    // TODO: Some way of requiring that implementations actually extend Preprocessor or its eventual siblings?

    public static abstract class Preprocessor<D extends DeclarativeDirective<D>> extends DeclarativeDirective<D> {
        public Preprocessor(Object sourceLocation) {
            super(sourceLocation);
        }

        public abstract ModelASTPipelineDef process(@Nonnull ModelASTPipelineDef pipelineDef,
                                                    @Nonnull ErrorCollector errorCollector,
                                                    @CheckForNull Run<?,?> build);
    }

}
