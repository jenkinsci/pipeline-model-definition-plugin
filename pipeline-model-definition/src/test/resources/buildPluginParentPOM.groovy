pipeline {
    tools {
        maven "apache-maven-3.5.0"
        jdk "default"
    }

    agent {
        label "master"
    }

    stages {
        stage("build") {
            steps {
                dir("tmp") {
                    git changelog: false, poll: false, url: 'git://github.com/jenkinsci/plugin-pom.git', branch: 'master'
                    script {
                        if (isUnix()) {
                            sh 'echo "M2_HOME: ${M2_HOME}"'
                            sh 'echo "JAVA_HOME: ${JAVA_HOME}"'
                            sh 'mvn clean verify -Dmaven.test.failure.ignore=true'
                        } else {
                            bat 'echo "M2_HOME: %M2_HOME%"'
                            bat 'echo "JAVA_HOME: %JAVA_HOME%"'
                            bat 'mvn clean verify -Dmaven.test.failure.ignore=true'
                        }
                    }
                }
            }
        }
    }

    post {
        always {
            archive "target/**/*"
        }
    }
}
