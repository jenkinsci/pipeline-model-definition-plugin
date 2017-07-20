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
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import hudson.ExtensionList
import hudson.FilePath
import hudson.Launcher
import hudson.model.JobProperty
import hudson.model.JobPropertyDescriptor
import org.codehaus.groovy.ast.ASTNode
import org.codehaus.groovy.ast.builder.AstBuilder
import org.codehaus.groovy.ast.expr.Expression
import org.codehaus.groovy.ast.expr.MapExpression
import org.codehaus.groovy.ast.expr.MethodCallExpression
import org.codehaus.groovy.ast.stmt.Statement
import org.jenkinsci.plugins.pipeline.modeldefinition.Utils
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTElement
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTMethodCall
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTOption
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTOptions
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTStep
import org.jenkinsci.plugins.pipeline.modeldefinition.options.DeclarativeOption
import org.jenkinsci.plugins.pipeline.modeldefinition.options.DeclarativeOptionDescriptor
import org.jenkinsci.plugins.pipeline.modeldefinition.parser.ASTParserUtils
import org.jenkinsci.plugins.structs.SymbolLookup
import org.jenkinsci.plugins.structs.describable.UninstantiatedDescribable
import org.jenkinsci.plugins.workflow.steps.StepDescriptor

import javax.annotation.CheckForNull
import javax.annotation.Nonnull

import static org.jenkinsci.plugins.pipeline.modeldefinition.parser.ASTParserUtils.getAst
import static org.jenkinsci.plugins.pipeline.modeldefinition.parser.ASTParserUtils.matchMethodCall
import static org.jenkinsci.plugins.pipeline.modeldefinition.parser.ASTParserUtils.methodCallArgs
import static org.jenkinsci.plugins.pipeline.modeldefinition.parser.ASTParserUtils.methodCallToDescribable
import static org.jenkinsci.plugins.pipeline.modeldefinition.parser.ASTParserUtils.transformListOfDescribables

/**
 * Container for job options.
 *
 * @author Andrew Bayer
 */
@ToString
@EqualsAndHashCode
@SuppressFBWarnings(value="SE_NO_SERIALVERSIONID")
public class Options implements Serializable {
    public final static List<String> BLOCKED_PROPERTIES = ["pipelineTriggers", "parameters"]

    // Transient since JobProperty isn't serializable. Doesn't really matter since we're in trouble if we get interrupted
    // anyway.
    transient List<JobProperty> properties = []
    transient Map<String, DeclarativeOption> options = [:]
    transient Map<String, Object> wrappers = [:]

    public Options(List<Object> input) {
        input.each { i ->
            if (i instanceof UninstantiatedDescribable) {
                def o = i.instantiate()
                if (o instanceof JobProperty) {
                    properties << o
                } else if (o instanceof DeclarativeOption) {
                    options[o.descriptor.name] = o
                }
            } else if (i instanceof List && i.size() == 2 && i[0] instanceof String) {
                wrappers[(String)i[0]] = i[1]
            }
        }
    }

    Options(@Nonnull List<JobProperty> properties, @Nonnull Map<String, DeclarativeOption> options,
            @Nonnull Map<String, Object> wrappers) {
        this.properties.addAll(properties)
        this.options.putAll(options)
        this.wrappers.putAll(wrappers)
    }

    public List<JobProperty> getProperties() {
        return properties
    }

    public Map<String, DeclarativeOption> getOptions() {
        return options
    }

    public Map<String, Object> getWrappers() {
        return wrappers
    }

    private static final Object OPTION_CACHE_KEY = new Object()
    private static final Object CACHE_KEY = new Object()
    private static final Object WRAPPER_STEPS_KEY = new Object()

    private static final LoadingCache<Object,Map<String,String>> propertyTypeCache =
        Utils.generateTypeCache(JobPropertyDescriptor.class, false, BLOCKED_PROPERTIES)

    private static final LoadingCache<Object,Map<String,String>> optionTypeCache =
        Utils.generateTypeCache(DeclarativeOptionDescriptor.class, false, [])

    private static final LoadingCache<Object,Map<String,String>> wrapperStepsTypeCache  =
        Utils.generateTypeCache(StepDescriptor.class, false, [],
            { StepDescriptor s ->
                return s.takesImplicitBlockArgument() &&
                    !(s.getFunctionName() in ModelASTMethodCall.blockedSteps.keySet()) &&
                    !(Launcher.class in s.getRequiredContext()) &&
                    !(FilePath.class in s.getRequiredContext())
            }
        )

    public static Map<String,String> getEligibleWrapperStepClasses() {
        return wrapperStepsTypeCache.get(WRAPPER_STEPS_KEY)
    }

    public static List<String> getEligibleWrapperSteps() {
        return getEligibleWrapperStepClasses().keySet().sort()
    }

    protected Object readResolve() throws IOException {
        // Need to make sure options is initialized on deserialization, even if it's going to be empty.
        this.properties = []
        this.options = [:]
        this.wrappers = [:]
        return this;
    }

    /**
     * Get a map of allowed option type keys to their actual type ID. If a {@link org.jenkinsci.Symbol} is on the descriptor for a given
     * option, use that as the key. If the option type is a wrapper, use the step name as the key. Otherwise, use the class name.
     *
     * @return A map of valid option type keys to their actual type IDs.
     */
    public static Map<String,String> getAllowedOptionTypes() {
        Map<String,String> c = propertyTypeCache.get(CACHE_KEY)
        c.putAll(optionTypeCache.get(OPTION_CACHE_KEY))
        c.putAll(getEligibleWrapperStepClasses())
        return c.sort()
    }

    /**
     * Given a option type key, get the actual type ID.
     *
     * @param key The key to look up.
     * @return The type ID for that key, if it's in the option types cache.
     */
    public static String typeForKey(@Nonnull String key) {
        return getAllowedOptionTypes().get(key)
    }

    @CheckForNull
    static ASTNode transformToRuntimeAST(@CheckForNull ModelASTOptions original) {
        if (original != null && !original.options.isEmpty()) {
            List<ModelASTOption> jobProps = new ArrayList<>()
            List<ModelASTOption> options = new ArrayList<>()
            List<ModelASTOption> wrappers = new ArrayList<>()

            SymbolLookup symbolLookup = SymbolLookup.get()

            original.options.each { o ->
                if (symbolLookup.findDescriptor(JobProperty.class, o.name) != null) {
                    jobProps.add(o)
                } else if (symbolLookup.findDescriptor(DeclarativeOption.class, o.name) != null) {
                    options.add(o)
                } else if (StepDescriptor.byFunctionName(o.name) != null) {
                    wrappers.add(o)
                }
            }
            return getAst(new AstBuilder().buildFromSpec {
                returnStatement {
                    constructorCall(Options) {
                        argumentList {
                            expression.add(transformListOfDescribables(jobProps))
                            map {
                                options.each { o ->
                                    if (o.getSourceLocation() instanceof Statement) {
                                        MethodCallExpression expr = matchMethodCall((Statement) o.getSourceLocation())
                                        if (expr != null) {
                                            mapEntry {
                                                constant o.name
                                                expression.add(methodCallToDescribable(expr))
                                            }
                                        }
                                    }
                                }
                            }
                            map {
                                wrappers.each { w ->
                                    if (w.getSourceLocation() instanceof Statement) {
                                        MethodCallExpression expr = matchMethodCall((Statement) w.getSourceLocation())
                                        if (expr != null) {
                                            List<Expression> args = methodCallArgs(expr)

                                            mapEntry {
                                                constant w.name
                                                if (args.size() == 1) {
                                                    expression.add(args.get(0))
                                                } else if (args.size() > 1) {
                                                    list {
                                                        args.each { a ->
                                                            expression.add(a)
                                                        }
                                                    }
                                                } else {
                                                    // TODO: This may be wrong...
                                                    empty()
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            })
        }

        return null
    }
}
