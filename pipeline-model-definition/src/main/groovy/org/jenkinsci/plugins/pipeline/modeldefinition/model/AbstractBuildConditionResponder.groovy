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

import org.jenkinsci.plugins.workflow.job.WorkflowRun
import org.jenkinsci.plugins.workflow.support.steps.build.RunWrapper


/**
 * Parent for {@link Notifications} and {@link PostBuild} - containers for condition name/step block pairs.
 *
 * @author Andrew Bayer
 */
@SuppressFBWarnings(value="SE_NO_SERIALVERSIONID")
public abstract class AbstractBuildConditionResponder<T extends AbstractBuildConditionResponder<T>>
    extends MappedClosure<StepsBlock,T> {


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

    public List<Closure> satisfiedConditions(Object runWrapperObj) {
        RunWrapper runWrapper = (RunWrapper)runWrapperObj
        WorkflowRun run = (WorkflowRun)runWrapper.getRawBuild()

        List<Closure> closures = []

        Map<String,BuildCondition> conditions = BuildCondition.getConditionMethods()

        BuildCondition.orderedConditionNames.each { conditionName ->
            if (getMap().containsKey(conditionName) && conditions.get(conditionName).meetsCondition(run)) {
                closures.add(((StepsBlock)getMap().get(conditionName)).getClosure())
            }
        }

        return closures
    }
}
