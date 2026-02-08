// Client release email notification
def sendMailClientRelease(Map args = [:]) {
    def clientName    = args.get('clientName', '')
    def userId        = args.get('userId', 'Unknown User')
    def buildStatus   = args.get('buildStatus', 'UNKNOWN')
    def version       = args.get('version', 'N/A')
    def color         = args.get('color', '#808080') // Default to gray
    def jiraTicket    = args.get('jiraTicket', 'N/A')
    def recipients    = args.get('recipients', '')
    def attachLog     = args.get('attachLog', false)
    if (clientName == '' || clientName == null) {
        error "Client name not specified for email notification"
    }
    if (recipients == '' || recipients == null) {
        error "Recipients not specified for email notification"
    }

    script {
        emailext(
            subject: "Jenkins Build: Job '${JOB_NAME}' (${version}) - ${jiraTicket}",
            body: """<!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Build Notification</title>
                <style>
                    body { font-family: Arial, sans-serif; background: #f4f4f4; padding: 20px; }
                    .container { max-width: 600px; margin: auto; background: #fff; padding: 20px; border-radius: 8px; box-shadow: 0 2px 10px rgba(0, 0, 0, 0.1); }
                    h1 { color: #333; text-align: center; }
                    .status { background: ${color}; color: #fff; padding: 10px; border-radius: 4px; text-align: center; font-weight: bold; margin-top: 20px; }
                    a { color: #0066cc; text-decoration: none; }
                    a:hover { text-decoration: underline; }
                </style>
            </head>
            <body>
            <div class="container">
                <h1>${clientName} Release Notification</h1>
                <p><strong>Build Status:</strong> <span class="status">${buildStatus}</span></p>
                <p><strong>Job:</strong> ${JOB_NAME}</p>
                <p><strong>Version:</strong> ${version}</p>
                <p><strong>Jenkins Build Number:</strong> ${BUILD_ID}</p>
                <p>User ${userId} is requesting release ${clientName} ${version} <a href="${JOB_URL}">${JOB_URL}</a></p>
            </div>
            </body>
            </html>""",
            to: "${recipients}",
            attachLog: "${attachLog}"
        )       
    }
}

def sendMailClientReleaseSuccess(Map args = [:]) {
    args['buildStatus'] = 'SUCCESS'
    args['color'] = '#28a745'
    sendMailClientRelease(args)
}

def sendMailClientReleaseFailure(Map args = [:]) {
    args['buildStatus'] = 'FAILURE'
    args['color'] = '#dc3545'
    sendMailClientRelease(args)
}


// General build email notification
def sendMailBuild(Map args = [:]) {
    def userId       = args.get('userId', 'Unknown User')
    def buildStatus  = args.get('buildStatus', 'UNKNOWN')
    def version      = args.get('version', 'N/A')
    def color        = args.get('color', '#808080') // Default gray
    def recipients   = args.get('recipients', '')
    def attachLog    = args.get('attachLog', false)

    if (!recipients) {
        error "Recipients not specified for email notification"
    }

    def triggeredByHtml = (userId && userId != "null") ? "<p><strong>Triggered by:</strong> ${userId}</p>" : ""

    script {
        emailext(
            subject: "Jenkins Build: ${JOB_NAME} (${version}) - ${buildStatus}",
            body: """<!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Build Notification</title>
                <style>
                    body { font-family: Arial, sans-serif; background: #f4f4f4; padding: 20px; }
                    .container { max-width: 600px; margin: auto; background: #fff; padding: 20px; border-radius: 8px; box-shadow: 0 2px 10px rgba(0, 0, 0, 0.1); }
                    h1 { color: #333; text-align: center; }
                    .status { background: ${color}; color: #fff; padding: 10px; border-radius: 4px; text-align: center; font-weight: bold; margin-top: 20px; }
                    a { color: #0066cc; text-decoration: none; }
                    a:hover { text-decoration: underline; }
                </style>
            </head>
            <body>
            <div class="container">
                <h1>Build Notification</h1>
                <p><strong>Status:</strong> <span class="status">${buildStatus}</span></p>
                <p><strong>Job:</strong> ${JOB_NAME}</p>
                <p><strong>Build ID:</strong> ${BUILD_ID}</p>
                <p><strong>Version:</strong> ${version}</p>
                ${triggeredByHtml}
                <p>Check build details: <a href="${JOB_URL}">${JOB_URL}</a></p>
            </div>
            </body>
            </html>""",
            to: "${recipients}",
            attachLog: "${attachLog}"
        )
    }
}

def sendMailBuildSuccess(Map args = [:]) {
    args['buildStatus'] = 'SUCCESS'
    args['color'] = '#28a745'
    sendMailBuild(args)
}

def sendMailBuildFailure(Map args = [:]) {
    args['buildStatus'] = 'FAILURE'
    args['color'] = '#dc3545'
    sendMailBuild(args)
}

def needApprovalRelease(Map args = [:]) {
    def userId        = args.get('userId', 'Unknown User')
    def version       = args.get('version', 'N/A')
    def recipients    = args.get('recipients', '')
    
    script {
        emailext(
            subject: "Jenkins Build: ${JOB_NAME} (${version}) - Approval Needed",
            body: """<!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Build Approval Needed</title>
                <style>
                    body { font-family: Arial, sans-serif; background: #f4f4f4; padding: 20px; }
                    .container { max-width: 600px; margin: auto; background: #fff; padding: 20px; border-radius: 8px; box-shadow: 0 2px 10px rgba(0, 0, 0, 0.1); }
                    h1 { color: #333; text-align: center; }
                    .status { color: #fff; padding: 10px; border-radius: 4px; text-align: center; font-weight: bold; margin-top: 20px; }
                    a { color: #0066cc; text-decoration: none; }
                    a:hover { text-decoration: underline; }
                </style>
            </head>
            <body>
            <div class="container">
                <h1>Build Approval Needed</h1>
                <p><strong>Job:</strong> ${JOB_NAME}</p>
                <p><strong>Build ID:</strong> ${BUILD_ID}</p>
                <p><strong>Version:</strong> ${version}</p>
                <p>Triggered by: ${userId}</p>
                <p>Check build details: <a href="${JOB_URL}">${JOB_URL}</a></p>
                <b>Need to approve in 120 minutes</b>
            </div>
            </body>
            </html>""",
            to: "${recipients}"
        )
    }
}