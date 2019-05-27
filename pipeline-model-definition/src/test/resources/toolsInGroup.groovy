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
    agent {
        label "some-label"
    }
    tools {
        maven "apache-maven-3.0.1"
    }
    stages {
        stage("foo") {
            parallel {
                stage("solo") {
                    steps {
                        echo "Solo: ${getMavenVersion()}"
                    }
                }
                stage("group") {
                    tools {
                        maven "apache-maven-3.5.0"
                    }
                    stages {
                        stage("first-in-group") {
                            steps {
                                echo "First in group: ${getMavenVersion()}"
                            }
                        }
                        stage("second-in-group") {
                            tools {
                                maven "apache-maven-3.0.1"
                            }
                            steps {
                                echo "Second in group: ${getMavenVersion()}"
                            }
                        }
                    }
                }
            }
        }
    }
}

// This is split off into a separate method despite not being ideal Declarative usage because it saves us a lot of
// copy-pasting
def getMavenVersion() {
    String mvnOut = ""
    if (isUnix()) {
        mvnOut = sh(script:'mvn --version',returnStdout:true)
    } else {
        mvnOut = bat(script:'mvn --version',returnStdout:true)
    }

    def matcher = (mvnOut.split(/\n/).find { it.contains("Apache Maven") }?.trim() =~ /.*(Apache Maven .*?) \(/)

    return matcher[0].size() > 1 ? matcher[0][1] : null
}
