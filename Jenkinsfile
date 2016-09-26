// This Jenkinsfile's main purpose is to show a real-world-ish example
// of what Pipeline config syntax actually looks like. 
pipeline {
    // Make sure that the tools we need are installed and on the path.
    tools {
        maven "mvn"
        jdk "jdk8"
    }

    // Run on executors with the "docker" label, because it's either that or Windows here.
    agent label:"java"

    // Make sure we have GIT_COMMITTER_NAME and GIT_COMMITTER_EMAIL set due to machine weirdness.
    environment {
        GIT_COMMITTER_NAME = "jenkins"
        GIT_COMMITTER_EMAIL = "jenkins@jenkins.io"
    }
    
    // The order that sections are specified doesn't matter - this will still be run
    // after the stages, even though it's specified before the stages.
    postBuild {
        // No matter what the build status is, run these steps. There are other conditions
        // available as well, such as "success", "failed", "unstable", and "changed".
        always {
            archive "target/**/*"
            junit 'target/surefire-reports/*.xml'
        }
    }

    stages {
        // While there's only one stage here, you can specify as many stages as you like!
        stage("build") {
            sh 'mvn clean install -Dmaven.test.failure.ignore=true'
        }
    }

}
