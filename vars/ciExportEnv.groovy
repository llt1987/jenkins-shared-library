// vars/ciExportEnv.groovy
def call(Map args = [:]) {

    def cfg = ciConfig(args)   // Reuse config generator

    cfg.each { key, value ->
        env[key] = value.toString()
    }

    echo "[ciExportEnv] Exported ${cfg.size()} environment variables."
    return cfg
}