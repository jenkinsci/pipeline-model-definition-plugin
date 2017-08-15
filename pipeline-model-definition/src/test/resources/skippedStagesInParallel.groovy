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
    agent none
    stages {
        stage("foo") {
            steps {
                echo "hello"
            }
        }

        stage('first-parallel') {
            parallel {
                stage("bar") {
                    when {
                        expression {
                            return false
                        }
                    }
                    steps {
                        echo "I will be skipped"
                    }
                }

                stage("baz") {
                    steps {
                        error "I will not be skipped but I will fail"
                    }
                }
            }
        }

        stage('second-parallel') {
            parallel {
                stage("bar2") {
                    steps {
                        echo "bar2 skipped for earlier failure"
                    }
                }
                stage("baz2") {
                    steps {
                        echo "bar3 skipped for earlier failure"
                    }
                }
            }
        }
    }

    post {
        failure {
            echo "I have failed"
        }
        success {
            echo "I have succeeded"
        }
    }
}



