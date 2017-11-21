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

def prefixCnt = 0
def postfixCnt = 0
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
        // There's some weirdness around this kind of operation because there's no deterministic order of operations.
        PREFIX_EXP = "${++prefixCnt}"
        POSTFIX_EXP = "${postfixCnt++}"
        RANGE_EXP = "${(0..2)}"
        //UNARY_EXP = "${-(+(-(+1)))}"
    }

    agent {
        label "some-label"
    }

    stages {
        stage("foo") {
            steps {
                echo "FOO is $FOO"
                echo "LIST_EXP is $LIST_EXP"
                echo "MAP_EXP is $MAP_EXP"
                // echo "BITWISE_NEGATE is $BITWISE_NEGATE"
                echo "BOOL_EXP is $BOOL_EXP"
                echo "CTOR_EXP is $CTOR_EXP"
                echo "CAST_EXP is $CAST_EXP"
                echo "PTR_EXP is $PTR_EXP"
                echo "AS_EXP is $AS_EXP"
                echo "PREFIX_EXP is $PREFIX_EXP"
                echo "POSTFIX_EXP is $POSTFIX_EXP"
                echo "RANGE_EXP is $RANGE_EXP"
                //echo "UNARY_EXP is $UNARY_EXP"
            }
        }
    }
}


