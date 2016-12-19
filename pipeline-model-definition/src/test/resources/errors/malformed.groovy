pipeline {
    agent none
    stages {
        stage("Foo") {
            steps {
              echo "Hello"
        }
    }
}