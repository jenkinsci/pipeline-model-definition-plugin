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
import hudson.model.JobProperty
import hudson.model.JobPropertyDescriptor
import org.jenkinsci.plugins.pipeline.modeldefinition.Utils
import org.jenkinsci.plugins.pipeline.modeldefinition.options.DeclarativeOption
import org.jenkinsci.plugins.pipeline.modeldefinition.options.DeclarativeOptionDescriptor
import org.jenkinsci.plugins.structs.describable.UninstantiatedDescribable

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
    transient List<JobProperty> properties = []
    transient Map<String, DeclarativeOption> options = [:]

    public Options(List<UninstantiatedDescribable> input) {
        input.each { i ->
            def o = i.instantiate()
            if (o instanceof JobProperty) {
                properties << o
            } else if (o instanceof DeclarativeOption) {
                options[o.descriptor.name] = o
            }
        }
    }

    private static final Object OPTION_CACHE_KEY = new Object()
    private static final Object CACHE_KEY = new Object()

    private static final LoadingCache<Object,Map<String,String>> propertyTypeCache =
        Utils.generateTypeCache(JobPropertyDescriptor.class, false, ["pipelineTriggers", "parameters"])

    private static final LoadingCache<Object,Map<String,String>> optionTypeCache =
        Utils.generateTypeCache(DeclarativeOptionDescriptor.class, false, [])


    protected Object readResolve() throws IOException {
        // Need to make sure options is initialized on deserialization, even if it's going to be empty.
        this.properties = []
        this.options = [:]
        return this;
    }

    /**
     * Get a map of allowed property type keys to their actual type ID. If a {@link org.jenkinsci.Symbol} is on the descriptor for a given
     * job property, use that as the key. Otherwise, use the class name.
     *
     * @return A map of valid property type keys to their actual type IDs.
     */
    public static Map<String,String> getAllowedOptionTypes() {
        Map<String,String> c = propertyTypeCache.get(CACHE_KEY)
        c.putAll(optionTypeCache.get(OPTION_CACHE_KEY))
        return c
    }

    /**
     * Given a property type key, get the actual type ID.
     *
     * @param key The key to look up.
     * @return The type ID for that key, if it's in the property types cache.
     */
    public static String typeForKey(@Nonnull String key) {
        return getAllowedOptionTypes().get(key)
    }
}
