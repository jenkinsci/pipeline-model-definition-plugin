pipeline {
    agent none
    stages {
        stage("foo") {
            steps {
                echo "hello"
            }
        }
    }
}