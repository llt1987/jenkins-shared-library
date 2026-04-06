def call(Closure body) {
    pipeline {
        agent none   // ✅ not frozen globally

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
            body()
        }
    }
}
