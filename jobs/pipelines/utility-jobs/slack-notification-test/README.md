# Slack Notification Test Pipeline

A simple Jenkins pipeline to test Slack notifications with different build statuses.

## Purpose

This pipeline helps verify that:
- Slack integration is properly configured
- The `slack-token` credential is working
- Notifications are being delivered to the correct channel
- Different notification colors and formats work correctly

## Parameters

### TEST_TYPE
- **Type**: Choice
- **Options**: SUCCESS, FAILURE, UNSTABLE, ABORTED
- **Description**: Simulates different build outcomes to test corresponding notifications

### SLACK_CHANNEL
- **Type**: String
- **Default**: `#fedora-coreos-ci-tests`
- **Description**: The Slack channel where notifications will be sent

### CUSTOM_MESSAGE
- **Type**: Text
- **Default**: "This is a test message from Jenkins pipeline"
- **Description**: Custom message to include in the notification

## Usage

### Test SUCCESS Notification (Green)
```
TEST_TYPE: SUCCESS
SLACK_CHANNEL: #fedora-coreos-ci-tests
CUSTOM_MESSAGE: Testing success notification
```

### Test FAILURE Notification (Red)
```
TEST_TYPE: FAILURE
SLACK_CHANNEL: #fedora-coreos-ci-tests
CUSTOM_MESSAGE: Testing failure notification with sample error logs
```

### Test UNSTABLE Notification (Yellow)
```
TEST_TYPE: UNSTABLE
SLACK_CHANNEL: #fedora-coreos-ci-tests
CUSTOM_MESSAGE: Testing unstable notification
```

### Test ABORTED Notification (Gray)
```
TEST_TYPE: ABORTED
SLACK_CHANNEL: #fedora-coreos-ci-tests
CUSTOM_MESSAGE: Testing aborted notification
```

## Notification Format

Each notification includes:
- **Status Icon**: ✅ (success), ❌ (failure), ⚠️ (unstable), 🛑 (aborted)
- **Build Number**: Jenkins build number
- **Job Name**: Full job path
- **Build URL**: Direct link to Jenkins build
- **Test Type**: The selected test type
- **Custom Message**: Your custom message
- **Sample Error Log**: (FAILURE only) Shows how error logs appear

## Color Coding

- **SUCCESS**: Green (`good`)
- **FAILURE**: Red (`danger`)
- **UNSTABLE**: Yellow (`warning`)
- **ABORTED**: Gray (`#808080`)

## Prerequisites

1. **Slack Token Credential**: Jenkins must have a credential with ID `slack-token`
   - Go to Jenkins → Manage Jenkins → Credentials
   - Add a "Secret text" credential
   - ID: `slack-token`
   - Secret: Your Slack bot token

2. **Slack App Configuration**:
   - Create a Slack app in your workspace
   - Add the bot to the target channel
   - Grant necessary permissions (chat:write, chat:write.public)

3. **Jenkins Slack Plugin**: Ensure the Slack Notification plugin is installed

## Troubleshooting

### Notification Not Received
1. Check Jenkins console output for errors
2. Verify the `slack-token` credential exists and is valid
3. Ensure the Slack bot is added to the target channel
4. Check Slack app permissions

### Wrong Channel
- Verify the channel name starts with `#`
- Ensure the bot has access to the channel
- For private channels, explicitly invite the bot

### Authentication Errors
- Regenerate the Slack bot token
- Update the `slack-token` credential in Jenkins
- Verify the token has not expired

## Example Output

### Console Log
```
=========================================
Testing Slack Notification
Type: FAILURE
Channel: #fedora-coreos-ci-tests
=========================================
❌ Simulating FAILURE scenario
✅ FAILURE notification sent to #fedora-coreos-ci-tests
```

### Slack Message (FAILURE)
```
❌ Slack Notification Test - FAILURE

Build: #42
Job: utility-jobs/slack-notification-test
URL: https://jenkins.example.com/job/utility-jobs/job/slack-notification-test/42/
Test Type: FAILURE

Custom Message:
Testing failure notification with sample error logs

Sample Error Log (last 10 lines):
[ERROR] Test failure simulation
[ERROR] This is line 1 of sample error
...
[ERROR] Intentional failure for Slack notification testing

This is a test notification from Jenkins.
```

## Integration with Other Pipelines

Once verified, you can use the same Slack notification pattern in other pipelines:

```groovy
post {
    failure {
        script {
            def message = """
:x: *Your Pipeline Failed*
Build: #${BUILD_NUMBER}
URL: ${env.BUILD_URL}
"""
            slackSend(
                channel: '#your-channel',
                color: 'danger',
                message: message,
                tokenCredentialId: 'slack-token'
            )
        }
    }
}
```

## Related Pipelines

- **COSA Kola Test Pipeline**: Uses similar Slack notifications for test failures
- Location: `jenkins-infra/jobs/pipelines/utility-jobs/cosa-kola-test-job/`

## Notes

- This pipeline intentionally fails when TEST_TYPE is set to FAILURE
- All test types will send a notification regardless of the outcome
- The pipeline is safe to run multiple times for testing
- No actual work is performed; it only tests notifications