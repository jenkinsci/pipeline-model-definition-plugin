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
 *
 */

package org.jenkinsci.plugins.pipeline.modeldefinition.model

import com.google.common.cache.LoadingCache
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import org.jenkinsci.plugins.pipeline.modeldefinition.Utils
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTWhen
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTWhenCondition
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTWhenContent
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTWhenExpression
import org.jenkinsci.plugins.pipeline.modeldefinition.when.DeclarativeStageConditional
import org.jenkinsci.plugins.pipeline.modeldefinition.when.DeclarativeStageConditionalDescriptor

import javax.annotation.CheckForNull
import javax.annotation.Nonnull

import static org.jenkinsci.plugins.pipeline.modeldefinition.Utils.getDescribable

/**
 * The {@link Stage#when} block.
 */
@ToString
@EqualsAndHashCode
@SuppressFBWarnings(value="SE_NO_SERIALVERSIONID")
class StageConditionals implements Serializable {
    private static final Object NESTED_CACHE_KEY = new Object()
    private static final Object MULTIPLE_NESTED_CACHE_KEY = new Object()

    private static final LoadingCache<Object,Map<String,String>> nestedTypeCache =
        Utils.generateTypeCache(DeclarativeStageConditionalDescriptor.class, false, [],
            { DeclarativeStageConditionalDescriptor s ->
                return s.getAllowedChildrenCount() != 0
            }
        )

    private static final LoadingCache<Object,Map<String,String>> multipleNestedTypeCache =
        Utils.generateTypeCache(DeclarativeStageConditionalDescriptor.class, false, [],
            { DeclarativeStageConditionalDescriptor s ->
                return s.getAllowedChildrenCount() < 0
            }
        )

    public static Map<String,String> getNestedConditionals() {
        return nestedTypeCache.get(NESTED_CACHE_KEY)
    }

    public static Map<String,String> getMultipleNestedConditionals() {
        return multipleNestedTypeCache.get(MULTIPLE_NESTED_CACHE_KEY)
    }

    public List<DeclarativeStageConditional> conditions = []

    public StageConditionals(List<DeclarativeStageConditional<? extends DeclarativeStageConditional>> inList) {
        conditions.addAll(inList)
    }

    @CheckForNull
    public static StageConditionals fromAST(@CheckForNull ModelASTWhen ast) {
        if (ast != null) {
            List<DeclarativeStageConditional<? extends DeclarativeStageConditional>> conditionals = ast.conditions.collect { c ->
                stageConditionalFromAST(c)
            }

            return new StageConditionals(conditionals)
        } else {
            return null
        }
    }

    /**
     * Translates the {@link org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTWhenContent} into a {@link DeclarativeStageConditional}.
     *
     * @param w
     * @return A populated {@link DeclarativeStageConditional}
     */
    private static DeclarativeStageConditional stageConditionalFromAST(@Nonnull ModelASTWhenContent w) {
        DeclarativeStageConditional c = null
        DeclarativeStageConditionalDescriptor desc = DeclarativeStageConditionalDescriptor.byName(w.name)

        if (w instanceof ModelASTWhenCondition) {
            if (desc.allowedChildrenCount == 0) {
                Object[] arg = new Object[1]
                arg[0] = w.args?.argListToMap()
                c = (DeclarativeStageConditional)getDescribable(w.name, desc.clazz, arg).instantiate()
            } else if (desc.allowedChildrenCount == 1) {
                DeclarativeStageConditional single = stageConditionalFromAST(w.children.first())
                c = (DeclarativeStageConditional)getDescribable(w.name, desc.clazz, single).instantiate()
            } else {
                List<DeclarativeStageConditional> nested = w.children.collect { stageConditionalFromAST(it) }
                c = (DeclarativeStageConditional)getDescribable(w.name, desc.clazz, nested).instantiate()
            }
        } else if (w instanceof ModelASTWhenExpression) {
            ModelASTWhenExpression expr = (ModelASTWhenExpression)w

            c = (DeclarativeStageConditional)getDescribable(w.name, desc.clazz, expr.codeBlockAsString()).instantiate()
        }

        return c
    }

}
