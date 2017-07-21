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
package org.jenkinsci.plugins.pipeline.modeldefinition.model

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings
import org.codehaus.groovy.ast.ASTNode
import org.codehaus.groovy.ast.tools.GeneralUtils
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTBuildConditionsContainer

import javax.annotation.CheckForNull
import javax.annotation.Nonnull

import static org.jenkinsci.plugins.pipeline.modeldefinition.parser.ASTParserUtils.buildAst


/**
 * Parent for {@link PostStage} and {@link PostBuild} - containers for condition name/step block pairs.
 *
 * @author Andrew Bayer
 */
@SuppressFBWarnings(value="SE_NO_SERIALVERSIONID")
public abstract class AbstractBuildConditionResponder<T extends AbstractBuildConditionResponder<T>>
    extends MappedClosure<StepsBlock,T> {

    public AbstractBuildConditionResponder(Map<String,StepsBlock> m) {
        super(m)
    }

    @Override
    public void modelFromMap(Map<String,Object> inMap) {

        inMap.each { conditionName, conditionClosure ->
            if (conditionName in BuildCondition.getConditionMethods().keySet()) {

                if (StepsBlock.class.isInstance(conditionClosure)) {
                    put(conditionName, (StepsBlock)conditionClosure)
                }
            }
        }
    }

    public Closure closureForSatisfiedCondition(String conditionName, Object runWrapperObj) {
        if (getMap().containsKey(conditionName)) {
            BuildCondition condition = BuildCondition.getConditionMethods().get(conditionName)
            if (condition != null && condition.meetsCondition(runWrapperObj)) {
                return ((StepsBlock)getMap().get(conditionName)).getClosure()
            }
        }

        return null
    }

    public boolean satisfiedConditions(Object runWrapperObj) {
        Map<String,BuildCondition> conditions = BuildCondition.getConditionMethods()

        return BuildCondition.orderedConditionNames.any { conditionName ->
            getMap().containsKey(conditionName) && conditions.get(conditionName).meetsCondition(runWrapperObj)
        }
    }

    static ASTNode transformContainerToRuntimeAST(@CheckForNull ModelASTBuildConditionsContainer original,
                                                  @Nonnull Class container) {
        if (original != null) {
            return buildAst {
                constructorCall(container) {
                    argumentList {
                        map {
                            original.conditions.each { cond ->
                                ASTNode steps = StepsBlock.transformToRuntimeAST(cond)
                                if (steps != null) {
                                    mapEntry {
                                        constant cond.condition
                                        expression.add(steps)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return GeneralUtils.constX(null)
    }

}
