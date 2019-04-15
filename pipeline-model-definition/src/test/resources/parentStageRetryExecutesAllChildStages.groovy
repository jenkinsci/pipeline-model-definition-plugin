/*
 * The MIT License
 *
 * Copyright (c) 2019, CloudBees, Inc.
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

// Keeping the run count
int runCount = 0

// Main pipeline
pipeline {
    agent none

    stages {
        stage("Parent") {
            options {
                retry(3)
            }
            stages {
                stage('Init') {
                    steps {
                        echo "Init stage"

                        script {
                            // Increment the run count
                            runCount++
                        }

                        echo "runCount is ${runCount}"
                    }
                }

                stage("Foo") {

                    steps {

                        echo "Stage Foo executing"

                        script {
                            if (runCount < 2) {
                                error "Failing - retry me!"
                            } else {
                                echo "Stage Foo will not fail..."
                            }
                        }
                    }
                }
                stage("Bar") {
                    steps {
                        echo "Actually executing stage Bar"
                    }
                }
            }
        }
        stage("Baz") {
            steps {
                echo "Actually executing stage Baz"
            }
        }
    }
}
