def call(String buildStatus = 'STARTED', String message) {
  // Build status of null means successful.
    buildStatus = buildStatus ?: 'SUCCESS'
    // Replace encoded slashes.
    def decodedJobName = env.JOB_NAME.replaceAll("%2F", "/")

    def colorSlack

    if (POWERVS == true){
        power = ':powervs: :openshift:'
    } else if (POWERVS == false) {
        power = ':ibmpower1: :openshift:'
    } else {
        power = ''
    }


    if (buildStatus == 'STARTED') {
        colorSlack = '#D4DADF'
    } else if (buildStatus == 'SUCCESS') {
        slackEmoji = "${power} :sparkles:"
        colorSlack = '#BDFFC3'
    } else if (buildStatus == 'UNSTABLE') {
        colorSlack = '#FFFE89'
        slackEmoji = "${power} :e2e-unstable:"
    } else {
        colorSlack = '#FF9FA1'
        slackEmoji = "${power} :fire:"
    }
    def msgSlack = "${slackEmoji} ${buildStatus}: `${decodedJobName}` #${env.BUILD_NUMBER}: (<${env.BUILD_URL}|Open>) ${message}"

    slackSend(color: colorSlack, message: msgSlack)
}
