// This Jenkinsfile's main purpose is to show a real-world-ish example
// of what Pipeline config syntax actually looks like. 
pipeline {
    // Make sure that the tools we need are installed and on the path.
    tools {
        maven "mvn"
        jdk "jdk8"
    }

    // Run on executors with the "docker" label, because it's either that or Windows here.
    agent {
        label "java"
    }

    // Make sure we have GIT_COMMITTER_NAME and GIT_COMMITTER_EMAIL set due to machine weirdness.
    environment {
        GIT_COMMITTER_NAME = "jenkins"
        GIT_COMMITTER_EMAIL = "jenkins@jenkins.io"
    }
    
    // The order that sections are specified doesn't matter - this will still be run
    // after the stages, even though it's specified before the stages.
    post {
        // No matter what the build status is, run this step. There are other conditions
        // available as well, such as "success", "failed", "unstable", and "changed".
        always {
            junit '*/target/surefire-reports/*.xml'
        }
        success {
            archive "*/target/**/*"
        }
        unstable {
            archive "*/target/**/*"
        }
    }

    stages {
        // While there is only one stage here, you can specify as many stages as you like!
        stage("build") {
            steps {
                sh 'mvn clean install -Dtest=maven.test.failure.ignore=true'
            }
        }
    }

}
