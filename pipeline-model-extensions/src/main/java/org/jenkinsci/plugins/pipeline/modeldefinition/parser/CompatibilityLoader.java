/*
 * The MIT License
 *
 * Copyright 2025 CloudBees, Inc.
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

package org.jenkinsci.plugins.pipeline.modeldefinition.parser;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import groovy.lang.GroovyResourceLoader;
import hudson.ExtensionPoint;
import java.net.URL;
import org.jenkinsci.plugins.pipeline.modeldefinition.agent.DeclarativeAgentScript;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.Beta;

/**
 * Allows plugins formerly defining {@link DeclarativeAgentScript} to tolerate old builds.
 */
@Restricted(Beta.class) // TODO deprecate for deletion cca. 2026-03 (a year after 2.2234.v4a_b_13b_8cd590)
public interface CompatibilityLoader extends ExtensionPoint {

    /**
     * @param clazz a “filename”, actually a Groovy class FQN
     * @return a URL to a {@code *.groovy} source file, or null to use default classpath
     * @see GroovyResourceLoader#loadGroovySource
     */
    @CheckForNull URL loadGroovySource(String clazz);

}
