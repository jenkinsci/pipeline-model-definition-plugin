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
package org.jenkinsci.plugins.pipeline.modeldefinition.util;

import org.hamcrest.Description;
import org.hamcrest.Factory;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;
import net.sf.json.JSONObject;

import java.util.Iterator;

import static org.hamcrest.core.IsAnything.anything;
import static org.hamcrest.core.IsEqual.equalTo;

/**
 * {@link Matcher} for {@link JSONObject}s.
 *
 * @author Robert Sandell &lt;rsandell@cloudbees.com&gt;.
 */
public class IsJsonObjectContaining extends TypeSafeMatcher<JSONObject> {

    private final Matcher<String> keyMatcher;
    private final Matcher<?> valueMatcher;

    public IsJsonObjectContaining(Matcher<String> keyMatcher, Matcher<?> valueMatcher) {
        this.keyMatcher = keyMatcher;
        this.valueMatcher = valueMatcher;
    }

    @Override
    protected boolean matchesSafely(JSONObject item) {
        Iterator keys = item.keys();
        while (keys.hasNext()) {
            String key = (String)keys.next();
            if (keyMatcher.matches(key) && valueMatcher.matches(item.opt(key))) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void describeTo(Description description) {
        description.appendText("JSONObject containing [")
                .appendDescriptionOf(keyMatcher)
                .appendText("->")
                .appendDescriptionOf(valueMatcher)
                .appendText("]");
    }

    @Factory
    public static Matcher<JSONObject> hasEntry(Matcher<String> keyMatcher, Matcher<?> valueMatcher) {
        return new IsJsonObjectContaining(keyMatcher, valueMatcher);
    }

    @Factory
    public static Matcher<JSONObject> hasEntry(String key, Matcher<?> valueMatcher) {
        return new IsJsonObjectContaining(equalTo(key), valueMatcher);
    }

    @Factory
    public static Matcher<JSONObject> hasEntry(String key, Object value) {
        return new IsJsonObjectContaining(equalTo(key), equalTo(value));
    }

    @Factory
    public static Matcher<JSONObject> hasKey(Matcher<String> keyMatcher) {
        return new IsJsonObjectContaining(keyMatcher, anything());
    }

    @Factory
    public static Matcher<JSONObject> hasKey(String key) {
        return new IsJsonObjectContaining(equalTo(key), anything());
    }
}
