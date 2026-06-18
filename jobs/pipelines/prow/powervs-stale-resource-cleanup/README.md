# PowerVS Stale Resource Cleanup Job

## Overview
This Jenkins job runs daily to discover and report stale PowerVS resources across all regions. It helps maintain clean infrastructure by identifying resources that may need cleanup.

## Schedule
- **Frequency**: Daily
- **Time**: 11:00 AM IST (5:30 AM UTC)
- **Cron**: `30 5 * * *`

## Required Credentials

The following credentials must be configured in Jenkins as **Secret Text** credentials:

1. **`ibmcloud-api-key`**
   - IBM Cloud API Key with permissions to query PowerVS resources
   - Used by: `query_powervs_stale_resource.sh`

2. **`slack-webhook-url`**
   - Slack Webhook URL for sending notifications
   - Used by: `send_stale_resources_to_slack.sh`

3. **`cis-instance`**
   - Cloud Internet Services (CIS) instance name
   - Used for DNS record queries

4. **`cis-domain-id`**
   - CIS Domain ID for DNS operations
   - Used for DNS record queries

5. **`github-enterprise-token`** (if using private repo)
   - GitHub Enterprise token for accessing the script repository
   - Used for: Checking out scripts from https://github.ibm.com/redstack-power/rh-ocp-ci-monitor

## Script Repository

- **Repository**: https://github.ibm.com/redstack-power/rh-ocp-ci-monitor
- **Branch**: main
- **PR**: https://github.ibm.com/redstack-power/rh-ocp-ci-monitor/pull/24

## Scripts Executed

1. **`query_powervs_stale_resource.sh`**
   - Discovers stale PowerVS resources across all regions
   - Outputs detailed log to `stale_cleanup.log`
   - Checks: VMs, volumes, networks, DNS records, etc.

2. **`send_stale_resources_to_slack.sh`**
   - Parses the log file from step 1
   - Formats and sends a summary report to Slack
   - Includes resource counts and details

## Artifacts

- **`stale_cleanup.log`**: Detailed execution log archived with each build
  - Contains full output from resource discovery
  - Available for download from Jenkins UI
  - Retained for 30 builds

## Prerequisites

The Jenkins agent must have:
- `bash` (shell)
- `curl` (for API calls)
- `jq` (for JSON parsing)

## Job Configuration

### Build Triggers
- Scheduled via cron: Daily at 11:00 AM IST

### Build Options
- Timestamps enabled
- Timeout: 2 hours
- Build retention: 30 builds

### Post-Build Actions
- Archive `stale_cleanup.log` as artifact
- Slack notification on completion

## Monitoring

Check the following for job health:
1. Jenkins build history
2. Archived `stale_cleanup.log` files
3. Slack channel for daily reports
4. Build trends for failures

## Troubleshooting

### Common Issues

1. **Authentication Failures**
   - Verify `ibmcloud-api-key` credential is valid
   - Check API key has required permissions

2. **Slack Notification Not Sent**
   - Verify `slack-webhook-url` is correct
   - Check Slack workspace permissions

3. **Script Not Found**
   - Verify GitHub Enterprise token has access to repository
   - Check repository URL and branch name

4. **Missing Tools**
   - Ensure Jenkins agent has `bash`, `curl`, and `jq` installed

### Debug Steps

1. Check the archived `stale_cleanup.log` for detailed error messages
2. Review Jenkins console output for script execution errors
3. Verify all credentials are properly configured
4. Test scripts manually on Jenkins agent if needed

## Maintenance

### Updating Scripts
Scripts are pulled from the repository on each run, so updates to the repository will automatically be used in the next scheduled run.

### Modifying Schedule
Edit the `cron` trigger in the Jenkinsfile:
```groovy
triggers {
    cron('30 5 * * *')  // 11:00 AM IST
}
```

### Changing Retention
Modify the `buildDiscarder` in the Jenkinsfile:
```groovy
buildDiscarder(logRotator(numToKeep: 30, artifactNumToKeep: 30))
```

## Contact

For issues or questions:
- Check PR: https://github.ibm.com/redstack-power/rh-ocp-ci-monitor/pull/24
- Review script documentation in the repository