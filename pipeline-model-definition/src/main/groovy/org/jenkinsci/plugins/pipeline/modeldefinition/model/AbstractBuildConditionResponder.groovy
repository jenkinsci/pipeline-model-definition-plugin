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

/**
 * Parent for {@link PostStage} and {@link PostBuild} - containers for condition name/step block pairs.
 *
 * @author Andrew Bayer
 */
@SuppressFBWarnings(value="SE_NO_SERIALVERSIONID")
abstract class AbstractBuildConditionResponder<T extends AbstractBuildConditionResponder<T>> implements Serializable {
    private Map<String,StepsBlock> conditions
    private Map<StageConditionals,StepsBlock> whenConditions

    AbstractBuildConditionResponder(Map<String,StepsBlock> conditions, Map<StageConditionals,StepsBlock> whenConditions) {
        this.conditions = conditions
        this.whenConditions = whenConditions
    }

    Closure closureForSatisfiedCondition(String conditionName, Object runWrapperObj) {
        if (conditions.containsKey(conditionName)) {
            BuildCondition condition = BuildCondition.getConditionMethods().get(conditionName)
            if (condition != null && condition.meetsCondition(runWrapperObj)) {
                return ((StepsBlock)conditions.get(conditionName)).getClosure()
            }
        }

        return null
    }

    boolean satisfiedConditions(Object runWrapperObj) {
        Map<String,BuildCondition> conditions = BuildCondition.getConditionMethods()

        return BuildCondition.orderedConditionNames.any { conditionName ->
            conditions.containsKey(conditionName) && conditions.get(conditionName).meetsCondition(runWrapperObj)
        }
    }

    Map<StageConditionals,StepsBlock> getWhenConditions() {
        return whenConditions
    }
}
