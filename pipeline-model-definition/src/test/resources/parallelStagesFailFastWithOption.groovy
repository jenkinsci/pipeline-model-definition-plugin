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
    options {
        parallelsAlwaysFailFast()
    }
    stages {
        stage('Parallel Stage One') {
            // failFast true
            parallel {
                stage('Stage A') { steps { sleep 1; echo 'Stage A'} }
                stage('Stage B') { steps { sleep 1; echo 'Stage B'} }
                stage('Stage C') { steps { sleep 1; echo 'Stage C'} }
            }
        }
        stage('Parallel Stage Two') {
            // failFast true
            parallel {
                stage('Stage D') { steps { sleep 1; echo 'Stage D'} }
                stage('Stage E') { steps { sleep 2; echo 'Stage E'} }
                stage('Stage F') { steps { sleep 1; echo 'Stage F'} }
            }
        }
        stage('Parallel Stage Three') {
            // failFast true
            parallel {
                stage('Stage G') { steps { sleep 1; echo 'Stage G'} }
                stage('Stage H') { steps { sleep 2; echo 'Stage H'} }
                stage('Stage I') { steps { sleep 1; echo 'Stage I'} }
            }
        }
        stage('Failing stage'){
            parallel {
                stage('Stage J') {
                    steps {
                        error(" error simulation")
                    }
                }
            }
        }
    }
}




