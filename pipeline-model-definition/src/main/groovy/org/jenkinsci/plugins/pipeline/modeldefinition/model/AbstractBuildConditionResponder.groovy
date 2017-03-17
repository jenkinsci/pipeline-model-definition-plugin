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
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTBuildConditionsContainer

import javax.annotation.CheckForNull


/**
 * Parent for {@link PostStage} and {@link PostBuild} - containers for condition name/step block pairs.
 *
 * @author Andrew Bayer
 */
@SuppressFBWarnings(value="SE_NO_SERIALVERSIONID")
public abstract class AbstractBuildConditionResponder<T extends AbstractBuildConditionResponder<T>>
    implements Serializable {
    private Map<String,String> conditionMap = new TreeMap<>()

    public Map<String,String> getConditionMap() {
        return conditionMap
    }

    public void addCondition(String conditionName, String conditionString) {
        conditionMap.put(conditionName, conditionString)
    }

    public String stepsForSatisfiedCondition(String conditionName, Object runWrapperObj) {
        if (conditionMap.containsKey(conditionName)) {
            BuildCondition condition = BuildCondition.getConditionMethods().get(conditionName)
            if (condition != null && condition.meetsCondition(runWrapperObj)) {
                return conditionMap.get(conditionName)
            }
        }

        return null
    }

    public boolean satisfiedConditions(Object runWrapperObj) {
        Map<String,BuildCondition> conditions = BuildCondition.getConditionMethods()

        return BuildCondition.orderedConditionNames.any { conditionName ->
            conditionMap.containsKey(conditionName) && conditions.get(conditionName).meetsCondition(runWrapperObj)
        }
    }
}
