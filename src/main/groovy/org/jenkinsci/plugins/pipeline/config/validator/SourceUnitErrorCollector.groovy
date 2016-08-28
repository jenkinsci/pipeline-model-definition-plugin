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
package org.jenkinsci.plugins.pipeline.config.validator

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings
import org.codehaus.groovy.ast.ASTNode
import org.codehaus.groovy.control.SourceUnit
import org.codehaus.groovy.control.messages.Message
import org.codehaus.groovy.control.messages.SyntaxErrorMessage
import org.codehaus.groovy.syntax.SyntaxException
import org.jenkinsci.plugins.pipeline.config.ast.ConfigASTElement

/**
 * Error collector for parsing from Groovy.
 *
 * @author Andrew Bayer
 */
@SuppressFBWarnings(value="SE_NO_SERIALVERSIONID")
public class SourceUnitErrorCollector extends ErrorCollector {
    final SourceUnit sourceUnit

    public SourceUnitErrorCollector(SourceUnit u) {
        this.sourceUnit = u
    }

    @Override
    public void error(ConfigASTElement src, String message) {
        // TODO: Being defensive here - better ideas?
        ASTNode loc = new ASTNode()

        if (src.sourceLocation instanceof ASTNode) {
            loc = (ASTNode) src.sourceLocation
        }

        def e = new SyntaxException(message, loc.lineNumber, loc.columnNumber, loc.lastLineNumber, loc.lastColumnNumber)

        sourceUnit.addError(e)
    }

    @Override
    public int getErrorCount() {
        return sourceUnit.errorCollector.errorCount
    }

    @Override
    public List<String> errorsAsStrings() {
        return sourceUnit.errorCollector.errors.findAll { Message m ->
            m instanceof SyntaxErrorMessage
        }.collect { SyntaxErrorMessage s ->
            return s.getCause().getMessage()
        }
    }
}
