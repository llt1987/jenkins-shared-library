def buildRetention(Map config = [:]) {
    buildDiscarder(
        logRotator(
            numToKeepStr: config.numToKeep ?: '5',
            daysToKeepStr: config.daysToKeep ?: '5'
        )
    )
}