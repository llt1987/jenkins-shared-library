import groovy.json.JsonOutput

def call(Map config = [:]) {

    String status = config.status ?: "INFO"
    String message = config.message ?: "No message"
    String webhook = config.webhook ?: "https://default3f9410505131451c8ad67135423e60.94.environment.api.powerplatform.com:443/powerautomate/automations/direct/cu/10/workflows/90f2407e645848f0a08b6fba1e94871a/triggers/manual/paths/invoke?api-version=1&sp=%2Ftriggers%2Fmanual%2Frun&sv=1.0&sig=9yqvlbr5gxt_PhbwMJ0qhzEJdAH8Mf386cTplfQ54RQ"

    def color = "#0078D4"

    switch(status.toUpperCase()) {
        case "SUCCESS":
            color = "#107C10"
            break
        case "FAILURE":
            color = "#D13438"
            break
        case "UNSTABLE":
            color = "#FF8C00"
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
                type : "TextBlock",
                text : "Job: ${env.JOB_NAME}",
                wrap : true
            ],
            [
                type : "TextBlock",
                text : "Build: #${env.BUILD_NUMBER}",
                wrap : true
            ],
            [
                type : "TextBlock",
                text : "Status: ${status}",
                color: "Accent",
                wrap : true
            ],
            [
                type : "TextBlock",
                text : message,
                wrap : true
            ],
            [
                type : "TextBlock",
                text : "URL: ${env.BUILD_URL}",
                wrap : true
            ]
        ]
    ]

    httpRequest(
        httpMode: 'POST',
        contentType: 'APPLICATION_JSON',
        requestBody: JsonOutput.toJson(payload),
        url: webhook,
        validResponseCodes: '200:299'
    )
}