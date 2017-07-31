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

def cnt = 0
pipeline {
    environment {
        FOO = "${ true ? 'BAR' : 'OOF' }"
        TRUE_STR = "true"
        LIST_EXP = "${['a', FOO, 'c']}"
        MAP_EXP = "${[a: 'z', b: FOO, c: 'x']}"
        // BITWISE_NEGATE = "${~42}"
        BOOL_EXP = "${true == FOO}"
        CTOR_EXP = "${new URL('http://' + FOO)}"
        CAST_EXP = "${(Collection)['a', FOO, 'c']}"
        PTR_EXP = "${(Boolean.&parseBoolean)(TRUE_STR)}"
        AS_EXP = "${(['a', FOO, 'c'] as Set).class}"
    }

    agent {
        label "some-label"
    }

    stages {
        stage("foo") {
            steps {
                sh 'echo "FOO is $FOO"'
                sh 'echo "LIST_EXP is $LIST_EXP"'
                sh 'echo "MAP_EXP is $MAP_EXP"'
                // sh 'echo "BITWISE_NEGATE is $BITWISE_NEGATE"'
                sh 'echo "BOOL_EXP is $BOOL_EXP"'
                sh 'echo "CTOR_EXP is $CTOR_EXP"'
                sh 'echo "CAST_EXP is $CAST_EXP"'
                sh 'echo "PTR_EXP is $PTR_EXP"'
                sh 'echo "AS_EXP is $AS_EXP"'
                sh 'echo "PREFIX_EXP is $PREFIX_EXP"'
                sh 'echo "POSTFIX_EXP is $POSTFIX_EXP"'
            }
        }
    }
}


