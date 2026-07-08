import groovy.json.JsonOutput

def call(Map config = [:]) {

    String status      = config.status ?: "INFO"
    String remarks     = config.remarks ?: "N/A"
    String failedStage = config.failedStage ?: "N/A"
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
                text  : "Latest status of build #${env.BUILD_NUMBER}",
                weight: "Bolder",
                size  : "Medium"
            ],
            [
                type : "TextBlock",
                text : "Job: ${env.JOB_NAME}",
                wrap : true
            ],
            [
                type : "FactSet",
                facts: [
                    [
                        title: "Status",
                        value: "${statusIcon} ${status}"
                    ],
                    [
                        title: "Remarks",
                        value: remarks
                    ],
                    [
                        title: "Result",
                        value: failedStage
                    ],
                    [
                        title: "Branch",
                        value: branch
                    ]
                ]
            ]
        ],
        actions: [
            [
                type : "Action.OpenUrl",
                title: "Open Build",
                url  : env.BUILD_URL
            ]
        ]
    ]

    httpRequest(
        httpMode: 'POST',
        url: webhook,
        contentType: 'APPLICATION_JSON',
        requestBody: JsonOutput.toJson(payload),
        validResponseCodes: '200:299',
        consoleLogResponseBody: true
    )
}