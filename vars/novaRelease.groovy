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