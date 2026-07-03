// Jenkins Console Script to analyze PowerVS UPI jobs with failure categorization
// Run this in Jenkins > Manage Jenkins > Script Console
// Analyzes console logs to distinguish between build issues vs infrastructure issues

import jenkins.model.Jenkins
import hudson.model.*

// ============================================================================
// CONFIGURATION
// ============================================================================

def daysToAnalyze = 90  // Last 90 days (3 months)

// ============================================================================

// Define the full job paths for PowerVS UPI (Script-based) jobs
def jobPaths = [
    'powervs/ocp/4.21/daily-ocp4.21-powervs-script-p9-min',
    'powervs/ocp/4.22/daily-ocp4.22-powervs-script-p9-min',
    'powervs/ocp/5.0/daily-ocp5.0-powervs-script-p9-min'
]

// Version and region mapping
def jobInfo = [
    'daily-ocp4.21-powervs-script-p9-min': [version: '4.21', region: 'mon01 (Montreal)'],
    'daily-ocp4.22-powervs-script-p9-min': [version: '4.22', region: 'mon01 (Montreal)'],
    'daily-ocp5.0-powervs-script-p9-min': [version: '5.0', region: 'mon01 (Montreal)']
]

// Failure pattern detection
def buildIssuePatterns = [
    ~/(?i)image.*not found/,
    ~/(?i)release.*not available/,
    ~/(?i)pull.*image.*failed/,
    ~/(?i)invalid.*release/,
    ~/(?i)mirror.*failed/,
    ~/(?i)download.*failed/,
    ~/(?i)checksum.*mismatch/,
    ~/(?i)unable to extract release/,
    ~/(?i)no such.*image/,
    ~/(?i)rhcos.*image.*error/,
    ~/(?i)bastion.*image.*not found/
]

def infraIssuePatterns = [
    ~/(?i)timeout.*waiting/,
    ~/(?i)connection.*refused/,
    ~/(?i)network.*unreachable/,
    ~/(?i)quota.*exceeded/,
    ~/(?i)insufficient.*capacity/,
    ~/(?i)resource.*not available/,
    ~/(?i)api.*rate limit/,
    ~/(?i)service.*unavailable/,
    ~/(?i)instance.*creation.*failed/,
    ~/(?i)vpc.*error/,
    ~/(?i)subnet.*error/,
    ~/(?i)load balancer.*failed/,
    ~/(?i)dns.*resolution.*failed/,
    ~/(?i)terraform.*error.*creating/,
    ~/(?i)powervs.*service.*error/,
    ~/(?i)workspace.*not available/
]

def scriptIssuePatterns = [
    ~/(?i)ansible.*failed/,
    ~/(?i)playbook.*error/,
    ~/(?i)ssh.*connection.*failed/,
    ~/(?i)bastion.*unreachable/,
    ~/(?i)script.*execution.*failed/,
    ~/(?i)terraform.*apply.*failed/
]

def cutoffDate = new Date() - daysToAnalyze

println "=" * 100
println "PowerVS UPI (Script-based) Jobs - Stability Analysis with Failure Categorization"
println "Analyzing builds from last ${daysToAnalyze} days (since ${cutoffDate.format('yyyy-MM-dd')})"
println "=" * 100
println ""

def results = []

jobPaths.each { jobPath ->
    def job = Jenkins.instance.getItemByFullName(jobPath)
    
    if (job == null) {
        println "WARNING: Job '${jobPath}' not found!"
        return
    }
    
    def jobName = jobPath.tokenize('/')[-1]
    
    // Filter builds by date
    def builds = job.getBuilds().findAll { build ->
        def buildDate = new Date(build.getTimeInMillis())
        buildDate >= cutoffDate
    }
    
    def totalBuilds = 0
    def successfulBuilds = 0
    def failedBuilds = 0
    def abortedBuilds = 0
    def buildIssueFailures = 0
    def infraIssueFailures = 0
    def scriptIssueFailures = 0
    def unknownFailures = 0
    def avgDuration = 0
    def totalDuration = 0
    def oldestBuildDate = null
    def newestBuildDate = null
    
    builds.each { build ->
        totalBuilds++
        def result = build.getResult()
        def buildDate = new Date(build.getTimeInMillis())
        
        if (oldestBuildDate == null || buildDate < oldestBuildDate) {
            oldestBuildDate = buildDate
        }
        if (newestBuildDate == null || buildDate > newestBuildDate) {
            newestBuildDate = buildDate
        }
        
        if (result == Result.SUCCESS) {
            successfulBuilds++
        } else if (result == Result.FAILURE) {
            failedBuilds++
            
            // Analyze console log for failure type
            try {
                def consoleLog = build.getLog(500).join('\n')  // Get last 500 lines
                
                def isBuildIssue = buildIssuePatterns.any { pattern ->
                    consoleLog =~ pattern
                }
                
                def isInfraIssue = infraIssuePatterns.any { pattern ->
                    consoleLog =~ pattern
                }
                
                def isScriptIssue = scriptIssuePatterns.any { pattern ->
                    consoleLog =~ pattern
                }
                
                if (isBuildIssue) {
                    buildIssueFailures++
                } else if (isInfraIssue) {
                    infraIssueFailures++
                } else if (isScriptIssue) {
                    scriptIssueFailures++
                } else {
                    unknownFailures++
                }
            } catch (Exception e) {
                unknownFailures++
            }
        } else if (result == Result.ABORTED) {
            abortedBuilds++
        }
        
        totalDuration += build.getDuration()
    }
    
    if (totalBuilds > 0) {
        avgDuration = totalDuration / totalBuilds
    }
    
    def successRate = totalBuilds > 0 ? (successfulBuilds * 100.0 / totalBuilds) : 0
    def infraFailureRate = totalBuilds > 0 ? (infraIssueFailures * 100.0 / totalBuilds) : 0
    
    def info = jobInfo[jobName]
    def version = info?.version ?: 'Unknown'
    def region = info?.region ?: 'Unknown'
    
    results << [
        jobName: jobName,
        version: version,
        region: region,
        totalBuilds: totalBuilds,
        successfulBuilds: successfulBuilds,
        failedBuilds: failedBuilds,
        abortedBuilds: abortedBuilds,
        buildIssueFailures: buildIssueFailures,
        infraIssueFailures: infraIssueFailures,
        scriptIssueFailures: scriptIssueFailures,
        unknownFailures: unknownFailures,
        successRate: successRate,
        infraFailureRate: infraFailureRate,
        avgDuration: avgDuration,
        oldestBuild: oldestBuildDate,
        newestBuild: newestBuildDate
    ]
}

// Sort by version (descending)
results.sort { a, b -> b.version <=> a.version }

// Print results
println "STABILITY ANALYSIS BY VERSION (with Failure Categorization):"
println "=" * 100
println ""

results.each { r ->
    def durationMin = (r.avgDuration / 1000 / 60).setScale(1, BigDecimal.ROUND_HALF_UP)
    def successRateFormatted = r.successRate.setScale(2, BigDecimal.ROUND_HALF_UP)
    def infraFailureRateFormatted = r.infraFailureRate.setScale(2, BigDecimal.ROUND_HALF_UP)
    
    // Status indicator
    def status = ""
    if (r.successRate >= 90) {
        status = "✅ EXCELLENT"
    } else if (r.successRate >= 80) {
        status = "✓  GOOD"
    } else if (r.successRate >= 70) {
        status = "⚠  FAIR"
    } else {
        status = "❌ POOR"
    }
    
    println "OCP ${r.version} - ${r.region}"
    println "   Status: ${status}"
    println "   Overall Success Rate: ${successRateFormatted}% (${r.successfulBuilds}/${r.totalBuilds} builds)"
    println "   Failure Breakdown:"
    println "      - Infrastructure Issues: ${r.infraIssueFailures} (${infraFailureRateFormatted}%)"
    println "      - Build/Image Issues: ${r.buildIssueFailures}"
    println "      - Script/Deployment Issues: ${r.scriptIssueFailures}"
    println "      - Unknown/Other: ${r.unknownFailures}"
    println "      - Aborted: ${r.abortedBuilds}"
    println "   Avg Duration: ${durationMin} minutes"
    if (r.oldestBuild != null) {
        println "   Period: ${r.oldestBuild.format('yyyy-MM-dd')} to ${r.newestBuild.format('yyyy-MM-dd')}"
    }
    println "   Job: ${r.jobName}"
    println ""
}

println "=" * 100
println "SUMMARY:"
println "=" * 100

// Best performing version
def bestVersion = results.max { it.successRate }
if (bestVersion) {
    def successRateFormatted = bestVersion.successRate.setScale(2, BigDecimal.ROUND_HALF_UP)
    def infraFailureRateFormatted = bestVersion.infraFailureRate.setScale(2, BigDecimal.ROUND_HALF_UP)
    println "🏆 Most Stable Version: OCP ${bestVersion.version}"
    println "   Success Rate: ${successRateFormatted}%"
    println "   Infrastructure Failure Rate: ${infraFailureRateFormatted}%"
    println ""
}

// Version with most infra issues
def mostInfraIssues = results.max { it.infraFailureRate }
if (mostInfraIssues && mostInfraIssues.infraFailureRate > 0) {
    def infraFailureRateFormatted = mostInfraIssues.infraFailureRate.setScale(2, BigDecimal.ROUND_HALF_UP)
    println "⚠️  Version with Most Infrastructure Issues: OCP ${mostInfraIssues.version}"
    println "   Infrastructure Failure Rate: ${infraFailureRateFormatted}%"
    println "   (${mostInfraIssues.infraIssueFailures} infra failures out of ${mostInfraIssues.totalBuilds} builds)"
    println ""
}

// Breakdown summary
println "FAILURE TYPE SUMMARY (All Versions):"
def totalBuildIssues = results.sum { it.buildIssueFailures }
def totalInfraIssues = results.sum { it.infraIssueFailures }
def totalScriptIssues = results.sum { it.scriptIssueFailures }
def totalUnknown = results.sum { it.unknownFailures }
def totalAllBuilds = results.sum { it.totalBuilds }

println "   - Build/Image Issues: ${totalBuildIssues}"
println "   - Infrastructure Issues: ${totalInfraIssues}"
println "   - Script/Deployment Issues: ${totalScriptIssues}"
println "   - Unknown/Other: ${totalUnknown}"
println "   - Total Builds Analyzed: ${totalAllBuilds}"

println ""
println "=" * 100
println "NOTES:"
println "- Infrastructure issues: timeouts, network errors, quota/capacity, PowerVS service errors"
println "- Build issues: image not found, release unavailable, download failures"
println "- Script issues: Ansible/Terraform failures, SSH connection problems"
println "- All UPI jobs run in Montreal (mon01) region"
println "- This analysis helps identify version stability and failure root causes"
println "=" * 100
println ""
println "Analysis complete!"

// Made with Bob
