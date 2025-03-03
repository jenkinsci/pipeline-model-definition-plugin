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
import hudson.ExtensionList;
import hudson.util.VersionNumber;
import java.io.File;
import java.net.URL;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.XMLConstants;
import javax.xml.parsers.SAXParserFactory;
import org.jenkinsci.plugins.pipeline.modeldefinition.agent.DeclarativeAgentScript2;
import org.jenkinsci.plugins.pipeline.modeldefinition.parser.CompatibilityLoader;
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
            for (var loader : ExtensionList.lookup(CompatibilityLoader.class)) {
                var url = loader.loadGroovySource(filename);
                if (url != null) {
                    LOGGER.fine(() -> "for " + context + " loading " + filename + " via " + loader + " ⇒ " + url);
                    return url;
                }
            }
            var url = base.loadGroovySource(filename);
            if (url != null) {
                LOGGER.finer(() -> "for " + context + " loading " + filename + " ⇒ " + url);
            }
            return url;
        });
    }

    @Override public GroovyShellDecorator forTrusted() {
        return this;
    }

    @Extension public static final class Loader implements CompatibilityLoader {
        private static final Set<String> CLASSES = Set.of(
            "org.jenkinsci.plugins.pipeline.modeldefinition.ModelInterpreter",
            "org.jenkinsci.plugins.pipeline.modeldefinition.agent.CheckoutScript",
            "org.jenkinsci.plugins.pipeline.modeldefinition.agent.impl.AnyScript",
            "org.jenkinsci.plugins.pipeline.modeldefinition.agent.impl.LabelScript",
            "org.jenkinsci.plugins.pipeline.modeldefinition.agent.impl.NoneScript");
        @Override public URL loadGroovySource(String clazz) {
            if (CLASSES.contains(clazz)) {
                return Upgrade.class.getResource("compat/" + clazz.replaceFirst(".+[.]", "") + ".groovy");
            } else {
                return null;
            }
        }
    }

    private static boolean isOld(FlowExecutionOwner owner) throws Exception {
        var factory = SAXParserFactory.newDefaultInstance();
        // TODO XMLUtils does not support SAX parsing:
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        var parser = factory.newSAXParser();
        var old = new AtomicBoolean();
        var buildXml = new File(owner.getRootDir(), "build.xml");
        parser.parse(buildXml, new DefaultHandler() {
            @Override public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
                var plugin = attributes.getValue("plugin");
                if (plugin != null) {
                    int at = plugin.indexOf('@');
                    if (at != -1 && plugin.substring(0, at).equals("pipeline-model-definition")) {
                        var version = new VersionNumber(plugin.substring(at + 1));
                        LOGGER.fine(() -> "got " + version + " off " + qName + " in " + buildXml);
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
