package org.company.utils

class genericFunctions implements Serializable {

    static Closure commonPipelineOptions(Map config = [:]) {
        return {
            skipDefaultCheckout()
            disableConcurrentBuilds()
            timestamps()
            buildDiscarder(
                logRotator(
                    numToKeepStr: config.numToKeep ?: '5',
                    daysToKeepStr: config.daysToKeep ?: '5'
                )
            )
        }
    }
}
