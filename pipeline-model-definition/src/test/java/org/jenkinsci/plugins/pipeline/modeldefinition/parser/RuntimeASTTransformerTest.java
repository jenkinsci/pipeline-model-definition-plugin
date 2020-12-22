/*
 * The MIT License
 *
 * Copyright (c) 2021, CloudBees, Inc.
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

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.ExtensionList;
import org.codehaus.groovy.ast.ClassHelper;
import org.codehaus.groovy.ast.expr.Expression;
import org.codehaus.groovy.ast.expr.MapExpression;
import org.codehaus.groovy.ast.tools.GeneralUtils;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.pipeline.modeldefinition.AbstractModelDefTest;
import org.jenkinsci.plugins.pipeline.modeldefinition.Utils;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.InvisibleGlobalWhenCondition;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTWhenContent;
import org.jenkinsci.plugins.pipeline.modeldefinition.when.DeclarativeStageConditional;
import org.jenkinsci.plugins.pipeline.modeldefinition.when.DeclarativeStageConditionalDescriptor;
import org.jenkinsci.plugins.pipeline.modeldefinition.when.GlobalStageConditional;
import org.jenkinsci.plugins.pipeline.modeldefinition.when.GlobalStageConditionalDescriptor;
import org.jenkinsci.plugins.structs.SymbolLookup;
import org.junit.Test;
import org.jvnet.hudson.test.TestExtension;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

import static org.codehaus.groovy.ast.tools.GeneralUtils.args;
import static org.codehaus.groovy.ast.tools.GeneralUtils.callX;
import static org.codehaus.groovy.ast.tools.GeneralUtils.classX;
import static org.codehaus.groovy.ast.tools.GeneralUtils.constX;

public class RuntimeASTTransformerTest extends AbstractModelDefTest {

    @Test
    public void globalConditionalNoWhensMatching() throws Exception {
        GlobalTestConditional.GlobalTestConditionalDescriptor desc = ExtensionList.lookupSingleton(GlobalTestConditional.GlobalTestConditionalDescriptor.class);
        desc.skipStageName = "hello";

        expect("twoStages")
            .logNotContains("hello world")
            .logContains("goodbye world")
            .go();
    }

    @Test
    public void globalConditionalNoWhensNotMatching() throws Exception {
        GlobalTestConditional.GlobalTestConditionalDescriptor desc = ExtensionList.lookupSingleton(GlobalTestConditional.GlobalTestConditionalDescriptor.class);
        desc.skipStageName = "something else";

        expect("twoStages")
            .logContains("hello world", "goodbye world")
            .go();
    }

    @Test
    public void globalConditionalExistingWhensMatching() throws Exception {
        GlobalTestConditional.GlobalTestConditionalDescriptor desc = ExtensionList.lookupSingleton(GlobalTestConditional.GlobalTestConditionalDescriptor.class);
        desc.skipStageName = "Two";

        expect("when/whenEnv")
            .logNotContains("Heal it", "Should never be reached")
            .logContains("Ignore case worked")
            .go();
    }

    @Test
    public void globalConditionalExistingWhensNotMatching() throws Exception {
        GlobalTestConditional.GlobalTestConditionalDescriptor desc = ExtensionList.lookupSingleton(GlobalTestConditional.GlobalTestConditionalDescriptor.class);
        desc.skipStageName = "something else";

        expect("when/whenEnv")
            .logNotContains("Should never be reached")
            .logContains("Heal it", "Ignore case worked")
            .go();
    }

    public static class GlobalTestConditional extends GlobalStageConditional<GlobalTestConditional> {
        private String skipStageName;
        private String stageName;

        @DataBoundConstructor
        public GlobalTestConditional(String skipStageName) {
            this.skipStageName = skipStageName;
        }

        @DataBoundSetter
        public void setStageName(String stageName) {
            this.stageName = stageName;
        }

        public String getStageName() {
            return stageName;
        }

        public String getSkipStageName() {
            return skipStageName;
        }

        @TestExtension
        @Symbol("globalTest")
        public static class GlobalTestConditionalDescriptor extends GlobalStageConditionalDescriptor<GlobalTestConditional> {
            public String skipStageName;

            @Override
            public Map<String, Object> argMapForCondition(@NonNull InvisibleGlobalWhenCondition when) {
                Map<String,Object> argMap = new TreeMap<>();

                if (when.getName().equals("globalTest")) {
                    argMap.put("skipStageName", skipStageName);
                    argMap.put("stageName", when.getStageName());
                }

                return argMap;
            }

            @NonNull
            @Override
            public String getScriptClass() {
                return getClass().getPackage().getName() + ".GlobalTestConditionalScript";
            }
        }
    }
}
