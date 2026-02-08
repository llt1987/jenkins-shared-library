def getUserId() {
    if(currentBuild.upstreamBuilds){
        return currentBuild.upstreamBuilds[0].getBuildCauses()[0].userId
    } else {
        return currentBuild.getBuildCauses()[0].userId
    }
}