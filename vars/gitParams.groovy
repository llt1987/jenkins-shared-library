def novaTagsParameter(Map args = [:]) {
    def paramName        = args.get('name', 'NOVA_TAG')
    def paramDescription = args.get('description', "Select tag from ${args.repo ?: 'repository'}")
    def regexPattern     = args.get('regex', "*NOVA*-00-*")
    def repo             = args.get('repo', '')
    def credentialsId    = args.get('credentialsId', 'gitapac-personal-access-token')

    return [
        $class: 'CascadeChoiceParameter',
        choiceType: 'PT_SINGLE_SELECT',
        name: paramName,
        description: paramDescription,
        filterable: true,
        script: [
            $class: 'GroovyScript',
            fallbackScript: [
                classpath: [],
                sandbox: false,
                script: 'return ["Error loading tags"]'
            ],
            script: [
                classpath: [],
                sandbox: false,
                script: """
                    import jenkins.model.*

                    def credentialsId = '${credentialsId}'
                    def regexPattern = '${regexPattern}'
                    def repo = '${repo}'

                    def creds = com.cloudbees.plugins.credentials.CredentialsProvider.lookupCredentials(
                        com.cloudbees.plugins.credentials.common.StandardUsernameCredentials.class,
                        Jenkins.instance, null, null
                    ).find { it.id == credentialsId }

                    if (creds == null) {
                        return ["No credentials found for ID: " + credentialsId]
                    }

                    def fetchTags(String repo, String username, String password, String regexPattern) {
                        if (!repo) return ["Repository not specified"]
                        def cmd = "git ls-remote --refs https://" + username + ":" + password + "@" + repo + ".git --tags " + regexPattern
                        def proc = cmd.execute()
                        proc.waitFor()
                        def tags = proc.in.text.readLines()
                            .collect { it.split()[1].replace('refs/tags/', '').replace('refs/heads/', '') }
                        return tags ?: ["No matching tags"]
                    }

                    return [""] + fetchTags(repo, creds.username, creds.password.getPlainText(), regexPattern)
                """
            ]
        ]
    ]
}

def novaTagsReferenceParameter(Map args = [:]){
    def paramName        = args.get('name', 'NOVA_TAG')
    def paramDescription = args.get('description', "Select tag from ${args.repo ?: 'repository'}")
    def regexPattern     = args.get('regex', "*NOVA*-00-*")
    def credentialsId    = args.get('credentialsId', 'gitapac-personal-access-token')
    def referenceParameter = args.get('referencedParameters', 'COMPONENTS')

    return [
        $class: 'CascadeChoiceParameter',
        choiceType: 'PT_SINGLE_SELECT',
        name: paramName,
        description: paramDescription,
        filterable: true,
        referencedParameters: referenceParameter,
        script: [
            $class: 'GroovyScript',
            fallbackScript: [
                classpath: [],
                sandbox: false,
                script: 'return ["Error loading tags"]'
            ],
            script: [
                classpath: [],
                sandbox: false,
                script: """
                    import jenkins.model.*

                    def credentialsId = '${credentialsId}'
                    def regexPattern = '${regexPattern}'

                    def creds = com.cloudbees.plugins.credentials.CredentialsProvider.lookupCredentials(
                    com.cloudbees.plugins.credentials.common.StandardUsernameCredentials.class, Jenkins.instance, null, null ).find{
                        it.id == credentialsId}

                    def fetchTags(String repo, String username, String password, String regexPattern) {
                        def cmd = "git ls-remote --refs https://" + username + ":" + password + "@" + repo + ".git --tags " + regexPattern
                        def proc = cmd.execute()
                        proc.waitFor()
                        def tags = proc.in.text.readLines()
                            .collect { it.split()[1].replace('refs/tags/', '').replace('refs/heads/', '') }
                        return tags
                    }

                    def repoMap = [
                        ntierserver: "git.novacmx.com/nova/novantierserver",
                        ntiergui  : "git.novacmx.com/nova/novantiergui",
                        nxsrpc    : "git.novacmx.com/nova/nova"
                    ]
                    def selectedComponents = COMPONENTS.split(',').collect { it.trim() }
                    def intersection = null
                    selectedComponents.each { comp ->
                        if (repoMap.containsKey(comp)) {
                            def repo = repoMap[comp]
                            def tags = fetchTags(repo, creds.username, creds.password.getPlainText(), regexPattern) as Set
                            if (intersection == null) {
                                intersection = tags
                            } else {
                                intersection = intersection.intersect(tags)
                            }
                        }
                    }

                    def uniqueTags = (intersection ?: []) as List
                    uniqueTags = uniqueTags.sort()
                    return uniqueTags
                """
            ]
        ]
    ]
}