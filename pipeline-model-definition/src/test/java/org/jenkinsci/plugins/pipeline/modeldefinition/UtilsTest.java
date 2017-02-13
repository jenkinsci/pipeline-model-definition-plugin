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
 *
 */

package org.jenkinsci.plugins.pipeline.modeldefinition;

import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.pipeline.modeldefinition.environment.DeclarativeEnvironmentContributor;
import org.jenkinsci.plugins.pipeline.modeldefinition.environment.impl.Credentials;
import org.jenkinsci.plugins.structs.describable.UninstantiatedDescribable;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * Tests for {@link Utils}.
 */
public class UtilsTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    public void getDescribableSingleArgument() throws Exception {
        UninstantiatedDescribable d = Utils.getDescribable("credentials", DeclarativeEnvironmentContributor.class, new Object[] {Collections.singletonMap(UninstantiatedDescribable.ANONYMOUS_KEY, "credId")});
        assertNotNull(d);
        Credentials instance = (Credentials) d.instantiate();
        assertNotNull(instance);
        assertEquals("credId", instance.getCredentialsId());
    }

    @Test
    public void getDescribableTwoMustHaveArguments() throws Exception {
        Map<String,Object> p = new HashMap<>();
        p.put("one", "First");
        p.put("two", "Second");
        UninstantiatedDescribable d = Utils.getDescribable("twoParams", SomeTestDescribable.class, new Object[] {p});
        assertNotNull(d);
        TwoParameters instance = (TwoParameters) d.instantiate();
        assertNotNull(instance);
        assertEquals("First", instance.one);
        assertEquals("Second", instance.two);
    }

    @Test
    public void getDescribableOneMustHaveArguments() throws Exception {
        UninstantiatedDescribable d = Utils.getDescribable("oneButTwoParams", SomeTestDescribable.class, new Object[] {Collections.singletonMap(UninstantiatedDescribable.ANONYMOUS_KEY, "First")});
        assertNotNull(d);
        OneButTwoParameters instance = (OneButTwoParameters) d.instantiate();
        assertNotNull(instance);
        assertEquals("First", instance.one);
        assertNull(instance.two);
    }

    @Test
    public void getDescribableTwoArguments() throws Exception {
        Map<String,Object> p = new HashMap<>();
        p.put("one", "First");
        p.put("two", "Second");
        UninstantiatedDescribable d = Utils.getDescribable("oneButTwoParams", SomeTestDescribable.class, new Object[] {p});
        assertNotNull(d);
        OneButTwoParameters instance = (OneButTwoParameters) d.instantiate();
        assertNotNull(instance);
        assertEquals("First", instance.one);
        assertEquals("Second", instance.two);
    }


    public static abstract class SomeTestDescribable<T extends SomeTestDescribable<T>> extends AbstractDescribableImpl<T> {

    }

    public static abstract class SomeTestDescriptor<T extends SomeTestDescribable<T>> extends Descriptor<T> {

    }

    public static class TwoParameters extends SomeTestDescribable<TwoParameters> {
        private final String one;
        private final String two;

        @DataBoundConstructor
        public TwoParameters(String one, String two) {
            this.one = one;
            this.two = two;
        }

        @TestExtension @Symbol("twoParams")
        public static class DescriptorImpl extends SomeTestDescriptor<TwoParameters> {

        }
    }

    public static class OneButTwoParameters extends SomeTestDescribable<OneButTwoParameters> {
        private final String one;
        private String two;

        @DataBoundConstructor
        public OneButTwoParameters(String one) {
            this.one = one;
        }

        public String getTwo() {
            return two;
        }

        @DataBoundSetter
        public void setTwo(String two) {
            this.two = two;
        }

        @TestExtension @Symbol("oneButTwoParams")
        public static class DescriptorImpl extends SomeTestDescriptor<OneButTwoParameters> {

        }
    }

}