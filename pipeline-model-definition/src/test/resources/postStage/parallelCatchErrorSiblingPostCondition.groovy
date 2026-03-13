/*
 * The MIT License
 *
 * Copyright (c) 2026, CloudBees, Inc.
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

// Regression test for parallel stage post-condition race condition.
// When Stage 2 fails (caught by catchError), Stage 1's post block
// should evaluate based on Stage 1's own result, not be affected
// by Stage 2's failure polluting the global build result.

pipeline {
    agent any

    stages {
        stage('Parallel Stages') {
            parallel {
                stage('Stage 1') {
                    steps {
                        echo "Stage 1 running"
                    }
                    post {
                        success {
                            echo 'Stage 1 post: SUCCESS'
                        }
                        failure {
                            echo 'Stage 1 post: FAILURE'
                        }
                        always {
                            echo 'Stage 1 post: ALWAYS'
                        }
                    }
                }

                stage('Stage 2') {
                    steps {
                        catchError(buildResult: 'SUCCESS', stageResult: 'FAILURE') {
                            error('Stage 2 intentional failure')
                        }
                    }
                    post {
                        success {
                            echo 'Stage 2 post: SUCCESS'
                        }
                        failure {
                            echo 'Stage 2 post: FAILURE'
                        }
                        always {
                            echo 'Stage 2 post: ALWAYS'
                        }
                    }
                }
            }
        }
    }
}
