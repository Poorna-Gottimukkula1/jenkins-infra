// Jenkins Console Script to analyze PowerVS UPI (Script-based) jobs stability
// Run this in Jenkins > Manage Jenkins > Script Console
// URL: https://jenkins.ppc64le-cloud.cis.ibm.net/job/powervs/job/ocp/

import jenkins.model.Jenkins
import hudson.model.*

// ============================================================================
// CONFIGURATION - Adjust these parameters as needed
// ============================================================================

// For daily jobs, analyze last N days
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

def cutoffDate = new Date() - daysToAnalyze

println "=" * 100
println "PowerVS UPI (Script-based) Jobs Stability Analysis"
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
    
    def jobName = jobPath.tokenize('/')[-1]  // Extract job name from path
    
    // Filter builds by date
    def builds = job.getBuilds().findAll { build ->
        def buildDate = new Date(build.getTimeInMillis())
        buildDate >= cutoffDate
    }
    
    def totalBuilds = 0
    def successfulBuilds = 0
    def failedBuilds = 0
    def abortedBuilds = 0
    def unstableBuilds = 0
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
        } else if (result == Result.ABORTED) {
            abortedBuilds++
        } else if (result == Result.UNSTABLE) {
            unstableBuilds++
        }
        
        totalDuration += build.getDuration()
    }
    
    if (totalBuilds > 0) {
        avgDuration = totalDuration / totalBuilds
    }
    
    def successRate = totalBuilds > 0 ? (successfulBuilds * 100.0 / totalBuilds) : 0
    
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
        unstableBuilds: unstableBuilds,
        successRate: successRate,
        avgDuration: avgDuration,
        oldestBuild: oldestBuildDate,
        newestBuild: newestBuildDate
    ]
}

// Sort by version (descending)
results.sort { a, b -> b.version <=> a.version }

// Print results in simple list format
println "STABILITY ANALYSIS BY VERSION:"
println "=" * 100
println ""

results.each { r ->
    def durationMin = (r.avgDuration / 1000 / 60).setScale(1, BigDecimal.ROUND_HALF_UP)
    def successRateFormatted = r.successRate.setScale(2, BigDecimal.ROUND_HALF_UP)
    
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
    println "   Success Rate: ${successRateFormatted}% (${r.successfulBuilds}/${r.totalBuilds} builds passed)"
    println "   Failures: ${r.failedBuilds}, Aborted: ${r.abortedBuilds}"
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
    println "🏆 Most Stable Version: OCP ${bestVersion.version}"
    println "   Success Rate: ${bestVersion.successRate.setScale(2, BigDecimal.ROUND_HALF_UP)}%"
    println ""
}

// Versions with issues
def problematicVersions = results.findAll { it.successRate < 80 }
if (problematicVersions.size() > 0) {
    println "⚠️  Versions with Success Rate < 80%:"
    problematicVersions.each { r ->
        def successRateFormatted = r.successRate.setScale(2, BigDecimal.ROUND_HALF_UP)
        println "   - OCP ${r.version}: ${successRateFormatted}% (${r.failedBuilds} failures in ${r.totalBuilds} builds)"
    }
} else {
    println "✅ All versions performing well (≥80% success rate)"
}

println ""
println "=" * 100
println "NOTE: These are UPI (User Provisioned Infrastructure) deployments using scripts."
println "      All jobs run in Montreal (mon01) region."
println "      Failures may include upstream build issues, not just deployment problems."
println "=" * 100
println ""
println "Analysis complete!"

// Made with Bob
