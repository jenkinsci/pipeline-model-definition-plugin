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
package org.jenkinsci.plugins.pipeline.modeldefinition.model;

import hudson.Extension;
import org.jenkinsci.plugins.scriptsecurity.sandbox.Whitelist;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Special {@link Whitelist} to allow using {@code invokeMethod} and {@code setProperty} on our special classes.
 *
 * @author Andrew Bayer
 */
@Extension
public class MethodMissingWrapperWhitelist extends Whitelist {
    @Override
    public boolean permitsMethod(@Nonnull Method method, @Nonnull Object receiver, @Nonnull Object[] args) {
        return (method.getName().equals("invokeMethod") ||
                method.getName().equals("setProperty") ||
                method.getName().equals("getProperty"))
                && MethodMissingWrapper.class.isAssignableFrom(receiver.getClass());
    }

    @Override
    public boolean permitsConstructor(@Nonnull Constructor<?> constructor, @Nonnull Object[] args) {
        return false;
    }

    @Override
    public boolean permitsStaticMethod(@Nonnull Method method, @Nonnull Object[] args) {
        return false;
    }

    @Override
    public boolean permitsFieldGet(@Nonnull Field field, @Nonnull Object receiver) {
        return false;
    }

    @Override
    public boolean permitsFieldSet(@Nonnull Field field, @Nonnull Object receiver, @CheckForNull Object value) {
        return false;
    }

    @Override
    public boolean permitsStaticFieldGet(@Nonnull Field field) {
        return false;
    }

    @Override
    public boolean permitsStaticFieldSet(@Nonnull Field field, @CheckForNull Object value) {
        return false;
    }
}
