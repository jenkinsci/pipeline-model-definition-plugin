// This Jenkinsfile's main purpose is to show a real-world-ish example
// of what Pipeline config syntax actually looks like. 
pipeline {
    // Make sure that the tools we need are installed and on the path.
    tools {
        maven "Maven 3.3.9"
        jdk "Oracle JDK 8u40"
    }

    // Run on any executor.
    agent label:""

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

    // This will also run after the stages *and* after postBuild. Further improvements planned
    // for the mail step (https://issues.jenkins-ci.org/browse/JENKINS-37869,
    // https://issues.jenkins-ci.org/browse/JENKINS-37870 to start, more to come) and other
    // notifier plugins to be more user-friendly.
    notifications {
        success {
            mail to:"abayer@cloudbees.com", subject:"SUCCESS: ${currentBuild.fullDisplayName}", body: "Yay, we passed."
        }
        failure {
            mail to:"abayer@cloudbees.com", subject:"FAILURE: ${currentBuild.fullDisplayName}", body: "Boo, we failed."
        }
        unstable {
            mail to:"abayer@cloudbees.com", subject:"UNSTABLE: ${currentBuild.fullDisplayName}", body: "Huh, we're unstable."
        }
        changed {
            mail to:"abayer@cloudbees.com", subject:"CHANGED: ${currentBuild.fullDisplayName}", body: "Wow, our status changed!"
        }
    }

    stages {
        // While there's only one stage here, you can specify as many stages as you like!
        stage("build") {
            sh 'mvn clean install -Dmaven.test.failure.ignore=true'
        }
    }

}
