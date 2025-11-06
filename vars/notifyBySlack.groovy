def call(String buildStatus = 'STARTED', String message) {
  // Build status of null means successful.
    buildStatus = buildStatus ?: 'SUCCESS'
    // Replace encoded slashes.
    def decodedJobName = env.JOB_NAME.replaceAll("%2F", "/")

    def colorSlack

    if (buildStatus == 'STARTED') {
        colorSlack = '#D4DADF'
    } else if (buildStatus == 'SUCCESS') {
        slackEmoji = ':ibmpower1: :openshift: :sparkles:'
        colorSlack = '#BDFFC3'
    } else if (buildStatus == 'UNSTABLE') {
        colorSlack = '#FFFE89'
        slackEmoji = ':ibmpower1: :openshift: :e2e-unstable:'
    } else {
        colorSlack = '#FF9FA1'
        slackEmoji = ':ibmpower1: :openshift: :fire:'
    }

    def msgSlack = "${slackEmoji} ${buildStatus}: `${decodedJobName}` #${env.BUILD_NUMBER}: (<${env.BUILD_URL}|Open>) ${message}"

    slackSend(color: colorSlack, message: msgSlack)
}
