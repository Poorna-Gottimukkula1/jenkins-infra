def call(Map config = [:]) {
    if (!env.JIRA_ISSUE_KEY?.trim()) {
        return
    }

    withCredentials([
        string(credentialsId: 'JIRA_API_TOKEN', variable: 'JIRA_API_TOKEN'),
        string(credentialsId: 'JIRA_BASE_URL', variable: 'JIRA_BASE_URL'),
        string(credentialsId: 'JIRA_USER_EMAIL', variable: 'JIRA_USER_EMAIL')
    ]) {
        def jiraComment = config.comment
        writeFile file: 'deploy/jira-comment.json', text: groovy.json.JsonOutput.toJson([body: jiraComment])

        sh '''
            set -e
            curl -sS -X POST \
                -u "${JIRA_USER_EMAIL}:${JIRA_API_TOKEN}" \
                -H "Content-Type: application/json" \
                --data @deploy/jira-comment.json \
                "${JIRA_BASE_URL}/rest/api/2/issue/${JIRA_ISSUE_KEY}/comment"
        '''

        if (config.clusterHealthPath) {
            sh """
                set -e
                if [ -f \"${config.clusterHealthPath}\" ]; then
                    curl -sS -X POST \\
                        -u \"${JIRA_USER_EMAIL}:${JIRA_API_TOKEN}\" \\
                        -H \"X-Atlassian-Token: no-check\" \\
                        -F \"file=@${config.clusterHealthPath}\" \\
                        \"${JIRA_BASE_URL}/rest/api/2/issue/${JIRA_ISSUE_KEY}/attachments\"
                fi
            """
        }

        if (config.screenshotGlob) {
            sh """
                set -e
                find ${config.screenshotGlob} -type f -name \"*.png\" | while read file; do
                    curl -sS -X POST \\
                        -u \"${JIRA_USER_EMAIL}:${JIRA_API_TOKEN}\" \\
                        -H \"X-Atlassian-Token: no-check\" \\
                        -F \"file=@${file}\" \\
                        \"${JIRA_BASE_URL}/rest/api/2/issue/${JIRA_ISSUE_KEY}/attachments\"
                done
            """
        }
    }
}
