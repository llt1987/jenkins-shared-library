def call(Map config = [:]) {
    return buildDiscarder(
        logRotator(
            numToKeepStr: config.numToKeep ?: '5',
            daysToKeepStr: config.daysToKeep ?: '5'
        )
    )
}