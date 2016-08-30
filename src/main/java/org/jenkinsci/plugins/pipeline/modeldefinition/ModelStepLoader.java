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
package org.jenkinsci.plugins.pipeline.modeldefinition;

import hudson.Extension;
import org.jenkinsci.plugins.pipeline.modeldefinition.model.MethodMissingWrapperWhitelist;
import org.jenkinsci.plugins.scriptsecurity.sandbox.whitelists.ProxyWhitelist;
import org.jenkinsci.plugins.scriptsecurity.sandbox.whitelists.StaticWhitelist;
import org.jenkinsci.plugins.workflow.cps.CpsScript;
import org.jenkinsci.plugins.workflow.cps.GlobalVariable;

import javax.annotation.Nonnull;
import java.io.IOException;

/**
 * Loads the main "pipeline" step as well as the additional CPS-transformed code it depends on.
 *
 * @author Andrew Bayer
 */
@Extension
public class ModelStepLoader extends GlobalVariable {
    public static final String STEP_NAME = "pipeline";

    @Override
    @Nonnull
    public String getName() {
        return STEP_NAME;
    }

    @Override
    @Nonnull
    public Object getValue(@Nonnull CpsScript script) throws Exception {
        // Make sure we've already loaded ClosureModelTranslator or load it now.
        script.getClass().getClassLoader().loadClass("org.jenkinsci.plugins.pipeline.modeldefinition.ClosureModelTranslator");
        script.getClass().getClassLoader().loadClass("org.jenkinsci.plugins.pipeline.modeldefinition.PropertiesToMapTranslator");

        return script.getClass()
                .getClassLoader()
                .loadClass("org.jenkinsci.plugins.pipeline.modeldefinition.ModelInterpreter")
                .getConstructor(CpsScript.class)
                .newInstance(script);
    }

    // TODO: Remember to prune out debugging stuff from the whitelist in place of a better debugging setup.
    @Extension
    public static class ModelDefinitionWhitelist extends ProxyWhitelist {
        public ModelDefinitionWhitelist() throws IOException {
            super(new MethodMissingWrapperWhitelist(), new StaticWhitelist(
                    "method java.util.Map containsKey java.lang.Object",
                    "method java.util.Collection isEmpty",
                    "method java.util.Map putAll java.util.Map",
                    "method java.util.Collection addAll java.util.Collection",

                    // Used for debugging - can probably be removed eventually
                    "staticField java.lang.System err",
                    "method java.io.PrintStream println java.lang.String"
            ));
        }
    }
}