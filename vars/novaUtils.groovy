def getNovaBuildPath(Map args = [:]) {
    def buildDir = args.get('buildDir', "/mnt/novabld")
    def version = args.get('version', '')
    if (!version) {
        error "version not specified"
    }
    def novaVersion = version.replaceAll(/^NOVA(\d+)-.*$/, '$1') + "00"
    return "${buildDir}/novaarchive/${novaVersion}-BUILDS/${version}"
}

def getNovaBuildPathWithVersion(Map args = [:]) {
    def buildDir = args.get('buildDir', "/mnt/novabld")
    def version = args.get('version', '')
    if (!version) {
        error "version not specified"
    }
    def novaVersion = version.replaceAll(/^NOVA(\d+)-.*$/, '$1') + "00"
	
	def path = "${buildDir}/novaarchive/${novaVersion}-BUILDS/${version}"

    return [path: path, novaVersion: novaVersion]
	
}

def getNovaReleasePath(Map args = [:]) {
    def releaseDir = args.get('releaseDir', "/mnt/Novarel")
    def version = args.get('version', '')
    def client = args.get('client', '')
    def patch = args.get('patch', '')
    if (!version) {
        error "version not specified"
    }
    def novaVersion = version.replaceAll(/^NOVA(\d+)-.*$/, '$1') + "00"
    return "${releaseDir}/${novaVersion}-BUILDS-RELEASED/${version}${client ? '-' + client : ''}${patch ? '-' + patch : ''}"
}

def getNovaReleasePathWithVersion(Map args = [:]) {
    def releaseDir = args.get('releaseDir', "/mnt/Novarel")
    def version    = args.get('version', '')
    if (!version) {
        error "version not specified"
    }

    def novaVersion = version.replaceAll(/^NOVA(\d+)-.*$/, '$1') + "00"
    def path = "${releaseDir}/${novaVersion}-BUILDS-RELEASED/${version}"

    return [path: path, novaVersion: novaVersion]
}

def getNovaReleaseOrBuildPath(Map args = [:]) {
    def buildDir = args.get('buildDir', "/mnt/novabld")
    def releaseDir = args.get('releaseDir', "/mnt/Novarel")
    def version = args.get('version', '')
    def client = args.get('client', '')
    if (!version) {
        error "version not specified"
    }
    
    def releasePath = getNovaReleasePath(releaseDir: releaseDir, version: version, client: client)
    
    if (fileExists(releasePath)) {
        return releasePath
    }
    
    return getNovaBuildPath(buildDir: buildDir, version: version)
}

def getNovaArchivePath(Map args = [:]) {
    def releaseDir = args.get('releaseDir', "/mnt/Code_archive")
    def version = args.get('version', '')
    def client = args.get('client', '')
    def patch = args.get('patch', '')
    if (!version) {
        error "version not specified"
    }
    def novaVersion = version.replaceAll(/^NOVA(\d+)-.*$/, '$1') + "00"
    return "${releaseDir}/${novaVersion}-BUILDS-RELEASED/${version}${client ? '-' + client : ''}${patch ? '-' + patch : ''}"
}