def call(String buildStatus = 'STARTED', String message) {
  // Build status of null means successful.
    buildStatus = buildStatus ?: 'SUCCESS'
    // Replace encoded slashes.
    def decodedJobName = env.JOB_NAME.replaceAll("%2F", "/")

    def colorSlack
    def slackEmoji = ''
    def power = ''
    def build_status = ''

    if (env.POWERVS == "true"){
        power = ':powervs: :openshift:'
    } else if (env.POWERVS == "false") {
        power = ':ibmpower2: :openshift:'
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
        // Distinguish between aborted and failed
        if (currentBuild.result == 'ABORTED') {
            build_status = ':aborted:'
        } else {
            build_status = ':fire:'
        }
        colorSlack = '#FF9FA1'
        slackEmoji = "${power} ${build_status}"

    }
    def msgSlack = "${slackEmoji} ${buildStatus}: `${decodedJobName}` #${env.BUILD_NUMBER}: (<${env.BUILD_URL}|Open>) ${message}"

    slackSend(color: colorSlack, message: msgSlack)
}
