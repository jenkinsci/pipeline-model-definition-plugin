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
            failFast true
            parallel {
                stage("first") {
                    steps {
                        // There's a race condition where if the first branch fails before the second branch even gets into
                        // its node, the second stage is marked as failed, not aborted. This isn't ideal, but frankly
                        // I don't feel like figuring it out and I think it's likely to be at a deeper level than just
                        // Declarative, so let's sleep here to be safe.
                        sleep 5
                        error "First branch"
                    }
                    post {
                        aborted {
                            echo "FIRST STAGE ABORTED"
                        }
                        failure {
                            echo "FIRST STAGE FAILURE"
                        }
                    }
                }
                stage("second") {
                    agent any
                    options {
                        skipDefaultCheckout()
                    }
                    steps {
                        sleep 10
                        echo "Second branch"
                    }
                    post {
                        aborted {
                            echo "SECOND STAGE ABORTED"
                        }
                        failure {
                            echo "SECOND STAGE FAILURE"
                        }
                    }
                }
            }
        }
    }
}
