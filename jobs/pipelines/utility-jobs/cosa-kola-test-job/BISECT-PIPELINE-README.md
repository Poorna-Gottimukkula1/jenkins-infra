# CoreOS Build Bisect Pipeline

## Overview

This Jenkins pipeline automates the process of identifying which Fedora CoreOS build introduced a kernel panic or other failure. It wraps the existing `coreos-builds-bisect.py` script with Jenkins automation, retry logic for intermittent failures, and comprehensive reporting.

## How It Works

The pipeline uses binary search (bisect) to efficiently find the problematic build:

```
Known Good Build (A) → Unknown Builds (B, C, D, E) → Known Bad Build (F)

1. Test middle build (C)
   - If PASS: Mark A, B, C as good
   - If FAIL: Mark C, D, E, F as bad
   
2. Continue bisecting until range is narrowed to two adjacent builds
3. Report: Last good build and first bad build
```

## Pipeline Parameters

| Parameter | Default | Description |
|-----------|---------|-------------|
| `GOOD_BUILD` | `45.20260703.91.0` | Known good build that works correctly |
| `BAD_BUILD` | `45.20260703.91.2` | Known bad build with kernel panic |
| `STREAM` | `rawhide` | CoreOS stream (rawhide, stable, testing, next) |
| `ARCH` | `ppc64le` | Target architecture |
| `QEMU_MEMORY` | `4096` | QEMU memory in MB |
| `TEST_RETRIES` | `3` | Number of retries per build (for intermittent failures) |
| `RESUME` | `false` | Resume a previous interrupted bisect |

## Usage

### 1. Starting a New Bisect

1. **Identify Good and Bad Builds**
   ```bash
   # Find a known good build (boots successfully)
   GOOD_BUILD=45.20260703.91.0
   
   # Find a known bad build (kernel panic)
   BAD_BUILD=45.20260703.91.2
   ```

2. **Run the Pipeline**
   - Go to Jenkins job: `utility-jobs/coreos-bisect`
   - Click "Build with Parameters"
   - Set `GOOD_BUILD` and `BAD_BUILD`
   - Set `STREAM` (usually `rawhide` for development)
   - Click "Build"

3. **Monitor Progress**
   - Pipeline will test builds in binary search order
   - Each build is tested with retries (default: 3)
   - Console output shows which builds pass/fail
   - Estimated steps remaining is displayed

### 2. Resuming an Interrupted Bisect

If the pipeline is interrupted (network issue, timeout, etc.):

1. **Resume the Bisect**
   - Go to the same Jenkins job
   - Click "Build with Parameters"
   - Set `RESUME` to `true`
   - Use the **same** `GOOD_BUILD` and `BAD_BUILD` as before
   - Click "Build"

2. **State is Preserved**
   - Previous test results are loaded from `coreos-builds-bisect-data.json`
   - Bisect continues from where it left off
   - No need to re-test already tested builds

## Test Logic

The pipeline tests each build with the following logic:

### Test Script Behavior

```bash
# For each build:
1. Fetch build artifacts (QEMU image)
2. Run upgrade test with timeout (15 minutes)
3. Check for kernel panic in logs
4. Retry if intermittent (default: 3 attempts)
5. Return result:
   - Exit 0: Test passed (no kernel panic)
   - Exit 1: Test failed (kernel panic detected)
   - Exit 99: Inconclusive (failure not related to kernel panic)
```

### Retry Logic for Intermittent Failures

Since kernel panics can be intermittent, the test script uses majority voting:

```
Retries = 3
- If 2+ passes: Build is GOOD
- If 2+ fails: Build is BAD
- Otherwise: Continue testing
```

## Output and Artifacts

### Archived Artifacts

After completion, the following files are archived:

1. **`bisect-output.log`** - Complete bisect execution log
2. **`BISECT-REPORT.md`** - Formatted report with results
3. **`coreos-builds-bisect-data.json`** - Bisect state (for resume)
4. **`test-*.log`** - Individual test logs for each build

### Bisect Report

The generated report includes:

```markdown
# Bisect Report: Kernel Panic Investigation

## Results
- Last Good Build: 45.20260703.91.0
- First Bad Build: 45.20260703.91.1

## Next Steps
1. Compare builds
2. Check kernel changes
3. Review build metadata
4. Report to upstream
```

## Example Scenarios

### Scenario 1: Kernel Panic in Upgrade Test

```
Problem: Upgrade from 45.20260703.91.1 → 45.20260703.91.2 causes kernel panic
Known Good: 45.20260703.91.0 (boots fine)
Known Bad: 45.20260703.91.2 (kernel panic)

Pipeline Parameters:
- GOOD_BUILD: 45.20260703.91.0
- BAD_BUILD: 45.20260703.91.2
- STREAM: rawhide
- TEST_RETRIES: 3

Expected Result:
- Identifies which build introduced the panic
- Provides package diff between good and bad builds
```

### Scenario 2: Intermittent Kernel Panic

```
Problem: Kernel panic occurs randomly (50% of the time)
Solution: Use TEST_RETRIES=5 for better confidence

Pipeline Parameters:
- TEST_RETRIES: 5 (instead of default 3)
- Other parameters as normal

Result:
- More reliable detection of intermittent issues
- Majority voting (3/5 passes = good, 3/5 fails = bad)
```

### Scenario 3: Network Interruption

```
Problem: Pipeline interrupted after testing 3 builds
Solution: Resume the bisect

Pipeline Parameters:
- RESUME: true
- Same GOOD_BUILD and BAD_BUILD as before

Result:
- Loads previous state
- Continues from where it left off
- No wasted effort
```

## Understanding the Results

### Last Good Build vs First Bad Build

```
Last Good Build: 45.20260703.91.0
First Bad Build: 45.20260703.91.1

This means:
- Build 45.20260703.91.0 works correctly
- Build 45.20260703.91.1 has the kernel panic
- The problem was introduced between these two builds
```

### Next Steps After Bisect

1. **Compare Package Versions**
   ```bash
   # Fetch stream
   cosa buildfetch --stream=rawhide --force
   
   # Fetch both builds
   cosa buildfetch --build=45.20260703.91.0 --artifact=qemu
   cosa buildfetch --build=45.20260703.91.1 --artifact=qemu
   
   # Compare packages
   rpm-ostree db diff 45.20260703.91.0 45.20260703.91.1
   ```

2. **Check Kernel Changes**
   ```bash
   # Extract kernel versions
   rpm-ostree db list 45.20260703.91.0 | grep kernel
   rpm-ostree db list 45.20260703.91.1 | grep kernel
   ```

3. **Review Build Metadata**
   - Visit build URLs in the report
   - Check commit logs
   - Review package changelogs

4. **Report Upstream**
   - File bug with bisect results
   - Include kernel panic logs
   - Reference both builds

## Troubleshooting

### Issue: Bisect Takes Too Long

**Solution**: Reduce TEST_RETRIES
```
Default: 3 retries per build
For faster results: 1 retry (less reliable for intermittent issues)
For more reliability: 5 retries (slower but more accurate)
```

### Issue: All Builds Fail

**Problem**: The "good" build might not actually be good

**Solution**: 
1. Manually verify the good build works
2. Try an older build as the good build
3. Check if the test itself is broken

### Issue: All Builds Pass

**Problem**: The "bad" build might not actually be bad, or test doesn't detect the issue

**Solution**:
1. Manually verify the bad build fails
2. Check if test script correctly detects kernel panic
3. Review test logs for false positives

### Issue: Inconclusive Results

**Problem**: Test returns exit code 99 (inconclusive)

**Meaning**: Test failed but not due to kernel panic

**Solution**:
1. Review test logs to understand failure
2. Adjust test script to handle this case
3. May need to bisect separately for this different issue

## Advanced Usage

### Custom Test Script

You can modify the test script in the pipeline for different test scenarios:

```groovy
// In Jenkinsfile-bisect-wrapper, modify the testScript variable
def testScript = '''#!/bin/bash
# Your custom test logic here
# Must accept BUILD_ID as $1
# Must return 0 (pass), 1 (fail), or 99 (inconclusive)
'''
```

### Different Test Types

The pipeline can be adapted for different tests:

1. **Kernel Replace Test**: Change test command to `cosa kola run ext.config.kernel-replace`
2. **Specific Kola Test**: Change to `cosa kola run <test-name>`
3. **Custom Test**: Provide your own test script

## Technical Details

### Files and Locations

```
Workspace Structure:
├── cosa-workspace/
│   ├── builds/
│   │   └── builds.json              # List of all builds
│   ├── coreos-builds-bisect-data.json  # Bisect state
│   ├── bisect-output.log            # Full bisect log
│   ├── BISECT-REPORT.md             # Generated report
│   └── test-*.log                   # Individual test logs
├── coreos-builds-bisect.py          # Bisect script
└── test-upgrade-panic.sh            # Test script
```

### Bisect Algorithm

The Python script uses binary search:

```python
# Pseudocode
builds = [A, B, C, D, E, F, G]  # Ordered list
good = A
bad = G

while unknowns exist:
    middle = unknowns[len(unknowns)//2]
    result = test(middle)
    
    if result == PASS:
        mark all builds from good to middle as GOOD
    elif result == FAIL:
        mark all builds from middle to bad as BAD
    else:  # INCONCLUSIVE
        mark middle as INCONCLUSIVE, continue
```

## References

- **Original Bisect Script**: `fedora-coreos-config/tests/manual/coreos-builds-bisect.py`
- **Inspired By**: [rpm-ostree-bisect](https://github.com/ostreedev/ostree-releng-scripts/blob/master/rpm-ostree-bisect)
- **CoreOS Builds**: https://builds.coreos.fedoraproject.org/
- **Bug Reports**: See `LTC-BUG-REPORT.md` for template

## Support

For issues or questions:
1. Check console output for error messages
2. Review archived artifacts (logs, reports)
3. Consult with CoreOS team
4. File Jenkins job issue if pipeline problem

---

**Last Updated**: 2026-07-04  
**Pipeline**: `utility-jobs/coreos-bisect`  
**Maintainer**: CoreOS Testing Team