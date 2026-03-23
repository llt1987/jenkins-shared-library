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

    }
}

def build() {

    stage('Build') {
        echo 'Set build id...'


        echo 'Build starting...'


    }
}

def validate() {

    stage('Validate') {
        script {
            FAILED_STAGE = env.STAGE_NAME


                echo "Validating artifact at:"


            }
        }
    }


def validateSIT() {

    stage('Validate') {
        script {
            FAILED_STAGE = env.STAGE_NAME


                def buildType = env.BUILDTYPE?.toUpperCase()

                echo "✅ ${buildType} build successful for ${TAG}"
            
        }
    }
}

return this