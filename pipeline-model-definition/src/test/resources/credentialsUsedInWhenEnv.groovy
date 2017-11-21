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
    environment {
        CRED1 = credentials("cred1")
        INBETWEEN = "Something ${CRED1} between"
    }

    agent any

    stages {
        stage("foo") {
            steps {
                echo "CRED1 is $CRED1"
                echo "INBETWEEN is $INBETWEEN"
                writeFile file: "cred1.txt", text: "${CRED1}"
                archive "**/*.txt"
            }
        }
        stage("bar") {
            when {
                allOf {
                    environment name: "CRED1", value: "Some secret text for 1"
                    environment name: "INBETWEEN", value: "Something ${CRED1} between"
                }
            }
            steps {
                echo "Got to stage 'bar'"
            }
        }
    }
}