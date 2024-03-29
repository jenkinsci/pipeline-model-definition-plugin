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
package org.jenkinsci.plugins.pipeline.modeldefinition.validator;

import org.jenkinsci.plugins.pipeline.modeldefinition.AbstractModelDefTest;
import org.jenkinsci.plugins.pipeline.modeldefinition.BaseParserLoaderTest;
import org.jenkinsci.plugins.pipeline.modeldefinition.Messages;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class ErrorsJSONParserTest extends BaseParserLoaderTest {
    private String configName;
    private String expectedError;

    public ErrorsJSONParserTest(String configName, String expectedError) {
        this.configName = configName;
        this.expectedError = expectedError;
    }

    @Parameterized.Parameters(name="Name: {0}")
    public static Iterable<Object[]> generateParameters() {
        return AbstractModelDefTest.configsWithErrors();
    }

    @Test
    public void parseAndValidateJSONWithError() throws Exception {
        /*
         * If the error message contains the list of legal agent types, ensure that agent types contributed by other
         * plugins (for example, the Kubernetes plugin in PCT context) are present. Note that we can't do this from
         * AbstractModelDefTest#configsWithErrors because determining this list requires Jenkins to be started, and
         * Jenkins has not yet been started when we are determining the parameters for the JUnit parameterized test.
         */
        if (expectedError.equals(
                Messages.ModelValidatorImpl_InvalidAgentType("foo", "[any, label, none, otherField]"))) {
            expectedError = Messages.ModelValidatorImpl_InvalidAgentType("foo", legalAgentTypes);
        }

        findErrorInJSON(expectedError, configName);
    }
}
