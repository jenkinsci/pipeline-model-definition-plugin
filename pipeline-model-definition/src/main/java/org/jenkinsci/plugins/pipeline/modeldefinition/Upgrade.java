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

package org.jenkinsci.plugins.pipeline.modeldefinition;

import groovy.lang.GroovyShell;
import hudson.Extension;
import hudson.util.VersionNumber;
import java.io.File;
import java.net.URL;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.parsers.SAXParserFactory;
import org.jenkinsci.plugins.pipeline.modeldefinition.agent.DeclarativeAgentScript2;
import org.jenkinsci.plugins.workflow.cps.CpsFlowExecution;
import org.jenkinsci.plugins.workflow.cps.GroovyShellDecorator;
import org.jenkinsci.plugins.workflow.flow.FlowExecutionOwner;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * Detects old builds (predating for example {@link DeclarativeAgentScript2}) and loads old Groovy resources to match.
 */
@Extension public final class Upgrade extends GroovyShellDecorator {

    private static final Logger LOGGER = Logger.getLogger(Upgrade.class.getName());

    @Override public void configureShell(CpsFlowExecution context, GroovyShell shell) {
        if (context == null) {
            return;
        }
        var owner = context.getOwner();
        if (owner == null) {
            return;
        }
        try {
            if (!isOld(owner)) {
                LOGGER.fine(() -> context + " does not seem to be old");
                return;
            }
        } catch (Exception x) {
            LOGGER.log(Level.WARNING, "failed to check " + context, x);
            return;
        }
        var cl = shell.getClassLoader();
        var base = cl.getResourceLoader();
        cl.setResourceLoader(filename -> {
            URL url;
            // TODO convert into an extension point for benefit of other plugins extending DeclarativeAgentScript
            if (filename.equals("org.jenkinsci.plugins.pipeline.modeldefinition.agent.CheckoutScript")) {
                url = Upgrade.class.getResource("/compat/CheckoutScript.groovy");
            } else if (filename.equals("org.jenkinsci.plugins.pipeline.modeldefinition.agent.impl.LabelScript")) {
                url = Upgrade.class.getResource("/compat/LabelScript.groovy");
                // TODO also AnyScript, NoneScript
            } else if (filename.equals("org.jenkinsci.plugins.pipeline.modeldefinition.ModelInterpreter")) {
                url = Upgrade.class.getResource("/compat/ModelInterpreter.groovy");
            } else {
                url = base.loadGroovySource(filename);
            }
            if (url != null) {
                LOGGER.fine(() -> "for " + context + " loading " + filename + " ⇒ " + url);
            }
            return url;
        });
    }

    @Override public GroovyShellDecorator forTrusted() {
        return this;
    }

    private static boolean isOld(FlowExecutionOwner owner) throws Exception {
        var rootDir = owner.getRootDir();
        var parser = SAXParserFactory.newDefaultInstance().newSAXParser();
        // TODO disable entity includes etc. like in XMLUtils
        var old = new AtomicBoolean();
        parser.parse(new File(rootDir, "build.xml"), new DefaultHandler() {
            @Override public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
                var plugin = attributes.getValue("plugin");
                if (plugin != null) {
                    int at = plugin.indexOf('@');
                    if (at != -1 && plugin.substring(0, at).equals("pipeline-model-definition")) {
                        var version = new VersionNumber(plugin.substring(at + 1));
                        LOGGER.fine(() -> "got " + version + " off " + qName);
                        if (version.isOlderThan(new VersionNumber("2.2234"))) {
                            old.set(true);
                        }
                    }
                }
            }
        });
        return old.get();
    }

}
