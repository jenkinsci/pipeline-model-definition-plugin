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
    environment {
        OVERRIDE_TWICE = "override twice"
        DO_NOT_OVERRIDE = "do not override"
        OVERRIDE_ONCE = "override once"
    }
    stages {
        stage("foo") {
            environment {
                OVERRIDE_TWICE = "overrode once, one to go"
                OVERRIDE_ONCE = "overrode once and done"
                OVERRIDE_PER_NESTED = "override in each branch"
            }
            parallel {
                stage("first") {
                    agent {
                        label "first-agent"
                    }
                    environment {
                        OVERRIDE_TWICE = "overrode twice, in first branch"
                        OVERRIDE_PER_NESTED = "overrode per nested, in first branch"
                        DECLARED_PER_NESTED = "declared per nested, in first branch"
                    }
                    steps {
                        echo "First stage, ${WHICH_AGENT}"
                        echo "First stage, ${DO_NOT_OVERRIDE}"
                        echo "First stage, ${OVERRIDE_ONCE}"
                        echo "First stage, ${OVERRIDE_TWICE}"
                        echo "First stage, ${OVERRIDE_PER_NESTED}"
                        echo "First stage, ${DECLARED_PER_NESTED}"
                    }
                }
                stage("second") {
                    agent {
                        label "second-agent"
                    }
                    tools {
                        maven "apache-maven-3.0.1"
                    }
                    environment {
                        OVERRIDE_TWICE = "overrode twice, in second branch"
                        OVERRIDE_PER_NESTED = "overrode per nested, in second branch"
                        DECLARED_PER_NESTED = "declared per nested, in second branch"
                    }
                    steps {
                        echo "Second stage, ${WHICH_AGENT}"
                        echo "Second stage, ${DO_NOT_OVERRIDE}"
                        echo "Second stage, ${OVERRIDE_ONCE}"
                        echo "Second stage, ${OVERRIDE_TWICE}"
                        echo "Second stage, ${OVERRIDE_PER_NESTED}"
                        echo "Second stage, ${DECLARED_PER_NESTED}"
                        script {
                            if (isUnix()) {
                                sh 'mvn --version'
                            } else {
                                bat 'mvn --version'
                            }
                        }
                    }
                }
                stage("third") {
                    when {
                        expression {
                            return false
                        }
                    }
                    steps {
                        echo "WE SHOULD NEVER GET HERE"
                    }
                }
            }
        }
    }
}




