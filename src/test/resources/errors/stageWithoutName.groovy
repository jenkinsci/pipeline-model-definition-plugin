pipeline {
    stages {
        stage {
            steps {
                sh './test.sh'
            }
        }
    }
}