pipeline {
    options {
        parallelsAlwaysFailFast()
        skipDefaultCheckout()
    }
    agent none
    stages {
        stage('fails') {
            steps {
                error 'some problem'
            }
        }
        stage('parallels') {
            parallel {
                stage('branch-1') {
                    steps {
                        echo 'branch 1'
                    }
                }
                stage('branch-2') {
                    steps {
                        echo 'branch 2'
                    }
                }
            }
        }
    }
    post {
        failure {
            echo 'the final failure block'
        }
    }
}
