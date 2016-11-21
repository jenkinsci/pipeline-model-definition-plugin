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
import hudson.cli.CLICommand;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.apache.commons.io.IOUtils;
import org.codehaus.groovy.control.Janitor;
import org.codehaus.groovy.control.MultipleCompilationErrorsException;
import org.codehaus.groovy.control.messages.SyntaxErrorMessage;
import org.jenkinsci.plugins.pipeline.modeldefinition.endpoints.ModelConverterAction;
import org.jenkinsci.plugins.pipeline.modeldefinition.parser.Converter;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

@Extension
public class DeclarativeLinterCommand extends CLICommand {
    @Override
    public String getShortDescription() {
        return Messages.DeclarativeLinterCommand_ShortDescription();
    }

    protected int run() throws Exception {
        StringWriter w = new StringWriter();
        IOUtils.copy(stdin, w);

        int retVal = 0;
        List<String> output = new ArrayList<>();

        try {
            Converter.scriptToPipelineDef(w.toString());
            output.add("Jenkinsfile successfully validated.");
            retVal = 0;
        } catch (Exception e) {
            output.add("Errors encountered validating Jenkinsfile:");
            retVal = 1;
            if (e instanceof MultipleCompilationErrorsException) {
                MultipleCompilationErrorsException ce = (MultipleCompilationErrorsException)e;
                for (Object o : ce.getErrorCollector().getErrors()) {
                    if (o instanceof SyntaxErrorMessage) {
                        SyntaxErrorMessage s = (SyntaxErrorMessage)o;
                        StringWriter sw = new StringWriter();
                        PrintWriter pw = new PrintWriter(sw);
                        s.write(pw);
                        pw.close();
                        output.add(sw.toString());
                    }
                }
            } else {
                output.add(e.getMessage());
            }
        }

        IOUtils.writeLines(output, null, stdout);

        return retVal;
    }
}
