// This Jenkinsfile's main purpose is to show a real-world-ish example
// of what Pipeline config syntax actually looks like. 
pipeline {
    // Make sure that the tools we need are installed and on the path.
    tools {
        maven "mvn"
        jdk "jdk8"
    }

    agent none

    // Set log rotation, timeout and timestamps in the console
    options {
        buildDiscarder(logRotator(numToKeepStr:'10'))
        timestamps()
        timeout(time: 120, unit: 'MINUTES')
    }

    // Make sure we have GIT_COMMITTER_NAME and GIT_COMMITTER_EMAIL set due to machine weirdness.
    environment {
        GIT_COMMITTER_NAME = "jenkins"
        GIT_COMMITTER_EMAIL = "jenkins@jenkins.io"
        NEWER_CORE_VERSION = "2.138.3"
        TEST_TIMEOUT = "600"
    }
    

    stages {
        // While there is only one stage here, you can specify as many stages as you like!
        stage("build") {
            parallel {
                stage("linux") {
                    agent {
                        label "highmem"
                    }
                    steps {
                        sh "mvn clean install -Dmaven.test.failure.ignore=true -Djenkins.test.timeout=${TEST_TIMEOUT}"
                    }
                    post {
                        // No matter what the build status is, run this step. There are other conditions
                        // available as well, such as "success", "failed", "unstable", and "changed".
                        always {
                            junit testResults: '*/target/surefire-reports/*.xml', keepLongStdio: true
                        }
                        success {
                            archive "**/target/*.hpi"
                            archive "**/target/site/jacoco/jacoco.xml"
                        }
                        unstable {
                            archive "**/target/*.hpi"
                            archive "**/target/site/jacoco/jacoco.xml"
                        }
                    }
                }
                stage("windows") {
                    agent {
                        label "windows"
                    }
                    steps {
                        bat "mvn clean install -Dconcurrency=1 -Dmaven.test.failure.ignore=true -Dcodenarc.skip=true -Djenkins.test.timeout=${TEST_TIMEOUT}"
                    }
                    post {
                        always {
                            junit testResults: '*/target/surefire-reports/*.xml', keepLongStdio: true
                        }
                    }
                }
                stage("linux-newer-core") {
                    agent {
                        label "highmem"
                    }
                    steps {
                        sh "mvn clean install -Dmaven.test.failure.ignore=true -Djava.level=8 -Djenkins.test.timeout=${TEST_TIMEOUT} -Djenkins.version=${NEWER_CORE_VERSION}"
                    }
                    post {
                        // No matter what the build status is, run this step. There are other conditions
                        // available as well, such as "success", "failed", "unstable", and "changed".
                        always {
                            junit testResults: '*/target/surefire-reports/*.xml', keepLongStdio: true
                        }
                        success {
                            archive "**/target/*.hpi"
                            archive "**/target/site/jacoco/jacoco.xml"
                        }
                        unstable {
                            archive "**/target/*.hpi"
                            archive "**/target/site/jacoco/jacoco.xml"
                        }
                    }
                }
                stage("windows-newer-core") {
                    agent {
                        label "windows"
                    }
                    steps {
                        bat "mvn clean install -Dconcurrency=1 -Dmaven.test.failure.ignore=true -Dcodenarc.skip=true -Djava.level=8 -Djenkins.test.timeout=${TEST_TIMEOUT} -Djenkins.version=${NEWER_CORE_VERSION}"
                    }
                    post {
                        always {
                            junit testResults: '*/target/surefire-reports/*.xml', keepLongStdio: true
                        }
                    }
                }
            }
        }
    }
}
