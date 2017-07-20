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

import com.google.common.cache.LoadingCache
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings
import hudson.tools.ToolDescriptor
import org.codehaus.groovy.ast.builder.AstBuilder
import org.codehaus.groovy.ast.expr.Expression
import org.codehaus.groovy.ast.expr.MethodCallExpression
import org.codehaus.groovy.ast.expr.TupleExpression
import org.codehaus.groovy.ast.stmt.Statement
import org.jenkinsci.Symbol
import org.jenkinsci.plugins.pipeline.modeldefinition.Utils
import org.jenkinsci.plugins.pipeline.modeldefinition.parser.BlockStatementMatch
import org.jenkinsci.plugins.pipeline.modeldefinition.parser.ParserUtils

import javax.annotation.CheckForNull
import javax.annotation.Nonnull

import static org.jenkinsci.plugins.pipeline.modeldefinition.parser.ParserUtils.*

/**
 * A map of tool types to tool name (i.e., specific installation's configured name) to install and add to the path and
 * environment for the build.
 *
 * @author Andrew Bayer
 */
@SuppressFBWarnings(value="SE_NO_SERIALVERSIONID")
public class Tools extends MappedClosure<String,Tools> implements Serializable {

    private static final Object CACHE_KEY = new Object()

    private static final LoadingCache<Object,Map<String,String>> toolTypeCache =
        Utils.generateTypeCache(ToolDescriptor.class, true)

    public Tools(Map<String,String> inMap) {
        resultMap.putAll(inMap)
    }

    /**
     * Workaround for iterating over a map in CPS code. Gets the tools as a list of type/name tuples.
     *
     * @return A list of type/name tuples
     */
    @Nonnull
    public List<List<Object>> getToolEntries() {
        return getMap().collect { k, v ->
            return [k, v]
        }
    }
    /*

    @CheckForNull
    public static Statement transform(@CheckForNull Statement original) {
        if (original == null) {
            return null
        } else {
            BlockStatementMatch m = matchBlockStatement(original)
            if (m == null) {
                return null
            } else {
                def astResult = new AstBuilder().buildFromSpec {
                    returnStatement {
                        constructorCall(Tools) {
                            argumentList {
                                map {
                                    eachStatement(m.body.code) { s ->
                                        MethodCallExpression mce = matchMethodCall(s)
                                        if (mce != null) {
                                            String key = parseStringLiteral(mce.method)
                                            List<Expression> args = ((TupleExpression) mce.arguments).expressions
                                            if (!args.isEmpty() && args.size() == 1) {

                                        }
                                    }

                                }
                            }
                        }
                    }
                }
            }
        }
    }

     */
    /**
     * Merges the tool entries from another instance into this one, defaulting to the current instance's values.
     *
     * @return A list of type/name tuples
     */
    @Nonnull
    public List<List<Object>> mergeToolEntries(@CheckForNull Tools other) {
        if (other == null) {
            return getToolEntries()
        } else {
            Map<String,Object> mergedMap = new TreeMap<>()
            mergedMap.putAll(other.getMap())
            mergedMap.putAll(getMap())

            return mergedMap.collect { k, v -> return [k, v] }
        }
    }

    /**
     * Get a map of allowed tool type keys to their actual type ID. If a {@link Symbol} is on the descriptor for a given
     * tool, use that as the key. Otherwise, use the class name.
     *
     * @return A map of valid tool type keys to their actual type IDs.
     */
    public static Map<String,String> getAllowedToolTypes() {
        return toolTypeCache.get(CACHE_KEY)
    }

    /**
     * Given a tool type key, get the actual type ID.
     *
     * @param key The key to look up.
     * @return The type ID for that key, if it's in the tool types cache.
     */
    public static String typeForKey(@Nonnull String key) {
        return getAllowedToolTypes().get(key)
    }
}
