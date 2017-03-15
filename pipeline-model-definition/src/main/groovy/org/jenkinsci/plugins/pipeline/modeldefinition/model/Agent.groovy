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
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import org.jenkinsci.plugins.pipeline.modeldefinition.agent.DeclarativeAgent
import org.jenkinsci.plugins.pipeline.modeldefinition.agent.DeclarativeAgentDescriptor
import org.jenkinsci.plugins.pipeline.modeldefinition.agent.impl.None
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTAgent
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTClosureMap
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTValue
import org.jenkinsci.plugins.pipeline.modeldefinition.options.impl.SkipDefaultCheckout
import org.jenkinsci.plugins.structs.describable.UninstantiatedDescribable

import javax.annotation.CheckForNull
import javax.annotation.Nonnull


/**
 * What context the build should run in - i.e., on a given label, within a container of a given Docker agent, or without
 * any automatic management of node/agent/etc.
 *
 * @author Andrew Bayer
 */
@ToString
@EqualsAndHashCode
@SuppressFBWarnings(value="SE_NO_SERIALVERSIONID")
public class Agent implements Serializable {
    private String agentType
    private Map<String,Object> config = new TreeMap<>()

    public void setAgentType(@Nonnull String agentType) {
        this.agentType = agentType
    }

    @Nonnull
    public String getAgentType() {
        return agentType
    }

    @Nonnull
    public Map<String,Object> getConfig() {
        return config
    }

    @Deprecated
    public DeclarativeAgent getDeclarativeAgent(Object context) {
        return getDeclarativeAgent(null, context)
    }

    /**
     * Get the appropriate instantiated {@link DeclarativeAgent} corresponding to our arguments.
     *
     * @return The instantiated declarative agent or null if not found.
     */
    public DeclarativeAgent getDeclarativeAgent(@CheckForNull Root root, Object context) {
        DeclarativeAgentDescriptor foundDescriptor = DeclarativeAgentDescriptor.byName(getAgentType())
        if (foundDescriptor != null) {
            DeclarativeAgent a = DeclarativeAgentDescriptor.instanceForDescriptor(foundDescriptor, getConfig())

            boolean doCheckout = false
            if (context instanceof Root) {
                a.setInStage(false)
            } else {
                a.setInStage(true)
            }
            if (root != null) {
                SkipDefaultCheckout skip = (SkipDefaultCheckout) root?.options?.options?.get("skipDefaultCheckout")
                if (!skip?.isSkipDefaultCheckout()) {
                    doCheckout = true
                }
            }
            a.setDoCheckout(doCheckout)

            return a
        } else {
            return null
        }
    }

    public boolean hasAgent() {
        DeclarativeAgent a = getDeclarativeAgent(null, null)
        return a != null && !None.class.isInstance(a)
    }

    @CheckForNull
    public static Agent fromAST(@CheckForNull ModelASTAgent ast) {
        if (ast != null) {
            Agent agent = new Agent()
            agent.setAgentType(ast.agentType.key)

            if (!(agent.agentType in DeclarativeAgentDescriptor.zeroArgModels().keySet())) {
                if (ast.variables instanceof ModelASTClosureMap) {
                    agent.config.putAll(getNestedAgentMap((ModelASTClosureMap) ast.variables))
                } else if (ast.variables instanceof ModelASTValue) {
                    agent.config.put(UninstantiatedDescribable.ANONYMOUS_KEY,
                        ((ModelASTValue) ast.variables).getValue().toString())
                } else {
                    throw new IllegalArgumentException("Error configuring agent - " + ast.variables + " did not parse correctly?")
                }
            }
            return agent
        } else {
            return null
        }
    }

    @Nonnull
    private static Map<String,Object> getNestedAgentMap(@Nonnull ModelASTClosureMap ast) {
        Map<String, Object> inMap = new TreeMap<>()
        if (ast != null) {
            ast.variables.each { k, v ->
                def inVal = null
                if (v instanceof ModelASTClosureMap) {
                    inVal = getNestedAgentMap(v)
                } else if (v instanceof ModelASTValue) {
                    inVal = v.getValue()
                } else {
                    throw new IllegalArgumentException("Error configuring agent - " + v + " did not parse correctly?")
                }
                inMap.put(k.key, inVal)
            }
        }

        return inMap
    }
}
