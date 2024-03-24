pipeline {
    agent none
    stages {
        stage('BuildAndTest') {
            matrix {
                agent any
                axes {
                    axis {
                        name 'PLATFORM'
                        values 'linux', 'windows'
                    }
                    axis {
                        name 'BROWSER'
                        values 'firefox'
                    }
                }
                stages {
                    stage('Build') {
                        when {
                            branch 'testing'
                        }
                        steps {
                            echo "Do Build1 for ${PLATFORM} - ${BROWSER}"
                        }

                    }
                }
            }
        }
    }
}
