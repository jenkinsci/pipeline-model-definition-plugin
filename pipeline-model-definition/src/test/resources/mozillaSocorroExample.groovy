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

// This specifically covers library use, passing an externally defined variable to a library step,
// and ansiColor plugin usage.
@Library('echo-utils@master') _

/** Desired capabilities */
def capabilities = [
    browserName: 'Firefox',
    version: '47.0',
    platform: 'Windows 10'
]

pipeline {
    agent any
    options {
        ansiColor('xterm')
        timeout(time: 1, unit: 'HOURS')
    }
    environment {
        // TODO: Update this to be multiline with + when JENKINS-42771 lands
        PYTEST_ADDOPTS = "-n=10 --color=yes --tb=short --driver=SauceLabs --variables=capabilities.json"
    }
    stages {
        stage('Test') {
            steps {
                echo 'The following word is supposed to be \\u001B[31mred\\u001B[0m'
                myecho(capabilities)
            }
        }
    }
}



