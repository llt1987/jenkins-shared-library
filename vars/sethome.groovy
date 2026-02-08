// vars/ciConfig.groovy
def call(Map args = [:]) {
    /*
     * Returns a map of standardized CI config values.
     *
     * Params (optional):
     *   branch       - overrides env.BRANCH (e.g., "release/1.2.3")
     *   novaversion  - overrides default NOVAVERSION "700"
     *   buildDir     - overrides default BUILD_DIR "/mnt/novabld"
     *
     * Usage (Jenkinsfile):
     *   def cfg = ciConfig(branch: "feature/my-branch")
     */

    // Resolve inputs / defaults
    def envBranch     = args.branch    ?: (env.BRANCH ?: env.GIT_BRANCH ?: "unknown-branch")
    def NOVAVERSION   = args.novaversion ?: "700"
    def BUILD_DIR     = args.buildDir    ?: "/mnt/novabld"

    // Static values
    def HOME          = "/home/data_jenkins_agent/cicd"
    def ACO_CONFIG    = "${HOME}/conf/config_mt1.aco"
    def NOVARPC       = "JNI"
    def PATH_SCRIPTS  = "/var/lib/jenkins/script_build"

    // Derived values
    def NOVA_BUILD_DIR     = "${BUILD_DIR}/novaarchive/${NOVAVERSION}-BUILDS/${envBranch}"
    def NOVA_SIT_BUILD_DIR = "${BUILD_DIR}/novaarchive/SIT/NOVA-${NOVAVERSION}-NTIER"

    return [
        HOME             : HOME,
        ACO_CONFIG       : ACO_CONFIG,
        NOVARPC          : NOVARPC,
        PATH_SCRIPTS     : PATH_SCRIPTS,
        NOVAVERSION      : NOVAVERSION,
        BUILD_DIR        : BUILD_DIR,
        NOVA_BUILD_DIR   : NOVA_BUILD_DIR,
        NOVA_SIT_BUILD_DIR: NOVA_SIT_BUILD_DIR,
        BRANCH           : envBranch,     // helpful to have this echoed back
    ]
}