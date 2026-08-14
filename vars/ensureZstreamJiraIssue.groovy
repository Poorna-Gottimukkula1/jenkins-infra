def call(Map config = [:]) {
    if (params.JIRA_ISSUE_KEY?.trim()) {
        env.JIRA_ISSUE_KEY = params.JIRA_ISSUE_KEY.trim()
        return env.JIRA_ISSUE_KEY
    }

    withCredentials([
        string(credentialsId: 'JIRA_API_TOKEN', variable: 'JIRA_API_TOKEN'),
        string(credentialsId: 'JIRA_BASE_URL', variable: 'JIRA_BASE_URL'),
        string(credentialsId: 'JIRA_USER_EMAIL', variable: 'JIRA_USER_EMAIL')
    ]) {
        def jiraLabels = config.labels
        def jiraSummary = config.summary
        def jiraDescription = config.description
        def jiraJql = config.jql

        writeFile file: 'deploy/jira-search.json', text: groovy.json.JsonOutput.toJson([
            jql: jiraJql,
            maxResults: 1,
            fields: ['key']
        ])

        sh '''
            set -e
            curl -sS \
                -u "${JIRA_USER_EMAIL}:${JIRA_API_TOKEN}" \
                -H "Content-Type: application/json" \
                --data @deploy/jira-search.json \
                "${JIRA_BASE_URL}/rest/api/2/search" > deploy/jira-search-result.json
        '''

        def jiraSearchResult = readJSON file: 'deploy/jira-search-result.json'
        if (jiraSearchResult.issues && jiraSearchResult.issues.size() > 0) {
            env.JIRA_ISSUE_KEY = jiraSearchResult.issues[0].key
        } else {
            writeFile file: 'deploy/jira-create.json', text: groovy.json.JsonOutput.toJson([
                fields: [
                    project: [key: 'OPENSHIFTP'],
                    issuetype: [name: 'Task'],
                    summary: jiraSummary,
                    description: jiraDescription,
                    labels: jiraLabels
                ]
            ])

            sh '''
                set -e
                curl -sS \
                    -u "${JIRA_USER_EMAIL}:${JIRA_API_TOKEN}" \
                    -H "Content-Type: application/json" \
                    --data @deploy/jira-create.json \
                    "${JIRA_BASE_URL}/rest/api/2/issue" > deploy/jira-create-result.json
            '''

            def jiraCreateResult = readJSON file: 'deploy/jira-create-result.json'
            env.JIRA_ISSUE_KEY = jiraCreateResult.key
        }
    }

    echo "Using Jira issue: ${env.JIRA_ISSUE_KEY}"
    env.JIRA_ISSUE_KEY
}
