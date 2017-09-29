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

pipeline {
    agent any

    environment {
        SIMPLE_BACKSLASH = 'C:\\hey'
        NESTED_BACKSLASH = "${SIMPLE_BACKSLASH}\\there"
        HAS_TAB = 'oh\they'
        NESTED_HAS_TAB = "${HAS_TAB}\tthere"
    }

    stages {
        stage("foo") {
            steps {
                echo "echo SIMPLE_BACKSLASH is ${SIMPLE_BACKSLASH}"
                echo "echo NESTED_BACKSLASH is ${NESTED_BACKSLASH}"
                echo "echo HAS_TAB is ${HAS_TAB}"
                echo "echo NESTED_HAS_TAB is ${NESTED_HAS_TAB}"
                script {
                    if (isUnix()) {
                        sh 'echo "shell SIMPLE_BACKSLASH is ${SIMPLE_BACKSLASH}"'
                        sh 'echo "shell NESTED_BACKSLASH is ${NESTED_BACKSLASH}"'
                        sh 'echo "shell HAS_TAB is ${HAS_TAB}"'
                        sh 'echo "shell NESTED_HAS_TAB is $NESTED_HAS_TAB"'
                    } else {
                        bat 'echo "shell SIMPLE_BACKSLASH is %SIMPLE_BACKSLASH%"'
                        bat 'echo "shell NESTED_BACKSLASH is %NESTED_BACKSLASH%"'
                        bat 'echo "shell HAS_TAB is %HAS_TAB%"'
                        bat 'echo "shell NESTED_HAS_TAB is %NESTED_HAS_TAB%"'
                    }
                }
            }
        }
    }
}



