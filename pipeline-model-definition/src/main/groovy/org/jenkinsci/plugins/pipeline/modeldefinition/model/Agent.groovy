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
import org.jenkinsci.plugins.scriptsecurity.sandbox.whitelists.Whitelisted


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
    @Whitelisted
    Map<String,String> arguments = [:]

    @Whitelisted
    public Agent(Map<String,String> args) {
        this.arguments.putAll(args)
    }

    /**
     * Special constructor for the no-additional-arguments agent types, i.e., none and any
     */
    @Whitelisted
    public Agent(String s) {
        this.arguments.put(s, "true")
    }

    /**
     * Get the appropriate instantiated {@link DeclarativeAgent} corresponding to our arguments.
     *
     * @return The instantiated declarative agent or null if not found.
     */
    @Whitelisted
    public DeclarativeAgent getDeclarativeAgent() {
        String foundType = DeclarativeAgentDescriptor.orderedNames.find { arguments.containsKey(it) }
        if (foundType != null) {
            return DeclarativeAgentDescriptor.instanceForName(foundType, arguments)
        } else {
            return null
        }
    }

    @Whitelisted
    public boolean hasAgent() {
        DeclarativeAgent a = getDeclarativeAgent()
        return a != null && !None.class.isInstance(a)
    }
}
