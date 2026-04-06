def call(Map config = [:], Closure body) {
    pipeline {

        /* ✅ Agent is configurable, NOT frozen */
        agent config.agent ?: none

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
            body.delegate = this
            body()
        }
    }
}
