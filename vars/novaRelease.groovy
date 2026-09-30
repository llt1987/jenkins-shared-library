def dockerProcess(SERVICE_NAME, BRANCH, NOVA_VERSION){
    sh "docker build -t harbor.novacmx.com/posttrade/${SERVICE_NAME}:${BRANCH} -t harbor.novacmx.com/posttrade/${SERVICE_NAME}:latest -f novaserver/docker/${SERVICE_NAME}.Dockerfile novaserver/"
    sh "docker push harbor.novacmx.com/posttrade/${SERVICE_NAME}:${BRANCH}"
    sh "docker push harbor.novacmx.com/posttrade/${SERVICE_NAME}:latest"
    sh "$HOME/scripts/copy_docker_images.sh ${SERVICE_NAME} ${BRANCH} ${NOVA_VERSION}"
    if ("{BRANCH}" != "latest"){
        sh "docker rmi harbor.novacmx.com/posttrade/${SERVICE_NAME}:${BRANCH} || true"
    }
    sh "docker rmi harbor.novacmx.com/posttrade/${SERVICE_NAME}:latest || true"
}

def dockerHash(Map args = [:]) {
    def scriptName = args.get('scriptName', 'BOFIS.SAM-31404_HashNTIER.sh')
    def novaVersion = args.get('novaVersion', 'latest')

    def scriptContent = libraryResource "scripts/${scriptName}"
    
    def tempScript = "${env.WORKSPACE}/temp_${scriptName}"
    writeFile file: tempScript, text: scriptContent
    sh "chmod +x ${tempScript}"
    
    sh "${tempScript} ${novaVersion}"
}

// prepare packages for nova release
def ntierCommonPackages() {
    return [
        "ntier/dockerscripts.tar",
        "ntier/dockerscripts_v2.tar",
        "ntier/nginx.tar.gz",
        "ntier/novagui.tar.gz",
        "ntier/novakeycloak.tar.gz",
        "ntier/novaserver.tar.gz",
        "ntier/novastp-19c-ol8.tar.gz"
    ]
}

def portainerPackages() {
    return [
        "ntier/portainer-agent.tar.gz",
        "ntier/portainer-ce.tar.gz"
    ]
}

def preCheckPackages(Map args = [:]) {
    def buildDir = args.get('buildDir', '/mnt/novabld')
    def version = args.get('version', '')
    def packages = args.get('packages', [])
    def extraPackages = args.get('extraPackages', [])

    if (!version) error "preCheck: version not specified"

    def novaBuildPath = novaUtils.getNovaBuildPath(buildDir: buildDir, version: version)

    def existList = []
    def missingList = []

    def checkItem = { String path ->
        def fullPath = path.startsWith(buildDir) ? path : "${novaBuildPath}/${path}"
        def status = sh(
            script: """
                if [ -e "${fullPath}" ]; then
                    exit 0
                else
                    exit 1
                fi
            """,
            returnStatus: true
        )
        if (status == 0) {
            existList << path
        } else {
            missingList << path
        }
    }

    packages.each { p -> checkItem(p) }
    extraPackages.each { p -> checkItem("novaarchive/posttrade-tools/${p}") }

    return [
        exist: existList,
        missing: missingList
    ]
}

def prepareRelease(Map args = [:]) {
    def buildDir = args.get('buildDir', "/mnt/novabld")
    def releaseDir = args.get('releaseDir', "/mnt/Novarel")
    def version = args.get('version', '')
    def client = args.get('client', '')
    def patch = args.get('patch', '')
    def packages = args.get('packages', [])
    def extraPackages = args.get('extraPackages', [])
    def archivePackages = args.get('archivePackages', [])
    def mapping = args.get('mapping', [])

    def skipIfExists = args.get('skipIfExists', true)

    if (!version) {
        error "version not specified"
    }

    if (!client) {
        error "client not specified"
    }

    if ((!packages || packages.isEmpty()) &&
        (!extraPackages || extraPackages.isEmpty()) &&
        (!archivePackages || archivePackages.isEmpty()) &&
        (!mapping || mapping.isEmpty())) {

        echo "WARNING: Both packages and mapping are empty. Nothing to prepare."
    }

    def novaBuildPath = novaUtils.getNovaBuildPath(
        buildDir: buildDir,
        version: version
    )

    def novaReleasePath = novaUtils.getNovaReleasePath(
        releaseDir: releaseDir,
        version: version,
        client: client,
        patch: patch
    )

    def archiveReleasePath = novaUtils.getNovaArchivePath(
        version: version,
        client: client,
        patch: patch
    )

    sh """
        mkdir -p '${novaReleasePath}'
        mkdir -p '${archiveReleasePath}'
    """

    def successList = []
    def failList = []
    def skipList = []

    def copyItem = { String name, String src, String dst ->
        def dstDir = dst.substring(0, dst.lastIndexOf('/'))

        def status = sh(
            script: """
                if [ ! -e "${src}" ]; then
                    echo "[ERROR] Source not found: ${src}"
                    exit 2
                fi

                mkdir -p "${dstDir}"

                # ----- FILE -----
                if [ -f "${src}" ]; then
                    if [ "${skipIfExists}" = "true" ] && [ -e "${dst}" ]; then
                        echo "[SKIP] File exists: ${dst}"
                        exit 10
                    fi
                    cp "${src}" "${dst}"
                    exit 0
                fi

                # ----- DIRECTORY -----
                if [ -d "${src}" ]; then
                    mkdir -p "${dst}"
                    cp -r "${src}/." "${dst}/"
                    exit 0
                fi

                exit 2
            """,
            returnStatus: true
        )

        if (status == 0) {
            successList << name
        }
        else if (status == 10) {
            skipList << name
        }
        else {
            failList << name
        }
    }

    // Helper function to check if a package matches a mapping rule
    def findMatchedDest = { pkgSrc, defaultDest ->

        def match = mapping.find { m ->
            m.src == pkgSrc ||
            pkgSrc.endsWith(m.src) ||
            pkgSrc.contains(m.src + "/")
        }

        if (match) {
            // Apply the new destination mapping rules
            def mappedDest = match.dest.startsWith('/')
                ? match.dest
                : "${novaReleasePath}/${match.dest}"
            // If the matched rule is a directory replacement but the package provides a specific sub-file

            if (match.dest.endsWith('/')) {

                def fileName = pkgSrc.tokenize('/').last()

                return "${mappedDest}${fileName}"
            }

            return mappedDest
        }

        return defaultDest
    }

    // Unified list of [src, dest, originalName]
    def allRules = []

    /*
     * Standard release packages
     */
    packages.each { p ->

        def srcPath = "${novaBuildPath}/${p}"
        def defaultDestPath = "${novaReleasePath}/${p}"
        def finalDestPath = findMatchedDest(p, defaultDestPath)

        allRules << [
            src : srcPath,
            dest : finalDestPath,
            originalName : p
        ]
    }

    /*
     * Extra packages
     */
    extraPackages.each { p ->

        def srcPath =
            "${buildDir}/novaarchive/posttrade-tools/${p}"

        def defaultDestPath =
            "${novaReleasePath}/posttrade-tools/${p}"

        def finalDestPath =
            findMatchedDest(
                "posttrade-tools/${p}",
                defaultDestPath
            )

        allRules << [
            src : srcPath,
            dest : finalDestPath,
            originalName : "posttrade-tools/${p}"
        ]
    }

    /*
     * Archive packages
     * Copied separately from client release
     */
    archivePackages.each { p ->

        def srcPath = "${novaBuildPath}/${p}"

        def relativeDir = p.substring(0, p.lastIndexOf('/'))

        allRules << [
            src : srcPath,
            dest : "${archiveReleasePath}/${relativeDir}/",
            originalName : "ARCHIVE:${p}"
        ]
    }

    /*
     * Additional mapping rules
     */
    mapping.each { m ->

        if (!packages.contains(m.src) &&
            !extraPackages.contains(m.src) &&
            !archivePackages.contains(m.src) &&
            m.src.contains('*')) {

            def srcPath = m.src.startsWith('/')
                ? m.src
                : "${novaBuildPath}/${m.src}"

            def destPath = m.dest.startsWith('/')
                ? m.dest
                : "${novaReleasePath}/${m.dest}"

            allRules << [
                src : srcPath,
                dest : destPath,
                originalName : m.src
            ]
        }
    }

    /*
     * Resolve wildcards
     */
    def resolvedRules = []

    allRules.each { rule ->

        if (rule.src.contains('*')) {

            def parentDir =
                rule.src.substring(
                    0,
                    rule.src.lastIndexOf('/')
                )

            def findResult = sh(
                script: """
                    set +e
                    find "${parentDir}" \
                        -path "${rule.src}" \
                        -type f 2>/dev/null
                """,
                returnStdout: true
            ).trim()

            if (findResult) {

                def matchedFiles = findResult.split('\n')

                matchedFiles.each { matchedSrc ->

                    def fileName =
                        matchedSrc.tokenize('/').last()
                    // If dest contains a wildcard because it was inherited directly from mappedDest, 
                    // strip out everything from the last '/' where the wildcard is
                    def rawDest = rule.dest

                    if (rawDest.contains('*')) {
                        rawDest =
                            rawDest.substring(
                                0,
                                rawDest.lastIndexOf('/') + 1
                            )
                    }

                    def resolvedDst = rawDest
                    // If dest is a directory (ends with /) or has multiple matches
                    if (rawDest.endsWith('/')) {

                        resolvedDst =
                            "${rawDest}${fileName}"

                    } else if (matchedFiles.size() > 1) {

                        resolvedDst =
                            "${rawDest}/${fileName}"
                    }

                    resolvedRules << [
                        src : matchedSrc,
                        dest : resolvedDst,
                        originalName : rule.originalName
                    ]
                }
            }
            else {

                echo "[WARNING] No files found matching source pattern: ${rule.src}"

                failList << rule.originalName
            }

        } else {
            // No wildcard, try to resolve as directory or single file
            def status = sh(
                script: """
                    if [ -d "${rule.src}" ]; then exit 1; fi
                    if [ -f "${rule.src}" ]; then exit 0; fi
                    exit 2
                """,
                returnStatus: true
            )

            if (status == 1) {
                // It's a directory
                resolvedRules << [
                    src : rule.src,
                    dest : rule.dest,
                    originalName : rule.originalName,
                    isDir : true
                ]

            } else if (status == 0) {
                // It's a single file
                def resolvedDst = rule.dest

                if (rule.dest.endsWith('/')) {

                    def fileName =
                        rule.src.tokenize('/').last()

                    resolvedDst =
                        "${rule.dest}${fileName}"
                }

                resolvedRules << [
                    src : rule.src,
                    dest : resolvedDst,
                    originalName : rule.originalName
                ]

            } else {

                echo "[WARNING] Source not found: ${rule.src}"

                failList << rule.originalName
            }
        }
    }

    /*
     * Remove duplicates
     */
    def uniqueRulesMap = [:]

    resolvedRules.each { rule ->

        def key = "${rule.src}::${rule.dest}"

        if (!uniqueRulesMap.containsKey(key)) {
            uniqueRulesMap[key] = rule
        }
    }

    resolvedRules = uniqueRulesMap.values().toList()

    /*
     * Copy files
     */
    resolvedRules.each { rule ->

        def ruleDst = rule.dest
        // If it's explicitly marked as directory from earlier check
        if (rule.isDir) {
            ruleDst =
                rule.dest.endsWith('/')
                    ? rule.dest
                    : "${rule.dest}/"
        }

        copyItem(
            rule.originalName,
            rule.src,
            ruleDst
        )
    }

    return [
        releasePath : novaReleasePath,
        archivePath : archiveReleasePath,
        success : successList,
        skipped : skipList,
        failed : failList
    ]
}

def verifyRelease(Map args = [:]) {
    def result = args.get("result")
    def failOnError = args.get("failOnError", true) as boolean

    if (!result) {
        error "verifyRelease: result is required"
    }

    def success = result.success ?: []
    def skipped = result.skipped ?: []
    def failed  = result.failed  ?: []

    echo "================ RELEASE VERIFY ================"
    echo "Release path : ${result.releasePath ?: '(unknown)'}"

    echo "---- COPY SUCCESS ----"
    echo success ? success.join('\n') : '(none)'

    echo "---- COPY SKIPPED ----"
    echo skipped ? skipped.join('\n') : '(none)'

    echo "---- COPY FAILED ----"
    echo failed ? failed.join('\n') : '(none)'

    if (failed.size() > 0) {
        if (failOnError) {
            error "Release failed. Failed items: ${failed}"
        }
        currentBuild.result = 'UNSTABLE'
        echo "[WARNING] Release completed with failures"
    } else {
        echo "[OK] Release verification passed"
    }
}

// NOVACMX-SEC-SIG-001 Phase 1: SHA-256 manifest for every artefact in a release folder
def generateSha256Manifest(Map args = [:]) {
    def releasePath = args.get('releasePath', '')
    def skipFolders = args.get('skipFolders', ['archive'])
    if (!releasePath) {
        error "generateSha256Manifest: releasePath is required"
    }

    def pruneExpr = skipFolders.collect { folder ->
        if (!(folder ==~ /^[A-Za-z0-9._-]+$/)) {
            error "generateSha256Manifest: invalid skip folder name: ${folder}"
        }
        "-path './${folder}' -prune -o"
    }.join(' ')

    echo "Skip folders: ${skipFolders ? skipFolders.join(', ') : '(none)'}"

    def fileCount = sh(
        script: """
            set -eu
            RELEASE_DIR='${releasePath}'
            if [ ! -d "\$RELEASE_DIR" ]; then
                echo "[ERROR] Release directory not found: \$RELEASE_DIR" >&2
                exit 1
            fi
            TMP_MANIFEST=\$(mktemp)
            (
                cd "\$RELEASE_DIR"
                find . ${pruneExpr} -type f ! -name 'SHA256SUMS' ! -name 'SHA256SUMS.asc' -printf '%P\\0' \
                    | sort -z \
                    | xargs -0 -r sha256sum
            ) > "\$TMP_MANIFEST"
            if [ ! -s "\$TMP_MANIFEST" ]; then
                rm -f "\$TMP_MANIFEST"
                echo "[ERROR] No artefacts found to hash in \$RELEASE_DIR" >&2
                exit 1
            fi
            mv "\$TMP_MANIFEST" "\$RELEASE_DIR/SHA256SUMS"
            wc -l < "\$RELEASE_DIR/SHA256SUMS"
        """,
        returnStdout: true
    ).trim()

    echo "[OK] Wrote ${releasePath}/SHA256SUMS (${fileCount} artefact(s))"
    return "${releasePath}/SHA256SUMS"
}

def verifySha256Manifest(Map args = [:]) {
    def releasePath = args.get('releasePath', '')
    if (!releasePath) {
        error "verifySha256Manifest: releasePath is required"
    }

    sh """
        set -eu
        RELEASE_DIR='${releasePath}'
        if [ ! -f "\$RELEASE_DIR/SHA256SUMS" ]; then
            echo "[ERROR] SHA256SUMS not found in \$RELEASE_DIR. Run Phase 1 first." >&2
            exit 1
        fi
        (cd "\$RELEASE_DIR" && sha256sum -c SHA256SUMS)
    """
    echo "[OK] SHA256SUMS self-check passed for ${releasePath}"
}

def archiveIntegrityFiles(Map args = [:]) {
    def releasePath = args.get('releasePath', '')
    def files = args.get('files', ['SHA256SUMS'])
    if (!releasePath) {
        error "archiveIntegrityFiles: releasePath is required"
    }

    files.each { name ->
        sh "cp -f '${releasePath}/${name}' '${env.WORKSPACE}/${name}'"
    }
    archiveArtifacts artifacts: files.join(','), fingerprint: true, allowEmptyArchive: false
}

// NOVACMX-SEC-SIG-001 Phase 2: detached GPG signature over SHA256SUMS (ephemeral keyring)
def signSha256Manifest(Map args = [:]) {
    def releasePath = args.get('releasePath', '')
    def signingSubkeyCredId = args.get('signingSubkeyCredId', 'novacmx-gpg-signing-subkey')
    def passphraseCredId = args.get('passphraseCredId', 'novacmx-gpg-signing-passphrase')
    def keyIdCredId = args.get('keyIdCredId', 'novacmx-gpg-signing-key-id')

    if (!releasePath) {
        error "signSha256Manifest: releasePath is required"
    }

    withCredentials([
        file(credentialsId: signingSubkeyCredId, variable: 'GPG_SIGNING_SUBKEY_FILE'),
        string(credentialsId: passphraseCredId, variable: 'GPG_PASSPHRASE'),
        string(credentialsId: keyIdCredId, variable: 'GPG_SIGNING_KEY_ID')
    ]) {
        sh """
            set -eu
            RELEASE_DIR='${releasePath}'
            if [ ! -f "\$RELEASE_DIR/SHA256SUMS" ]; then
                echo "[ERROR] SHA256SUMS not found in \$RELEASE_DIR. Run Phase 1 first." >&2
                exit 1
            fi

            export GNUPGHOME=\$(mktemp -d)
            cleanup() { rm -rf "\$GNUPGHOME"; }
            trap cleanup EXIT

            if ! grep -q "BEGIN PGP" "\$GPG_SIGNING_SUBKEY_FILE"; then
                echo "[ERROR] ${signingSubkeyCredId} is not an armored PGP key file." >&2
                exit 1
            fi
            gpg --batch --import "\$GPG_SIGNING_SUBKEY_FILE"
            gpg --batch --yes --pinentry-mode loopback --passphrase "\$GPG_PASSPHRASE" \\
                --local-user "\$GPG_SIGNING_KEY_ID" \\
                --armor --detach-sign --output "\$RELEASE_DIR/SHA256SUMS.asc" "\$RELEASE_DIR/SHA256SUMS"
            gpg --verify "\$RELEASE_DIR/SHA256SUMS.asc" "\$RELEASE_DIR/SHA256SUMS"
        """
    }
    echo "[OK] Signed and verified ${releasePath}/SHA256SUMS.asc"
    return "${releasePath}/SHA256SUMS.asc"
}

def zipReleaseFolder(Map args = [:]) {
    def folderPath = args.get('folderPath', '')
    def zipPath = args.get('zipPath', '')
    def skipFolders = args.get('skipFolders', ['archive'])

    if (!folderPath) {
        error "zipReleaseFolder: folderPath is required"
    }
    if (!zipPath) {
        error "zipReleaseFolder: zipPath is required"
    }

    def folderName = folderPath.tokenize('/').last()
    def parentPath = folderPath.substring(0, folderPath.lastIndexOf('/'))
    def zipFileName = zipPath.tokenize('/').last()
    def excludeArgs = skipFolders.collect { folder ->
        if (!(folder ==~ /^[A-Za-z0-9._-]+$/)) {
            error "zipReleaseFolder: invalid skip folder name: ${folder}"
        }
        "-x '${folderName}/${folder}/*'"
    }.join(' ')
    excludeArgs = "${excludeArgs} -x '${folderName}/${zipFileName}'".trim()

    sh """
        set -eu
        if [ ! -d '${folderPath}' ]; then
            echo "[ERROR] Folder not found: ${folderPath}" >&2
            exit 1
        fi
        rm -f '${zipPath}'
        (cd '${parentPath}' && zip -r '${zipPath}' '${folderName}' ${excludeArgs})
        if [ ! -d '${folderPath}' ]; then
            echo "[ERROR] Release folder disappeared after zip: ${folderPath}" >&2
            exit 1
        fi
        echo "[OK] Release folder still present:"
        ls -ld '${folderPath}'
    """
    echo "[OK] Zipped ${folderPath} -> ${zipPath}"
    return zipPath
}

def encryptFileWithClientPubkey(Map args = [:]) {
    def inputPath = args.get('inputPath', '')
    def outputPath = args.get('outputPath', '')
    def pubkeyCredId = args.get('pubkeyCredId', '')

    if (!inputPath) {
        error "encryptFileWithClientPubkey: inputPath is required"
    }
    if (!outputPath) {
        error "encryptFileWithClientPubkey: outputPath is required"
    }
    if (!pubkeyCredId) {
        error "encryptFileWithClientPubkey: pubkeyCredId is required"
    }

    withCredentials([
        file(credentialsId: pubkeyCredId, variable: 'CLIENT_PUBKEY_FILE')
    ]) {
        sh """
            set -eu
            if [ ! -f '${inputPath}' ]; then
                echo "[ERROR] File not found: ${inputPath}" >&2
                exit 1
            fi

            export GNUPGHOME=\$(mktemp -d)
            cleanup() { rm -rf "\$GNUPGHOME"; }
            trap cleanup EXIT

            gpg --batch --quiet --import "\$CLIENT_PUBKEY_FILE"
            RECIPIENT=\$(gpg --list-keys --with-colons | awk -F: '/^fpr:/{print \$10; exit}')
            if [ -z "\$RECIPIENT" ]; then
                echo "[ERROR] No fingerprint found in client public key (${pubkeyCredId})" >&2
                exit 1
            fi
            echo "[OK] Encrypting for client key \$RECIPIENT"

            #mkdir -p "\$(dirname '${outputPath}')"
            #rm -f '${outputPath}'
            gpg --batch --yes --trust-model always \\
                --encrypt --recipient "\$RECIPIENT" \\
                --output '${outputPath}' '${inputPath}'
        """
    }
    echo "[OK] Encrypted ${inputPath} -> ${outputPath}"
    return outputPath
}
