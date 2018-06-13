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

package org.jenkinsci.plugins.pipeline.modeldefinition.model

import com.google.common.cache.LoadingCache
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import org.jenkinsci.plugins.pipeline.modeldefinition.Utils
import org.jenkinsci.plugins.pipeline.modeldefinition.options.DeclarativeOption
import org.jenkinsci.plugins.pipeline.modeldefinition.options.DeclarativeOptionDescriptor
import org.jenkinsci.plugins.scriptsecurity.sandbox.whitelists.Whitelisted

import javax.annotation.Nonnull


/**
 * Container for stage options.
 *
 * @author Andrew Bayer
 */
@ToString
@EqualsAndHashCode
@SuppressFBWarnings(value="SE_NO_SERIALVERSIONID")
class StageOptions implements Serializable {
    private Map<String, DeclarativeOption> options = [:]
    private Map<String, Object> wrappers = [:]

    @Whitelisted
    StageOptions(@Nonnull Map<String, DeclarativeOption> options, @Nonnull Map<String, Object> wrappers) {
        this.options.putAll(options)
        this.wrappers.putAll(wrappers)
    }

    Map<String, DeclarativeOption> getOptions() {
        return options
    }

    Map<String, Object> getWrappers() {
        return wrappers
    }

    /**
     * Get a map of allowed option type keys to their actual type ID. If a {@link org.jenkinsci.Symbol} is on the descriptor for a given
     * option, use that as the key. If the option type is a wrapper, use the step name as the key. Otherwise, use the class name.
     *
     * @return A map of valid option type keys to their actual type IDs.
     */
    static Map<String,String> getAllowedOptionTypes() {
        Map<String,String> c = [:]
        c.putAll(stageOptionTypeCache.get(STAGE_OPTION_CACHE_KEY))
        c.putAll(Options.getEligibleWrapperStepClasses())
        return c.sort()
    }

    /**
     * Given a option type key, get the actual type ID.
     *
     * @param key The key to look up.
     * @return The type ID for that key, if it's in the option types cache.
     */
    static String typeForKey(@Nonnull String key) {
        return getAllowedOptionTypes().get(key)
    }

    private static final Object STAGE_OPTION_CACHE_KEY = new Object()

    private static final LoadingCache<Object,Map<String,String>> stageOptionTypeCache =
        Utils.generateTypeCache(DeclarativeOptionDescriptor.class, false, [],
            { DeclarativeOptionDescriptor d ->
                return d.canUseInStage() || d.isStageOnly()
            })
}

