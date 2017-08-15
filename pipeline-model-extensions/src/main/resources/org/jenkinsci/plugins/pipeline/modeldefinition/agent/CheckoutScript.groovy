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


package org.jenkinsci.plugins.pipeline.modeldefinition.agent

import org.jenkinsci.plugins.pipeline.modeldefinition.SyntheticStageNames
import org.jenkinsci.plugins.workflow.cps.CpsScript

public class CheckoutScript implements Serializable {
    
    public static Closure doCheckout(CpsScript script, DeclarativeAgent agent, String customWorkspace = null, Closure body) {
        return {
            if (customWorkspace) {
                script.ws(customWorkspace) {
                    checkoutAndRun(script, agent, body).call()
                }
            } else {
                checkoutAndRun(script, agent, body).call()
            }
        }
    }
    
    private static Closure checkoutAndRun(CpsScript script, DeclarativeAgent agent, Closure body) {
        return {
            def checkoutMap
            if (agent.isDoCheckout() && agent.hasScmContext(script)) {
                if (!agent.inStage) {
                    script.stage(SyntheticStageNames.checkout()) {
                        checkoutMap = script.checkout script.scm
                    }
                } else {
                    // No stage when we're in a nested stage already
                    checkoutMap = script.checkout script.scm
                }
            }
            if (checkoutMap) {
                script.withEnv(checkoutMap.collect { k, v -> "${k}=${v}" }) {
                    body.call()
                }
            } else {
                body.call()
            }
        }
    }
    
}
