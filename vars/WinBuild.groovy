def checkout() {

    stage('Checkout source') {
        cleanWs()

        script {
            FAILED_STAGE = env.STAGE_NAME
        }

        echo 'Checkout starting...'
        echo "JOB_NAME :: ${env.JOB_NAME}"
        echo "JOB_BASE_NAME :: ${env.JOB_BASE_NAME}"

        // Create top-level directory before checkout

            echo  "buildallgit.bat ${env.BUILDTYPE} 700 CHECKOUT"
            echo 'Checkout finished...'
    }
}

def build() {

    stage('Build') {
        echo 'Set build id...'


        echo 'Build starting...'

            echo  "buildallgit.bat ${env.BUILDTYPE} 700 BUILD"

    }
}

def validate() {

    stage('Validate') {
        script {
            FAILED_STAGE = env.STAGE_NAME


                def archiveBase = '\\\\shared.novacmx.local\\Novabld\\novaarchive\\700-BUILDS'
                def buildType = env.BUILDTYPE?.toUpperCase()
                def subDir = ''
                if (buildType == 'INT64') {
                    subDir = '\\64bit'
                } else if (buildType != 'INT') {
                    error("Unsupported BUILDTYPE: ${buildType}")
                }

                def zipPath = "${archiveBase}\\${TAG}${subDir}\\archive\\novaxtools.zip"

                echo "Validating artifact at: ${zipPath}"


            }
        }
    }


def validateSIT() {

    stage('ValidateSIT') {
        script {
            FAILED_STAGE = env.STAGE_NAME


                def buildType = env.BUILDTYPE?.toUpperCase()
                def versionFile = ''

                /*
                 * Select version file based on BUILDTYPE
                 * (same pattern as INT / INT64 subDir logic)
                 */
                if (buildType == 'SIT64') {

                    versionFile = '\\\\shared.novacmx.local\\Novabld\\novaarchive\\SIT\\NOVA-700-MAINT-64\\tag.txt'

                } else if (buildType == 'SIT2019') {

                    versionFile = '\\\\shared.novacmx.local\\Novabld\\novaarchive\\SIT\\NOVA-700-MAINT-VS2019\\tag.txt'

                } else if (buildType == 'SIT') {

                    versionFile = '\\\\shared.novacmx.local\\Novabld\\novaarchive\\SIT\\NOVA-700-MAINT\\tag.txt'

                } else {

                    error("Unsupported BUILDTYPE for validation: ${buildType}")
                }

                echo "Validating version file: ${versionFile}"
                echo "✅ ${buildType} build successful for NOVA"
            
        }
    }
}

def cleanup() {
    echo 'Cleaning workspace'
    cleanWs()
}

return this