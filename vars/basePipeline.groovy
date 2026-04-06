def call(Closure body) {
    pipeline {
        agent none

        options {
            skipDefaultCheckout()
            disableConcurrentBuilds()
            timestamps()
            buildDiscarder(
                logRotator(
                    numToKeepStr: '5',
                    daysToKeepStr: '5'
                )
            )
        }

        stages {
            stage('Pipeline') {
                steps {
                    script {
                        body()
                    }
                }
            }
        }
    }
}