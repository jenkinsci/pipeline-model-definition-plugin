pipeline {
    agent none
    stages {
        stage('Build') {
            parallel {
                stage('A') {
                    when {
                        expression { false }
                    }
                    stages {
                        stage('Build') {
                            steps {
                                echo 'WE SHOULD NEVER GET HERE'
                            }
                        }
                    }
                }
                stage('B') {
                    stages {
                        stage('Build') {
                            steps {
                                echo 'Build B'
                            }
                        }
                    }
                }
            }
        }
    }
}
