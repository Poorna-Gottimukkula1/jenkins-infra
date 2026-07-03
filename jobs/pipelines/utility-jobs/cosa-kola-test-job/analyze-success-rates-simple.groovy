// Jenkins Script Console - Build Success Rate Analysis
// No imports needed - uses Jenkins built-in classes

// Configuration
def jobName = "utility-jobs/coreos"
def maxBuilds = 100

def job = Jenkins.instance.getItemByFullName(jobName)
if (!job) {
    println "Job '${jobName}' not found!"
    return
}

// Data structures
def stats = [:]  // [memory][testType] = detailed stats

println "=" * 100
println "BUILD SUCCESS RATE ANALYSIS - ${jobName}"
println "Analyzing last ${maxBuilds} builds..."
println "=" * 100

def builds = job.getBuilds().limit(maxBuilds)
builds.each { build ->
    def buildNumber = build.number
    def result = build.result?.toString() ?: "RUNNING"
    
    // Get parameters
    def params = build.getAction(hudson.model.ParametersAction)
    if (!params) return
    
    def memory = params.getParameter("QEMU_MEMORY")?.value ?: "unknown"
    def runKola = params.getParameter("RUN_KOLA_TESTS")?.value ?: false
    def runUpgrade = params.getParameter("RUN_UPGRADE_TESTS")?.value ?: false
    def runExtUpgrade = params.getParameter("RUN_EXTENDED_UPGRADE_TESTS")?.value ?: false
    def kolaTest = params.getParameter("KOLA_TEST")?.value ?: "unknown"
    
    // Determine test type
    def testTypes = []
    if (runKola) testTypes.add("kola:${kolaTest}")
    if (runUpgrade) testTypes.add("upgrade")
    if (runExtUpgrade) testTypes.add("ext-upgrade")
    if (testTypes.isEmpty()) testTypes.add("none")
    
    // Get console log to check for actual test failures
    def consoleLog = ""
    try {
        consoleLog = build.getLog(5000).join("\n")  // Get last 5000 lines
    } catch (Exception e) {
        println "Warning: Could not read log for build #${buildNumber}"
    }
    
    // Record stats
    testTypes.each { testType ->
        def key = "${memory}MB"
        if (!stats[key]) stats[key] = [:]
        if (!stats[key][testType]) {
            stats[key][testType] = [
                pipelineSuccess: 0,
                pipelineFailure: 0,
                pipelineAborted: 0,
                testRan: 0,
                testPassed: 0,
                testFailed: 0,
                testNotRun: 0,
                totalBuilds: 0,
                failedBuilds: [],
                passedBuilds: [],
                notRunBuilds: [],
                flakyBuilds: []  // Failed first run, passed on re-run
            ]
        }
        
        def data = stats[key][testType]
        data.totalBuilds++
        
        // Determine if test ran and its result based on console log
        def testRanPattern = ""
        def testFailurePatterns = []
        
        if (testType.startsWith("kola:")) {
            testRanPattern = "Running Kola Tests"
            testFailurePatterns = [
                "FAIL:",
                "--- FAIL:",
                "test suite failed",
                "kernel panic",
                "kernel oops"
            ]
        } else if (testType == "upgrade") {
            testRanPattern = "Running Upgrade Tests"
            testFailurePatterns = [
                "FAIL: fcos.upgrade",
                "--- FAIL: fcos.upgrade",
                "failed waiting for machine reboot",
                "time limit exceeded",
                "kernel panic",
                "kernel oops",
                "test suite failed"
            ]
        } else if (testType == "ext-upgrade") {
            testRanPattern = "Running Extended Upgrade Tests"
            testFailurePatterns = [
                "FAIL:",
                "--- FAIL:",
                "test suite failed",
                "kernel panic"
            ]
        }
        
        // Check if test ran
        def testRan = consoleLog.contains(testRanPattern)
        
        // Check for re-run (flake detection)
        def hasRerun = consoleLog.contains("Re-running failed tests (flake detection)")
        def rerunPassed = hasRerun && !consoleLog.contains("FAIL, output in tmp/kola-upgrade/rerun")
        
        // Analyze results
        if (result == "SUCCESS") {
            data.pipelineSuccess++
            if (testRan) {
                data.testRan++
                data.testPassed++
                
                // Check if this was a flaky success (failed first, passed on rerun)
                if (hasRerun && rerunPassed) {
                    data.flakyBuilds.add(buildNumber)
                } else {
                    data.passedBuilds.add(buildNumber)
                }
            } else {
                data.testNotRun++
                data.notRunBuilds.add(buildNumber)
            }
        } else if (result == "FAILURE") {
            data.pipelineFailure++
            
            if (testRan) {
                data.testRan++
                
                // Check if test actually failed
                def testFailed = testFailurePatterns.any { pattern ->
                    consoleLog.contains(pattern)
                }
                
                if (testFailed) {
                    data.testFailed++
                    data.failedBuilds.add(buildNumber)
                } else {
                    // Test ran but didn't fail - pipeline failed elsewhere
                    data.testPassed++
                    data.passedBuilds.add(buildNumber)
                }
            } else {
                // Test didn't run
                data.testNotRun++
                data.notRunBuilds.add(buildNumber)
            }
        } else if (result == "ABORTED") {
            data.pipelineAborted++
            data.testNotRun++
            data.notRunBuilds.add(buildNumber)
        }
    }
}

// Print detailed results
println "\n" + "=" * 100
println "DETAILED SUCCESS RATE ANALYSIS"
println "=" * 100

stats.sort().each { memory, testData ->
    println "\n📊 Memory Configuration: ${memory}"
    println "-" * 100
    
    testData.sort().each { testType, data ->
        // Calculate rates
        def pipelineRate = data.totalBuilds > 0 ?
            String.format("%.1f%%", (data.pipelineSuccess / data.totalBuilds) * 100) : "N/A"
        
        def testSuccessRate = data.testRan > 0 ?
            String.format("%.1f%%", (data.testPassed / data.testRan) * 100) : "N/A"
        
        def testRunRate = data.totalBuilds > 0 ?
            String.format("%.1f%%", (data.testRan / data.totalBuilds) * 100) : "N/A"
        
        println "\n  🔹 Test: ${testType}"
        println "  " + "-" * 96
        println sprintf("    Total Builds:        %3d", data.totalBuilds)
        println sprintf("    Pipeline Success:    %3d (%6s) - Entire pipeline succeeded", 
            data.pipelineSuccess, pipelineRate)
        println sprintf("    Pipeline Failure:    %3d - Pipeline failed somewhere",
            data.pipelineFailure)
        println sprintf("    Pipeline Aborted:    %3d - Build was aborted",
            data.pipelineAborted)
        println ""
        println sprintf("    Test Ran:            %3d (%6s) - Test stage executed",
            data.testRan, testRunRate)
        println sprintf("    Test Passed:         %3d (%6s) ✅ TRUE SUCCESS RATE",
            data.testPassed, testSuccessRate)
        println sprintf("      - Clean Pass:      %3d - Passed on first run",
            data.passedBuilds.size())
        println sprintf("      - Flaky Pass:      %3d - Failed first, passed on re-run",
            data.flakyBuilds.size())
        println sprintf("    Test Failed:         %3d - Test stage failed (even after re-run)",
            data.testFailed)
        println sprintf("    Test Not Run:        %3d - Test didn't execute",
            data.testNotRun)
        
        // Show build numbers for failed tests
        if (data.failedBuilds && !data.failedBuilds.isEmpty()) {
            println "\n    ❌ Failed Test Builds: ${data.failedBuilds.sort().reverse().take(20).join(', ')}"
            if (data.failedBuilds.size() > 20) {
                println "       (showing latest 20 of ${data.failedBuilds.size()} failures)"
            }
        }
        
        // Show build numbers for flaky tests (passed on re-run)
        if (data.flakyBuilds && !data.flakyBuilds.isEmpty()) {
            println "    🔄 Flaky Builds (passed on re-run): ${data.flakyBuilds.sort().reverse().take(15).join(', ')}"
            if (data.flakyBuilds.size() > 15) {
                println "       (showing latest 15 of ${data.flakyBuilds.size()} flaky builds)"
            }
        }
        
        // Show build numbers for tests that didn't run
        if (data.notRunBuilds && !data.notRunBuilds.isEmpty()) {
            println "    ⚠️  Test Not Run Builds: ${data.notRunBuilds.sort().reverse().take(10).join(', ')}"
            if (data.notRunBuilds.size() > 10) {
                println "       (showing latest 10 of ${data.notRunBuilds.size()})"
            }
        }
    }
}

// Summary table
println "\n" + "=" * 100
println "SUMMARY TABLE"
println "=" * 100
println sprintf("%-10s | %-35s | %5s | %12s | %12s | %8s",
    "Memory", "Test", "Total", "Pipeline", "Test Success", "Test Run")
println sprintf("%-10s | %-35s | %5s | %12s | %12s | %8s",
    "", "", "Builds", "Success Rate", "Rate (When Ran)", "Rate")
println "-" * 100

stats.sort().each { memory, testData ->
    testData.sort().each { testType, data ->
        def pipelineRate = data.totalBuilds > 0 ?
            String.format("%.1f%%", (data.pipelineSuccess / data.totalBuilds) * 100) : "N/A"
        def testSuccessRate = data.testRan > 0 ?
            String.format("%.1f%%", (data.testPassed / data.testRan) * 100) : "N/A"
        def testRunRate = data.totalBuilds > 0 ?
            String.format("%.1f%%", (data.testRan / data.totalBuilds) * 100) : "N/A"
        
        println sprintf("%-10s | %-35s | %5d | %12s | %12s | %8s",
            memory, testType, data.totalBuilds, pipelineRate, testSuccessRate, testRunRate)
    }
}

// Best configurations
println "\n" + "=" * 100
println "BEST PERFORMING CONFIGURATIONS (By True Test Success Rate)"
println "=" * 100

def bestConfigs = []
stats.each { memory, testData ->
    testData.each { testType, data ->
        if (data.testRan >= 3) {
            def testSuccessRate = (data.testPassed / data.testRan) * 100
            bestConfigs.add([
                memory: memory,
                test: testType,
                testSuccessRate: testSuccessRate,
                testRan: data.testRan,
                testPassed: data.testPassed,
                testFailed: data.testFailed,
                pipelineSuccessRate: (data.pipelineSuccess / data.totalBuilds) * 100,
                totalBuilds: data.totalBuilds
            ])
        }
    }
}

bestConfigs.sort { -it.testSuccessRate }.take(15).each { config ->
    println sprintf("  %s + %-35s",
        config.memory.padRight(10),
        config.test)
    println sprintf("    Test Success: %.1f%% (%d passed / %d ran) | Pipeline: %.1f%%",
        config.testSuccessRate,
        config.testPassed,
        config.testRan,
        config.pipelineSuccessRate)
}

// Worst configurations
println "\n" + "=" * 100
println "CONFIGURATIONS NEEDING ATTENTION"
println "=" * 100

bestConfigs.sort { it.testSuccessRate }.take(10).each { config ->
    if (config.testSuccessRate < 100) {
        println sprintf("  %s + %-35s",
            config.memory.padRight(10),
            config.test)
        println sprintf("    Test Success: %.1f%% (%d passed / %d ran, %d failed) ⚠️",
            config.testSuccessRate,
            config.testPassed,
            config.testRan,
            config.testFailed)
    }
}

// Overall statistics
println "\n" + "=" * 100
println "OVERALL STATISTICS"
println "=" * 100

def totalBuilds = 0
def totalPipelineSuccess = 0
def totalTestRan = 0
def totalTestPassed = 0
def totalTestFailed = 0

stats.each { memory, testData ->
    testData.each { testType, data ->
        totalBuilds += data.totalBuilds
        totalPipelineSuccess += data.pipelineSuccess
        totalTestRan += data.testRan
        totalTestPassed += data.testPassed
        totalTestFailed += data.testFailed
    }
}

def overallPipelineRate = totalBuilds > 0 ?
    String.format("%.1f%%", (totalPipelineSuccess / totalBuilds) * 100) : "N/A"
def overallTestSuccessRate = totalTestRan > 0 ?
    String.format("%.1f%%", (totalTestPassed / totalTestRan) * 100) : "N/A"
def overallTestRunRate = totalBuilds > 0 ?
    String.format("%.1f%%", (totalTestRan / totalBuilds) * 100) : "N/A"

println sprintf("Total Builds Analyzed:           %d", totalBuilds)
println sprintf("Pipeline Success Rate:           %s (%d/%d)", 
    overallPipelineRate, totalPipelineSuccess, totalBuilds)
println sprintf("Test Run Rate:                   %s (%d/%d)",
    overallTestRunRate, totalTestRan, totalBuilds)
println sprintf("Test Success Rate (When Ran):    %s (%d/%d) ✅",
    overallTestSuccessRate, totalTestPassed, totalTestRan)
println sprintf("Test Failure Rate (When Ran):    %.1f%% (%d/%d)",
    totalTestRan > 0 ? (totalTestFailed / totalTestRan) * 100 : 0,
    totalTestFailed, totalTestRan)

println "\n" + "=" * 100
println "Analysis complete!"
println "\n📊 This script analyzes console logs to detect actual test failures"
println "   including kernel panics, timeouts, and test suite failures."
println "=" * 100

// Made with Bob
