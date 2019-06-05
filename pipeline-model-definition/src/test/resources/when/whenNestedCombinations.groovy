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
        BRANCH_NAME = "master"
    }
    stages {
        stage("One") {
            steps {
                echo "First stage has no condition"
            }
        }
        stage("Two") {
            when {
                allOf {
                    branch "master"
                }
            }
            steps {
                echo "Second stage meets condition"
            }
        }
        stage("Three") {
            when {
                allOf {
                    branch "master"
                    expression { "a" == "a" }
                    expression { false }
                }
            }
            steps {
                echo "Third stage meets condition"
            }
        }
        stage("Four") {
            when {
                anyOf {
                    allOf {
                        not {
                            expression { false }
                        }
                        expression { true }
                    }
                    expression { false }
                }
            }
            steps {
                echo "Fourth stage meets condition"
            }
        }
    }
}