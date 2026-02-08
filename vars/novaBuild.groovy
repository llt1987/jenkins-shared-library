def buildServer() {
    echo "Building Java server project"
    sh "java --version"
    sh "mvn --version"
    dir("${env.WORKSPACE}/novaserver") {
        sh "mvn -f maven/standards/pom.xml install"
        sh "mvn -f maven/spring-boot-ebean-starter/pom.xml install"
        sh "mvn -f maven/nova/pom.xml install"
    }
    sh "java --version"
    sh "mvn --version"
    sh "mvn clean install --batch-mode -DskipTests -f novaserver/pom.xml"
    echo 'Java Server Build Successfully'
}

def buildGUI() {
    echo "Building Angular GUI"
    sh "npm -v; node -v; ng version"
    sh "export NODE_OPTIONS=--max-old-space-size=8192; npm install; npm run build"
    sh "tar -cvzf angulardist.tar.gz -C ${env.WORKSPACE}/dist ."
    if (env.NOVA_SIT_BUILD_DIR) {
        echo "Copying Angular dist to ${env.NOVA_SIT_BUILD_DIR}"
        sh "cp angulardist.tar.gz ${env.NOVA_SIT_BUILD_DIR}"
    } else {
        echo "Warning: NOVA_SIT_BUILD_DIR not set, skipping copy"
    }
    echo "Angular GUI Build Completed"
}

def buildNXSRPC(Map params = [:]) {
    echo "Building NXSRPC Docker image"
    def branch = params.BRANCH ?: env.BRANCH_NAME
    def novaversion = params.NOVAVERSION ?: env.NOVAVERSION
    def packageLocation = params.PACKAGE_LOCATION ?: "novabld"

    def novaBuildDir = "/mnt/novabld/novaarchive/${novaversion}-BUILDS"
    def linuxNexusFilename = "Linux-Nexus-sg-nov-ol08-04_19c.tar.gz"
    if (packageLocation.toLowerCase() == "novarel") {
        novaBuildDir = "/mnt/${packageLocation}/${novaversion}-BUILDS-RELEASED"
        linuxNexusFilename = "Linux-Nexus-sg-nov-rhel8-03_19c.tar.gz"
    }

    sh "docker images -q -f dangling=true | xargs --no-run-if-empty docker rmi"
    sh "docker volume ls -qf dangling=true | xargs -r docker volume rm"

    dir("${env.WORKSPACE}/build/novastp-19c-ol8") {
        sh "cp ${novaBuildDir}/${branch}/64bit/${linuxNexusFilename} ./Linux-Nexus-sg-nov-ol08-04_19c.tar.gz"
        sh "docker build -t harbor.novacmx.com/posttrade/novastp-19c-ol8:${branch} -t harbor.novacmx.com/posttrade/novastp-19c-ol8 -f Dockerfile ."
    }
    echo "NXSRPC Docker image build completed"
}

def buildPython() {
    echo "Building Python project"
    sh "python3 --version || python --version"

    dir("${env.WORKSPACE}/python") {
        if (fileExists('requirements.txt')) {
            echo "Installing Python dependencies"
            sh "pip3 install -r requirements.txt || pip install -r requirements.txt"
        } else {
            echo "No requirements.txt found, skipping dependency installation"
        }

        if (fileExists('tests') || fileExists('test')) {
            echo "Running Python tests"
            sh "pytest || python -m unittest discover"
        } else {
            echo "No test directory found, skipping tests"
        }

        if (fileExists('setup.py')) {
            echo "Building Python package"
            sh "python3 setup.py sdist bdist_wheel || python setup.py sdist bdist_wheel"
        } else {
            echo "No setup.py found, skipping package build"
        }
    }
    echo 'Python build completed'
}
