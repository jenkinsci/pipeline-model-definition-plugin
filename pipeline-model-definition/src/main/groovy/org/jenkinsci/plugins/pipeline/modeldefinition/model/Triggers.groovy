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
import hudson.triggers.Trigger
import hudson.triggers.TriggerDescriptor
import org.jenkinsci.plugins.pipeline.modeldefinition.Utils
import org.jenkinsci.plugins.scriptsecurity.sandbox.whitelists.Whitelisted

import javax.annotation.Nonnull

/**
 * A container for lists of triggers.
 *
 * @author Andrew Bayer
 */
@ToString
@EqualsAndHashCode
@SuppressFBWarnings(value="SE_NO_SERIALVERSIONID")
class Triggers implements Serializable {
    private static final Object CACHE_KEY = new Object()
    private static final LoadingCache<Object,Map<String,String>> triggerTypeCache =
        Utils.generateTypeCache(TriggerDescriptor.class)

    // Transient since Trigger isn't serializable. Doesn't really matter since we're in trouble if we get interrupted
    // anyway.
    transient List<Trigger> triggers = []

    @Whitelisted
    Triggers(List<Trigger> t) {
        this.triggers = t
    }

    List<Trigger> getTriggers() {
        return triggers
    }

    protected Object readResolve() throws IOException {
        // Need to make sure triggers is initialized on deserialization, even if it's going to be empty.
        this.triggers = []
        return this
    }

    /**
     * Get a map of allowed trigger type keys to their actual type ID. If a {@link org.jenkinsci.Symbol} is on the descriptor for a given
     * trigger, use that as the key. Otherwise, use the class name.
     *
     * @return A map of valid parameter type keys to their actual type IDs.
     */
    static Map<String,String> getAllowedTriggerTypes() {
        return triggerTypeCache.get(CACHE_KEY)
    }

    /**
     * Given a parameter type key, get the actual type ID.
     *
     * @param key The key to look up.
     * @return The type ID for that key, if it's in the parameter types cache.
     */
    static String typeForKey(@Nonnull String key) {
        return getAllowedTriggerTypes().get(key)
    }
}
