// This Jenkinsfile's main purpose is to show a real-world-ish example
// of what Pipeline config syntax actually looks like. 
pipeline {
    tools {
        maven "Maven 3.3.9"
        jdk "Oracle JDK 8u40"
    }

    image label:"any-executor"

    stages {
        stage("build") {
            sh 'echo "path: ${PATH}"'
            sh 'echo "M2_HOME: ${M2_HOME}"'
            sh 'mvn clean install -Dmaven.test.failure.ignore=true'
        }
    }

    postBuild {
        always {
            archive "target/**/*"
            junit 'target/surefire-reports/*.xml'
        }
    }

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
}
