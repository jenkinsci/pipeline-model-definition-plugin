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


package org.jenkinsci.plugins.pipeline.modeldefinition.model

import com.google.common.cache.LoadingCache
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import hudson.model.ParameterDefinition
import org.jenkinsci.plugins.pipeline.modeldefinition.Utils
import org.jenkinsci.plugins.scriptsecurity.sandbox.whitelists.Whitelisted

import javax.annotation.Nonnull

/**
 * A container for lists of parameter definitions.
 *
 * @author Andrew Bayer
 */
@ToString
@EqualsAndHashCode
@SuppressFBWarnings(value="SE_NO_SERIALVERSIONID")
class StageInput implements Serializable {
    String message
    String id
    String ok
    String submitter
    String submitterParameter
    List<ParameterDefinition> parameters = []

    @Whitelisted
    StageInput(String message, String id, String ok, String submitter, String submitterParameter, List<ParameterDefinition> params) {
        this.message = message
        this.id = id
        this.ok = ok
        this.submitter = submitter
        this.submitterParameter = submitterParameter
        if (params != null) {
            this.parameters = params
        }
    }

    String getMessage() {
        return message
    }

    String getId() {
        return id
    }

    String getOk() {
        return ok
    }

    String getSubmitter() {
        return submitter
    }

    String getSubmitterParameter() {
        return submitterParameter
    }

    List<ParameterDefinition> getParameters() {
        return parameters
    }
}
