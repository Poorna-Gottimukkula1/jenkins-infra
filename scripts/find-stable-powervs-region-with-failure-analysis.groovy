// Jenkins Console Script to analyze PowerVS IPI 4.20 with failure categorization
// Run this in Jenkins > Manage Jenkins > Script Console
// Analyzes console logs to distinguish between build issues vs infrastructure issues

import jenkins.model.Jenkins
import hudson.model.*

// ============================================================================
// CONFIGURATION
// ============================================================================

def weeksToAnalyze = 12  // Last 12 weeks (3 months)
def daysToAnalyze = weeksToAnalyze * 7

// ============================================================================

// Define the full job paths for PowerVS IPI 4.20
def jobPaths = [
    'powervs/ipi/4.20/daily-ipi4.20-powervs-frankfurt1',
    'powervs/ipi/4.20/daily-ipi4.20-powervs-frankfurt2',
    'powervs/ipi/4.20/daily-ipi4.20-powervs-london06',
    'powervs/ipi/4.20/daily-ipi4.20-powervs-madrid02',
    'powervs/ipi/4.20/daily-ipi4.20-powervs-madrid04',
    'powervs/ipi/4.20/daily-ipi4.20-powervs-osaka21',
    'powervs/ipi/4.20/daily-ipi4.20-powervs-saopaulo01',
    'powervs/ipi/4.20/daily-ipi4.20-powervs-saopaulo04',
    'powervs/ipi/4.20/daily-ipi4.20-powervs-sydney04',
    'powervs/ipi/4.20/daily-ipi4.20-powervs-washingtondc06',
    'powervs/ipi/4.20/daily-ipi4.20-powervs-washingtondc07'
]

// Region mapping
def regionMap = [
    'frankfurt1': 'eu-de-1',
    'frankfurt2': 'eu-de-2',
    'london06': 'lon06',
    'madrid02': 'mad02',
    'madrid04': 'mad04',
    'osaka21': 'osa21',
    'saopaulo01': 'sao01',
    'saopaulo04': 'sao04',
    'sydney04': 'syd04',
    'washingtondc06': 'wdc06',
    'washingtondc07': 'wdc07'
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
    ~/(?i)no such.*image/
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
    ~/(?i)dns.*resolution.*failed/
]

def cutoffDate = new Date() - daysToAnalyze

println "=" * 100
println "PowerVS IPI 4.20 - Region Stability Analysis with Failure Categorization"
println "Analyzing builds from last ${weeksToAnalyze} weeks (${daysToAnalyze} days) - since ${cutoffDate.format('yyyy-MM-dd')}"
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
                
                if (isBuildIssue) {
                    buildIssueFailures++
                } else if (isInfraIssue) {
                    infraIssueFailures++
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
    
    def regionKey = jobName.replaceAll('daily-ipi4.20-powervs-', '')
    def region = regionMap[regionKey] ?: regionKey
    
    results << [
        jobName: jobName,
        region: region,
        totalBuilds: totalBuilds,
        successfulBuilds: successfulBuilds,
        failedBuilds: failedBuilds,
        abortedBuilds: abortedBuilds,
        buildIssueFailures: buildIssueFailures,
        infraIssueFailures: infraIssueFailures,
        unknownFailures: unknownFailures,
        successRate: successRate,
        infraFailureRate: infraFailureRate,
        avgDuration: avgDuration,
        oldestBuild: oldestBuildDate,
        newestBuild: newestBuildDate
    ]
}

// Sort by infrastructure failure rate (ascending) then success rate (descending)
results.sort { a, b ->
    if (a.infraFailureRate != b.infraFailureRate) {
        return a.infraFailureRate <=> b.infraFailureRate
    } else {
        return b.successRate <=> a.successRate
    }
}

// Print results
println "STABILITY RANKING (Best Infrastructure Reliability):"
println "=" * 100
println ""

results.eachWithIndex { r, idx ->
    def rank = idx + 1
    def durationMin = (r.avgDuration / 1000 / 60).setScale(1, BigDecimal.ROUND_HALF_UP)
    def successRateFormatted = r.successRate.setScale(2, BigDecimal.ROUND_HALF_UP)
    def infraFailureRateFormatted = r.infraFailureRate.setScale(2, BigDecimal.ROUND_HALF_UP)
    
    // Status indicator based on infra issues
    def status = ""
    if (r.infraFailureRate == 0) {
        status = "✅ EXCELLENT"
    } else if (r.infraFailureRate < 5) {
        status = "✓  GOOD"
    } else if (r.infraFailureRate < 10) {
        status = "⚠  FAIR"
    } else {
        status = "❌ POOR"
    }
    
    println "${rank}. ${status} - ${r.region}"
    println "   Overall Success Rate: ${successRateFormatted}% (${r.successfulBuilds}/${r.totalBuilds} builds)"
    println "   Failure Breakdown:"
    println "      - Infrastructure Issues: ${r.infraIssueFailures} (${infraFailureRateFormatted}%)"
    println "      - Build/Image Issues: ${r.buildIssueFailures}"
    println "      - Unknown/Other: ${r.unknownFailures}"
    println "      - Aborted: ${r.abortedBuilds}"
    println "   Avg Duration: ${durationMin} minutes"
    if (r.oldestBuild != null) {
        println "   Period: ${r.oldestBuild.format('yyyy-MM-dd')} to ${r.newestBuild.format('yyyy-MM-dd')}"
    }
    println ""
}

println "=" * 100
println "🏆 RECOMMENDED REGIONS (Lowest Infrastructure Failure Rate):"
println "=" * 100

def top3 = results.take(3)
top3.eachWithIndex { r, idx ->
    def successRateFormatted = r.successRate.setScale(2, BigDecimal.ROUND_HALF_UP)
    def infraFailureRateFormatted = r.infraFailureRate.setScale(2, BigDecimal.ROUND_HALF_UP)
    println "${idx + 1}. ${r.region}"
    println "   - Success: ${successRateFormatted}%, Infra Issues: ${infraFailureRateFormatted}%"
}

println ""
println "=" * 100
println "⚠️  REGIONS WITH HIGH INFRASTRUCTURE FAILURE RATE (>5%):"
println "=" * 100

def problematicRegions = results.findAll { it.infraFailureRate > 5 }
if (problematicRegions.size() > 0) {
    problematicRegions.each { r ->
        def infraFailureRateFormatted = r.infraFailureRate.setScale(2, BigDecimal.ROUND_HALF_UP)
        println "- ${r.region}: ${infraFailureRateFormatted}% infra failures (${r.infraIssueFailures}/${r.totalBuilds} builds)"
    }
} else {
    println "None - All regions have <5% infrastructure failure rate!"
}

println ""
println "=" * 100
println "NOTES:"
println "- Infrastructure issues: timeouts, network errors, quota/capacity problems"
println "- Build issues: image not found, release unavailable, download failures"
println "- This analysis helps identify true regional stability vs upstream build problems"
println "=" * 100
println ""
println "Analysis complete!"

// Made with Bob
