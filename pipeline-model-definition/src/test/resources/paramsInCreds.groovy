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

    parameters {
        string(defaultValue:'FOOcredentials', description: "A parameter", name: 'testCreds')
    }

    environment {
        FOO = credentials("$testCreds")
        CONTAINS_CREDS = "${testCreds}"
    }

    agent any

    stages {
        stage("foo") {
            steps {
                echo "FOO is $FOO"
                echo "FOO_USR is $FOO_USR"
                echo "FOO_PSW is $FOO_PSW"
                echo "CONTAINS_CREDS is $CONTAINS_CREDS"

                //Write to file
                dir("combined") {
                    writeFile file:"foo.txt", text: "${FOO}"
                }
                writeFile file:"foo_psw.txt", text: "${FOO_PSW}"
                writeFile file:"foo_usr.txt", text: "${FOO_USR}"
                archive "**/*.txt"
            }
        }
    }
}