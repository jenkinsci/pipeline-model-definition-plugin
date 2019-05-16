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
    parameters {
        booleanParam(name: 'shouldRun', defaultValue: false, description: "a param")
    }
    stages {
        stage("bar") {
            steps {
                echo "We're ok"
                sleep 2
            }
        }
        stage("foo") {
            parallel {
                stage("first") {
                    agent any
                    when {
                        expression {
                            sleep 1
                            return params.shouldRun == true
                        }
                    }
                    steps {
                        echo "First branch"
                    }
                }
                stage("second") {
                    agent any
                    when {
                        expression {
                            return params.shouldRun == true
                        }
                    }
                    steps {
                        echo "Second branch"
                    }
                }
                stage("third") {
                    agent any
                    when {
                        expression {
                            return params.shouldRun == true
                        }
                    }
                    steps {
                        echo "third branch"
                    }
                }
                stage("fourth") {
                    agent any
                    when {
                        expression {
                            return params.shouldRun == true
                        }
                    }
                    steps {
                        echo "fourth branch"
                    }
                }
                stage("fifth") {
                    agent any
                    when {
                        expression {
                            return params.shouldRun == true
                        }
                    }
                    steps {
                        echo "Fifth branch"
                    }
                }
                stage("sixth") {
                    agent any
                    when {
                        expression {
                            return params.shouldRun == true
                        }
                    }
                    steps {
                        echo "sixth branch"
                    }
                }
                stage("seventh") {
                    agent any
                    when {
                        expression {
                            return params.shouldRun == true
                        }
                    }
                    steps {
                        echo "seventh branch"
                    }
                }
                stage("eighth") {
                    agent any
                    when {
                        expression {
                            return params.shouldRun == true
                        }
                    }
                    steps {
                        echo "eighth branch"
                    }
                }
                stage("ninth") {
                    agent any
                    when {
                        expression {
                            return params.shouldRun == false
                        }
                    }
                    steps {
                        echo "ninth branch"
                    }
                }
            }
            post {
                always {
                    sleep 2
                    echo "HUH"
                }
            }
        }
    }
    post {
        always {
            sleep 1
            echo "ok"
        }
    }
}




