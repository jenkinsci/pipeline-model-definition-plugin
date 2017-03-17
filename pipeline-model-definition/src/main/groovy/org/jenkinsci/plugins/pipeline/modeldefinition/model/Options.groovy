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
import org.jenkinsci.plugins.pipeline.modeldefinition.TypeLookupCache
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTOptions
import org.jenkinsci.plugins.pipeline.modeldefinition.options.DeclarativeOption
import org.jenkinsci.plugins.workflow.steps.Step

import javax.annotation.CheckForNull
import javax.annotation.Nonnull

/**
 * Container for job options.
 *
 * @author Andrew Bayer
 */
@ToString
@EqualsAndHashCode
@SuppressFBWarnings(value="SE_NO_SERIALVERSIONID")
public class Options implements Serializable {
    // Transient since JobProperty isn't serializable. Doesn't really matter since we're in trouble if we get interrupted
    // anyway.
    transient List<String> jobProperties = []
    transient Map<String, DeclarativeOption> options = new TreeMap<>()
    transient Map<String, String> optionsToEval = new TreeMap<>()
    transient Map<String, Object> wrappers = new TreeMap<>()

    public Options() {}

    public static Map<String,String> getEligibleWrapperStepClasses() {
        return TypeLookupCache.publicCache.getWrapperTypes()
    }

    public static List<String> getEligibleWrapperSteps() {
        return getEligibleWrapperStepClasses().keySet().sort()
    }

    protected Object readResolve() throws IOException {
        // Need to make sure options is initialized on deserialization, even if it's going to be empty.
        this.jobProperties = []
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
        TypeLookupCache lookup = TypeLookupCache.publicCache
        Map<String,String> c = new TreeMap<>()
        c.putAll(lookup.getPropertyTypes())
        c.putAll(lookup.getOptionTypes())
        c.putAll(lookup.getWrapperTypes())
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
    public static Options fromAST(@CheckForNull ModelASTOptions ast) {
        if (ast != null) {
            Options o = new Options()
            TypeLookupCache lookup = TypeLookupCache.publicCache
            Map<String,String> propCache = lookup.getPropertyTypes()
            Map<String,String> optsCache = lookup.getOptionTypes()
            ast.options.each { opt ->
                if (propCache.containsKey(opt.name)) {
                    o.jobProperties.add(opt.toGroovy())
                }
                // TODO: When we have a DeclarativeOption that actually can take a string, make sure we test it'll
                // be evaluated. No point while the only ones take booleans or nothing, though.
                else if (optsCache.containsKey(opt.name)) {
                    o.optionsToEval.put(opt.name, opt.toGroovy())
                } else {
                    o.wrappers.put(opt.name, opt.argsAsObject(Step.class, false))
                }
            }
            return o
        } else {
            return null
        }
    }
}
