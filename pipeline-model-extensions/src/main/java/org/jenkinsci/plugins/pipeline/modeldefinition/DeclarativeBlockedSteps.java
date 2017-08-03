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

package org.jenkinsci.plugins.pipeline.modeldefinition;

import hudson.ExtensionList;
import hudson.ExtensionPoint;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Extension point for providing steps, and messages, which are blocked in Declarative.
 */
public abstract class DeclarativeBlockedSteps implements ExtensionPoint {
    /**
     * Steps which are blocked in "steps", "post" conditions, and the like.
     */
    public abstract Map<String,String> blockedInSteps();

    /**
     * Steps which are blocked in method calls like "options" entries.
     */
    public abstract Map<String,String> blockedInMethodCalls();

    /**
     * Get all blocked-in-steps steps across all extensions.
     */
    public static Map<String,String> allBlockedInSteps() {
        Map<String,String> map = new LinkedHashMap<>();

        for (DeclarativeBlockedSteps dbs : ExtensionList.lookup(DeclarativeBlockedSteps.class).reverseView()) {
            map.putAll(dbs.blockedInSteps());
        }

        return map;
    }

    /**
     * Get all blocked-in-method-calls steps across all extensions.
     */
    public static Map<String,String> allBlockedInMethodCalls() {
        Map<String,String> map = new LinkedHashMap<>();

        for (DeclarativeBlockedSteps dbs : ExtensionList.lookup(DeclarativeBlockedSteps.class).reverseView()) {
            map.putAll(dbs.blockedInMethodCalls());
        }

        return map;
    }
}
