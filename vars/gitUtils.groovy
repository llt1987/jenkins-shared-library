def getBuildBranch(paramBranch){
    if( env.gitlabSourceBranch == null && paramBranch != null ){
        return paramBranch
    }
    return env.gitlabSourceBranch
}

def getImageTagFromBranch(branch){
    if( "${branch}" =~ /refs\/tags\/NOVA[0-9]-00-[0-9]+-[0-9]+/  ) {
        return "${branch.substring(10, )}"
    }
    if ("${branch}" == 'master' || "${branch}" == 'origin/master'){
        return 'latest'
    }
    return "${branch}"
}

def getLatestTag(String repo, String credentialsId, String regexPattern = "*NOVA*-00-*") {
    if (!repo) {
        error "Repository not specified"
    }

    withCredentials([usernamePassword(credentialsId: credentialsId, usernameVariable: 'GIT_USER', passwordVariable: 'GIT_PASS')]) {
        def cmd = """
            git ls-remote --tags https://${GIT_USER}:${GIT_PASS}@${repo}.git --tags ${regexPattern} \
            | awk '{print \$2}' \
            | sed 's#refs/tags/##' \
            | grep -v '\\^{}' \
            | sort -V \
            | tail -n 1
        """

        def proc = ["bash", "-c", cmd].execute()
        def output = proc.text.trim()
        proc.waitFor()

        if (proc.exitValue() != 0 || !output) {
            return null
        }

        return output
    }
}

def fetchTags(String repo, String username, String password, String regexPattern) {
    def cmd = "git ls-remote --refs https://" + username + ":" + password + "@" + repo + ".git --tags " + regexPattern
    def proc = cmd.execute()
    proc.waitFor()
    def tags = proc.in.text.readLines()
        .collect { it.split()[1].replace('refs/tags/', '').replace('refs/heads/', '') }
    return tags
}