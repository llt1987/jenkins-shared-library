// Returns OS-specific path and novaVersion based on the provided version.
// Args:
//   version              (required) - e.g. "NOVA123-4.5.6"
//   releaseDir           (optional) - override for both OSes (wins over per-OS overrides)
//   windowsReleaseDir    (optional) - default "\\shared\\local"
//   unixReleaseDir       (optional) - default "/mnt/Novarel"
//   buildsDirName        (optional) - default "<novaVersion>-BUILDS-RELEASED"
// Return: [ path: "<os-specific-path>", novaVersion: "<digits>00", os: "windows|linux|macos|unknown" ]
def call(Map args = [:]) {
    // --- Validate version ---
    def version = args.get('version', '') as String
    if (!version?.trim()) {
        error "getNovaReleasePathWithVersion: 'version' not specified"
    }

    // --- Detect OS (runs on agent when called inside steps/script) ---
    def osName = System.getProperty('os.name')?.toLowerCase() ?: ''
    def os = osName.contains('windows') ? 'windows'
           : osName.contains('linux')   ? 'linux'
           : osName.contains('mac')     ? 'macos'
           : 'unknown'
    def isWindows = (os == 'windows')

    // --- Default dirs ---
    // NOTE: UNC requires double escaping in Groovy: \\shared.local\release -> "\\\\shared.local\\release"
    def defaultWindowsDir = '\\\\shared.local\\release'
    def defaultUnixDir    = '/mnt/Novarel'

    // --- Determine releaseDir ---
    def releaseDir =
        (args.containsKey('releaseDir')                       ? args.releaseDir :
         isWindows && args.containsKey('windowsReleaseDir')   ? args.windowsReleaseDir :
         !isWindows && args.containsKey('unixReleaseDir')     ? args.unixReleaseDir :
         (isWindows ? defaultWindowsDir : defaultUnixDir)
        ) as String

    // --- Compute novaVersion as before (e.g., NOVA123-* -> 12300) ---
    def nv = version.replaceAll(/^NOVA(\d+)-.*$/, '$1')
    if (!nv || nv == version) {
        error "getNovaReleasePathWithVersion: version '${version}' does not match expected pattern 'NOVA<digits>-...'"
    }
    def novaVersion = "${nv}00"

    // Default builds directory
    def buildsDirName = (args.get('buildsDirName', "${novaVersion}-BUILDS-RELEASED")) as String

    // --- Normalization helpers ---
    def sep = isWindows ? '\\' : '/'

    // Normalize but preserve UNC prefix on Windows
    def normalizeWin = { String p ->
        if (!p) return p
        // Replace forward slashes with backslashes
        p = p.replace('/', '\\')

        // Detect UNC root form: \\host\share
        //def isUNC = (p ==~ /^\\\\[^\\]+\\[^\\]+/)
        def isDriveRoot = (p ==~ /^[A-Za-z]:\\$/)
        def isUNC = (p ==~ /^\\\\[^\\]+\\[^\\]+/)

        if (!isUNC && !isDriveRoot) {
            p = p.replaceAll(/[\\]+$/, '')
        }
        return p
    }

    def normalizeUnix = { String p ->
        if (!p) return p
        p = p.replace('\\', '/')
        if (p != '/') p = p.replaceAll(/\/+$/, '')
        return p
    }

    def normalize = isWindows ? normalizeWin : normalizeUnix

    releaseDir    = normalize(releaseDir)
    buildsDirName = normalize(buildsDirName)
    def versionLeaf = normalize(version)

    // Join helper
    def join = { a, b ->
        if (!a) return b
        if (!b) return a
        return a + sep + b
    }

    def path = join(join(releaseDir, buildsDirName), versionLeaf)

    return [
        path       : path,
        novaVersion: novaVersion,
        os         : os
    ]
}
