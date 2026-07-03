// Jenkins Console Script to find the most stable PowerVS region for IPI 4.20
// Run this in Jenkins > Manage Jenkins > Script Console
// URL: https://jenkins.ppc64le-cloud.cis.ibm.net/job/powervs/job/ipi/job/4.20/

import jenkins.model.Jenkins
import hudson.model.*

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

// ============================================================================
// CONFIGURATION - Adjust these parameters as needed
// ============================================================================

// For weekly jobs, analyze last N weeks of builds
def weeksToAnalyze = 12  // Last 12 weeks (3 months)
def daysToAnalyze = weeksToAnalyze * 7

// ============================================================================

def cutoffDate = null
if (daysToAnalyze != null) {
    cutoffDate = new Date() - daysToAnalyze
}

println "=" * 100
println "PowerVS IPI 4.20 - Weekly Jobs Stability Analysis"
println "Analyzing builds from last ${weeksToAnalyze} weeks (${daysToAnalyze} days) - since ${cutoffDate.format('yyyy-MM-dd')}"
println "=" * 100
println ""

def results = []

jobPaths.each { jobPath ->
    def job = Jenkins.instance.getItemByFullName(jobPath)
    
    if (job == null) {
        println "WARNING: Job '${jobPath}' not found!"
        println "         Trying to list available jobs in powervs/ipi/4.20/..."
        def folder = Jenkins.instance.getItemByFullName('powervs/ipi/4.20')
        if (folder != null) {
            println "         Available jobs:"
            folder.getItems().each { item ->
                println "         - ${item.name}"
            }
        }
        return
    }
    
    def jobName = jobPath.tokenize('/')[-1]  // Extract job name from path
    
    // Get builds based on time period or count
    def builds
    if (cutoffDate != null) {
        // Filter builds by date
        builds = job.getBuilds().findAll { build ->
            def buildDate = new Date(build.getTimeInMillis())
            buildDate >= cutoffDate
        }
    } else {
        // Use fixed number of builds
        builds = job.getBuilds().limit(buildsToAnalyze)
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
    
    // Extract region name from job name
    def regionKey = jobName.replaceAll('daily-ipi4.20-powervs-', '')
    def region = regionMap[regionKey] ?: regionKey
    
    results << [
        jobName: jobName,
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

// Sort by success rate (descending) and then by average duration (ascending)
results.sort { a, b ->
    if (b.successRate != a.successRate) {
        return b.successRate <=> a.successRate
    } else {
        return a.avgDuration <=> b.avgDuration
    }
}

// Print results in simple list format
println "STABILITY RANKING (Best to Worst):"
println "=" * 100
println ""

results.eachWithIndex { r, idx ->
    def rank = idx + 1
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
    
    println "${rank}. ${status} - ${r.region}"
    println "   Success Rate: ${successRateFormatted}% (${r.successfulBuilds}/${r.totalBuilds} builds passed)"
    println "   Failures: ${r.failedBuilds}, Aborted: ${r.abortedBuilds}"
    println "   Avg Duration: ${durationMin} minutes"
    if (r.oldestBuild != null) {
        println "   Period: ${r.oldestBuild.format('yyyy-MM-dd')} to ${r.newestBuild.format('yyyy-MM-dd')}"
    }
    println ""
}

println "=" * 100
println "🏆 RECOMMENDED REGIONS FOR DEPLOYMENT:"
println "=" * 100

// Top 3 regions
def top3 = results.take(3)
top3.eachWithIndex { r, idx ->
    def successRateFormatted = r.successRate.setScale(2, BigDecimal.ROUND_HALF_UP)
    println "${idx + 1}. ${r.region} - ${successRateFormatted}% success rate"
}

println ""
println "=" * 100
println "⚠️  AVOID THESE REGIONS (Success Rate < 80%):"
println "=" * 100

def problematicRegions = results.findAll { it.successRate < 80 }
if (problematicRegions.size() > 0) {
    problematicRegions.each { r ->
        def successRateFormatted = r.successRate.setScale(2, BigDecimal.ROUND_HALF_UP)
        println "- ${r.region}: ${successRateFormatted}% (${r.failedBuilds} failures in ${r.totalBuilds} builds)"
    }
} else {
    println "None - All regions performing well!"
}

println ""
println "Analysis complete!"

// Made with Bob
