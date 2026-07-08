import groovy.json.JsonOutput

def call(Map config = [:]) {

    String status      = config.status ?: "INFO"
    String remarks     = config.remarks ?: "N/A"
    String failedStage = config.failedStage ?: ""
    String branch      = config.branch ?: (env.BRANCH_NAME ?: "N/A")
    String webhook = config.webhook ?: "https://default3f9410505131451c8ad67135423e60.94.environment.api.powerplatform.com:443/powerautomate/automations/direct/cu/10/workflows/90f2407e645848f0a08b6fba1e94871a/triggers/manual/paths/invoke?api-version=1&sp=%2Ftriggers%2Fmanual%2Frun&sv=1.0&sig=9yqvlbr5gxt_PhbwMJ0qhzEJdAH8Mf386cTplfQ54RQ"

    String statusIcon = "ℹ️"

    switch(status.toUpperCase()) {
        case "SUCCESS":
            statusIcon = "✅"
            break
        case "FAILURE":
            statusIcon = "❌"
            break
        case "UNSTABLE":
            statusIcon = "⚠️"
            break
    }

    //
    // Collect developers / committers from SCM changes
    //
    def developers = []
    def committers = []

    try {
        currentBuild.changeSets.each { changeSet ->
            changeSet.items.each { item ->

                if (item.author) {
                    developers << item.author.toString()
                    committers << item.author.toString()
                }

                if (item.commitId) {
                    echo "Commit: ${item.commitId}"
                }
            }
        }
    } catch (Exception ex) {
        echo "Unable to read changelog information: ${ex}"
    }

    developers = developers.unique().sort()
    committers = committers.unique().sort()

    def facts = [
        [
            title: "Status",
            value: "${statusIcon} ${status}"
        ],
        [
            title: "Remarks",
            value: remarks
        ]
    ]

    if (committers) {
        facts << [
            title: "Committers",
            value: committers.join(", ")
        ]
    }

    if (developers) {
        facts << [
            title: "Developers",
            value: developers.join(", ")
        ]
    }

    //
    // Show Result only for FAILURE / UNSTABLE
    //
    if (status.toUpperCase() in ["FAILURE", "UNSTABLE"] &&
        failedStage?.trim()) {

        facts << [
            title: "Result",
            value: failedStage
        ]
    }

    facts << [
        title: "Branch",
        value: branch
    ]

    def payload = [
        type   : "AdaptiveCard",
        version: "1.4",
        body   : [
            [
                type  : "TextBlock",
                text  : "Jenkins Build Notification",
                weight: "Bolder",
                size  : "Large"
            ],
            [
                type  : "TextBlock",
                text  : "Notification from ${env.JOB_NAME}: ${status}",
                weight: "Bolder",
                size  : "Medium",
                color : status.toUpperCase() == "FAILURE" ? "Attention" :
                        status.toUpperCase() == "SUCCESS" ? "Good" :
                        "Warning"
            ],
            [
                type  : "TextBlock",
                text  : "Latest status of build #${env.BUILD_NUMBER}",
                size  : "Medium",
                wrap  : true
            ],
            [
                type : "FactSet",
                facts: facts
            ]
        ],
        actions: [
            [
                type : "Action.OpenUrl",
                title: "Check Build",
                url  : env.BUILD_URL
            ]
        ]
    ]

    echo "Sending Teams Notification"

    httpRequest(
        httpMode: 'POST',
        url: webhook,
        contentType: 'APPLICATION_JSON',
        requestBody: JsonOutput.toJson(payload),
        validResponseCodes: '200:299',
        consoleLogResponseBody: true
    )
}