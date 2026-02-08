def failure(Map args = [:]){
    def buildUrl = args.get('buildUrl', "${BUILD_URL}")
    def failedStage = args.get('failedStage', "null")
    def branch = args.get('branch', "null")

    office365ConnectorSend webhookUrl: "https://contemiukltd365.webhook.office.com/webhookb2/7a70a1e9-4e23-4b92-b9e4-9c344169925a@0745e4e9-2462-4b7a-97e2-4051a4ce1aa0/JenkinsCI/b0c35d70c9b84cae8ae485f8e026f3c4/f8331921-e886-4bb9-b7ea-926349d82761/V2eFOyfoq0TbWX0TVw26IMC12FrBXvZJdPtPMyRquTGFM1",
                factDefinitions: [[name: "Result", template: "<!DOCTYPE html> <html> <body> <h1 style='background-color:Red;'><b>Fail - ${failedStage}</b></h1> </body> </html>"],[name: "Branch: ", template: "${branch}"],
                                  [name: "Info: ", template: "$buildUrl"]]
}

def success(Map args = [:]){
    def buildUrl = args.get('buildUrl', "${BUILD_URL}")
    def branch = args.get('branch', "null")

    office365ConnectorSend webhookUrl: "https://contemiukltd365.webhook.office.com/webhookb2/7a70a1e9-4e23-4b92-b9e4-9c344169925a@0745e4e9-2462-4b7a-97e2-4051a4ce1aa0/JenkinsCI/b0c35d70c9b84cae8ae485f8e026f3c4/f8331921-e886-4bb9-b7ea-926349d82761/V2eFOyfoq0TbWX0TVw26IMC12FrBXvZJdPtPMyRquTGFM1",
                factDefinitions: [[name: "Result", template: "<!DOCTYPE html> <html> <body> <h1 style='background-color:MediumSeaGreen;'><b>Success</b></h1> </body> </html>"],[name: "Branch: ", template: "${branch}"],
                                  [name: "Info: ", template: "$buildUrl"]]
}

def unstable(Map args = [:]){
    def buildUrl = args.get('buildUrl', "${BUILD_URL}")
    def failedStage = args.get('failedStage', "null")
    def branch = args.get('branch', "null")

    office365ConnectorSend webhookUrl: "https://contemiukltd365.webhook.office.com/webhookb2/7a70a1e9-4e23-4b92-b9e4-9c344169925a@0745e4e9-2462-4b7a-97e2-4051a4ce1aa0/JenkinsCI/b0c35d70c9b84cae8ae485f8e026f3c4/f8331921-e886-4bb9-b7ea-926349d82761/V2eFOyfoq0TbWX0TVw26IMC12FrBXvZJdPtPMyRquTGFM1",
                factDefinitions: [[name: "Result", template: "<!DOCTYPE html> <html> <body> <h1 style='background-color:LightGray;'><b>Unstable - ${failedStage}</b></h1> </body> </html>"],[name: "Branch: ", template: "${branch}"],
                                  [name: "Info: ", template: "$buildUrl"]]
}