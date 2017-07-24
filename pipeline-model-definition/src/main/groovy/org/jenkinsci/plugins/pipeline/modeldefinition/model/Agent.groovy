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
import org.codehaus.groovy.ast.ASTNode
import org.codehaus.groovy.ast.stmt.Statement
import org.codehaus.groovy.ast.tools.GeneralUtils
import org.jenkinsci.plugins.pipeline.modeldefinition.agent.DeclarativeAgent
import org.jenkinsci.plugins.pipeline.modeldefinition.agent.DeclarativeAgentDescriptor
import org.jenkinsci.plugins.pipeline.modeldefinition.agent.impl.None
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTAgent
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTClosureMap
import org.jenkinsci.plugins.pipeline.modeldefinition.options.impl.SkipDefaultCheckout
import org.jenkinsci.plugins.pipeline.modeldefinition.parser.ASTParserUtils
import org.jenkinsci.plugins.pipeline.modeldefinition.parser.BlockStatementMatch
import org.jenkinsci.plugins.scriptsecurity.sandbox.whitelists.Whitelisted
import org.jenkinsci.plugins.structs.SymbolLookup
import org.jenkinsci.plugins.structs.describable.UninstantiatedDescribable

import javax.annotation.CheckForNull


/**
 * What context the build should run in - i.e., on a given label, within a container of a given Docker agent, or without
 * any automatic management of node/agent/etc.
 *
 * @author Andrew Bayer
 */
@ToString
@EqualsAndHashCode
@SuppressFBWarnings(value="SE_NO_SERIALVERSIONID")
public class Agent extends MappedClosure<Object,Agent> implements Serializable {

    @Whitelisted
    Agent(Map<String,Object> inMap) {
        resultMap = inMap
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
        String foundSymbol = findSymbol()
        if (foundSymbol != null) {
            DeclarativeAgentDescriptor foundDescriptor = DeclarativeAgentDescriptor.byName(foundSymbol)
            def val = getMap().get(foundSymbol)
            def argMap = [:]
            if (val instanceof Map) {
                argMap.putAll(val)
            } else {
                argMap.put(UninstantiatedDescribable.ANONYMOUS_KEY, val)
            }

            DeclarativeAgent a = DeclarativeAgentDescriptor.instanceForDescriptor(foundDescriptor, argMap)

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

    /**
     * Needed to handle the combination of describable ordinals *and* Descriptor lookup.
     * @return The first symbol (in descriptor-ordinal-order searching) found in the map.
     */
    private String findSymbol() {
        String sym = null
        DeclarativeAgentDescriptor.all().each { d ->
            SymbolLookup.getSymbolValue(d)?.each { s ->
                if (getMap().containsKey(s) && sym == null) {
                    sym = s
                }
            }
        }

        return sym
    }

    public boolean hasAgent() {
        DeclarativeAgent a = getDeclarativeAgent(null, null)
        return a != null && !None.class.isInstance(a)
    }

    public Agent convertZeroArgs() {
        Map<String,Object> inMap = getMap()
        DeclarativeAgentDescriptor.zeroArgModels().keySet().each { k ->
            if (inMap.keySet().contains("${k}Key".toString())) {
                inMap.put(k, inMap.remove("${k}Key".toString()))
            }
        }
        return new Agent(inMap)
    }

    static ASTNode transformToRuntimeAST(@CheckForNull ModelASTAgent original) {
        if (ASTParserUtils.isGroovyAST(original) && original.agentType != null) {
            return ASTParserUtils.buildAst {
                constructorCall(Agent) {
                    argumentList {
                        if (original.variables == null ||
                            (original.variables instanceof ModelASTClosureMap &&
                                ((ModelASTClosureMap) original.variables).variables.isEmpty())) {
                            map {
                                mapEntry {
                                    constant original.agentType.key
                                    constant true
                                }
                            }
                        } else {
                            BlockStatementMatch match =
                                ASTParserUtils.matchBlockStatement((Statement) original.sourceLocation)
                            if (match != null) {
                                expression.add(ASTParserUtils.recurseAndTransformMappedClosure(match.body))
                            }
                        }
                    }
                }
            }
        }

        return GeneralUtils.constX(null)
    }
}
