def commentJiraTicketNotify(Map args = [:]) {
    def jiraTickets    = args.get('jiraTickets', '')
    def userId        = args.get('userId', 'Unknown User')
    def buildStatus   = args.get('buildStatus', 'UNKNOWN')
    def version       = args.get('version', 'N/A')

    if (jiraTickets == '' || jiraTickets == null) {
        error "jiraTickets not specified"
    }

    def tickets = jiraTickets.split(',').collect { it.trim() }
    tickets.each { ticketId ->
        jiraComment body: "Jenkins pipeline: ${JOB_NAME}\nVersion: ${version}\nStatus: ${buildStatus}\nUser: ${userId}\nPipeline URL: ${BUILD_URL}", issueKey: "${ticketId}"
    }
}