pipeline {
    agent none
    stages {
        stage('Build') {
            stages {
                stage('Build') {
                    when {
                        expression { false }
                    }
                    steps {
                        echo 'WE SHOULD NEVER GET HERE'
                    }
                }
                stage('Test') {
                    steps {
                        echo 'Test ran'
                    }
                }
            }
        }
    }
}
