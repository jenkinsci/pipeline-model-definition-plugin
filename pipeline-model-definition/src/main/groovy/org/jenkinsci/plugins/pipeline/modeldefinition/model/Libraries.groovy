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

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import org.codehaus.groovy.ast.ASTNode
import org.codehaus.groovy.ast.tools.GeneralUtils
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTLibraries
import org.jenkinsci.plugins.pipeline.modeldefinition.parser.ASTParserUtils
import org.jenkinsci.plugins.scriptsecurity.sandbox.whitelists.Whitelisted

import javax.annotation.CheckForNull


/**
 * A container for one or more library identifiers, within the build, in the order they're declared.
 *
 * @author Andrew Bayer
 */
@ToString
@EqualsAndHashCode
@SuppressFBWarnings(value="SE_NO_SERIALVERSIONID")
public class Libraries implements Serializable {
    List<String> libs = []

    @Whitelisted
    Libraries(List<String> s) {
        libs.addAll(s)
    }

    Libraries libs(List<String> s) {
        this.libs = s
        return this
    }

    List<String> getLibs() {
        return libs
    }

    static ASTNode transformToRuntimeAST(@CheckForNull ModelASTLibraries original) {
        if (original != null && !original.libs.isEmpty()) {
            return ASTParserUtils.buildAst {
                constructorCall(Libraries) {
                    argumentList {
                        list {
                            original.libs.each { l ->
                                if (l.sourceLocation instanceof ASTNode) {
                                    expression.addAll((ASTNode) l.sourceLocation)
                                }
                            }
                        }
                    }
                }
            }
        }

        return GeneralUtils.constX(null)
    }
}
