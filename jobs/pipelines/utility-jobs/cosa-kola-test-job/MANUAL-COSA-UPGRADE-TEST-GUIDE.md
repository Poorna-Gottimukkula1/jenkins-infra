# Manual COSA Upgrade Test Guide

Step-by-step guide to run CoreOS upgrade tests manually using COSA (CoreOS Assembler) commands.

## Table of Contents
- [Prerequisites](#prerequisites)
- [Setup COSA Environment](#setup-cosa-environment)
- [Test Scenarios](#test-scenarios)
- [Troubleshooting](#troubleshooting)
- [Command Reference](#command-reference)

---

## Prerequisites

### System Requirements
- **Architecture:** ppc64le (PowerPC 64-bit Little Endian)
- **OS:** Fedora or RHEL-based system
- **Memory:** 16GB+ recommended (8GB for VM + 8GB for host)
- **Disk:** 50GB+ free space
- **Packages:** podman, git, jq

### Install Required Packages
```bash
# Install podman and dependencies
sudo dnf install -y podman git jq

# Verify podman
podman --version

# Enable KVM (if not already enabled)
sudo modprobe kvm
sudo chmod 666 /dev/kvm
```

### Pull COSA Container
```bash
# Pull latest COSA container
podman pull quay.io/coreos-assembler/coreos-assembler:latest

# Verify
podman images | grep coreos-assembler
```

---

## Setup COSA Environment

### Step 1: Create Working Directory
```bash
# Create a dedicated directory for COSA work
mkdir -p ~/cosa-workspace
cd ~/cosa-workspace

# Set environment variable
export COREOS_ASSEMBLER_CONTAINER="quay.io/coreos-assembler/coreos-assembler:latest"
```

### Step 2: Define COSA Helper Function

#### Option A: Temporary (Current Session Only)
```bash
# Define function in current shell session
cosa() {
    env | grep COREOS_ASSEMBLER
    local -r COREOS_ASSEMBLER_CONTAINER_LATEST="quay.io/coreos-assembler/coreos-assembler:latest"
    
    if [[ -z ${COREOS_ASSEMBLER_CONTAINER} ]] && podman image exists ${COREOS_ASSEMBLER_CONTAINER_LATEST}; then
        local -r cosa_build_date_str="$(podman inspect -f "{{.Created}}" ${COREOS_ASSEMBLER_CONTAINER_LATEST} | awk '{print $1}')"
        local -r cosa_build_date="$(date -d ${cosa_build_date_str} +%s)"
        
        if [[ $(date +%s) -ge $((cosa_build_date + 60*60*24*7)) ]]; then
            echo -e "\e[0;33m----" >&2
            echo "The COSA container image is more than a week old and likely outdated." >&2
            echo "You should pull the latest version with:" >&2
            echo "podman pull ${COREOS_ASSEMBLER_CONTAINER_LATEST}" >&2
            echo -e "----\e[0m" >&2
            sleep 10
        fi
    fi
    
    set -x
    podman run --rm -ti --security-opt=label=disable --privileged \
        --userns=keep-id:uid=1000,gid=1000 \
        -v=${PWD}:/srv/ --device=/dev/kvm --device=/dev/fuse \
        --tmpfs=/tmp -v=/var/tmp:/var/tmp --name=cosa-$(uuidgen) \
        ${COREOS_ASSEMBLER_CONFIG_GIT:+-v=$COREOS_ASSEMBLER_CONFIG_GIT:/srv/src/config/:ro} \
        ${COREOS_ASSEMBLER_GIT:+-v=$COREOS_ASSEMBLER_GIT/src/:/usr/lib/coreos-assembler/:ro} \
        ${COREOS_ASSEMBLER_ADD_CERTS:+-v=/etc/pki/ca-trust:/etc/pki/ca-trust:ro} \
        ${COREOS_ASSEMBLER_CONTAINER_RUNTIME_ARGS} \
        ${COREOS_ASSEMBLER_CONTAINER:-$COREOS_ASSEMBLER_CONTAINER_LATEST} "$@"
    rc=$?
    set +x
    return $rc
}

# Test the function
cosa --help
```

#### Option B: Permanent (Add to ~/.bashrc) ⭐ RECOMMENDED

**One-Command Setup (No Editor Required):**

```bash
# Append COSA function to ~/.bashrc using cat
cat >> ~/.bashrc << 'EOF'

# ============================================
# COSA (CoreOS Assembler) Helper Function
# ============================================

# Set default COSA container
export COREOS_ASSEMBLER_CONTAINER="quay.io/coreos-assembler/coreos-assembler:latest"

# COSA wrapper function
cosa() {
    env | grep COREOS_ASSEMBLER
    local -r COREOS_ASSEMBLER_CONTAINER_LATEST="quay.io/coreos-assembler/coreos-assembler:latest"
    
    # Check if container image is outdated
    if [[ -z ${COREOS_ASSEMBLER_CONTAINER} ]] && podman image exists ${COREOS_ASSEMBLER_CONTAINER_LATEST}; then
        local -r cosa_build_date_str="$(podman inspect -f "{{.Created}}" ${COREOS_ASSEMBLER_CONTAINER_LATEST} | awk '{print $1}')"
        local -r cosa_build_date="$(date -d ${cosa_build_date_str} +%s)"
        
        if [[ $(date +%s) -ge $((cosa_build_date + 60*60*24*7)) ]]; then
            echo -e "\e[0;33m----" >&2
            echo "The COSA container image is more than a week old and likely outdated." >&2
            echo "You should pull the latest version with:" >&2
            echo "podman pull ${COREOS_ASSEMBLER_CONTAINER_LATEST}" >&2
            echo -e "----\e[0m" >&2
            sleep 10
        fi
    fi
    
    # Run COSA container with proper mounts and privileges
    set -x
    podman run --rm -ti --security-opt=label=disable --privileged \
        --userns=keep-id:uid=1000,gid=1000 \
        -v=${PWD}:/srv/ --device=/dev/kvm --device=/dev/fuse \
        --tmpfs=/tmp -v=/var/tmp:/var/tmp --name=cosa-$(uuidgen) \
        ${COREOS_ASSEMBLER_CONFIG_GIT:+-v=$COREOS_ASSEMBLER_CONFIG_GIT:/srv/src/config/:ro} \
        ${COREOS_ASSEMBLER_GIT:+-v=$COREOS_ASSEMBLER_GIT/src/:/usr/lib/coreos-assembler/:ro} \
        ${COREOS_ASSEMBLER_ADD_CERTS:+-v=/etc/pki/ca-trust:/etc/pki/ca-trust:ro} \
        ${COREOS_ASSEMBLER_CONTAINER_RUNTIME_ARGS} \
        ${COREOS_ASSEMBLER_CONTAINER:-$COREOS_ASSEMBLER_CONTAINER_LATEST} "$@"
    rc=$?
    set +x
    return $rc
}

# Optional: Add alias for quick COSA workspace navigation
alias cdcosa='cd ~/cosa-workspace'

# ============================================
# End of COSA Configuration
# ============================================
EOF

# Reload bashrc to apply changes immediately
source ~/.bashrc

# Verify cosa function is available
type cosa

# Test the function
cosa --help
```

**That's it!** The COSA function is now permanently available in all new bash sessions.

**Alternative: Manual Method (Using Editor)**

If you prefer to edit manually:

```bash
# Open ~/.bashrc in your preferred editor
vi ~/.bashrc
# or
nano ~/.bashrc

# Scroll to the end and paste the COSA function block shown above
# Save and exit, then reload:
source ~/.bashrc
```

**Verify in New Terminal:**
```bash
# Open a new terminal and test
cosa --help

# Should work without redefining the function
```

#### Option C: System-Wide Installation (All Users)

**For system administrators who want COSA available to all users:**

```bash
# Create system-wide script
sudo vi /etc/profile.d/cosa.sh

# Add the following content:
```

```bash
#!/bin/bash
# /etc/profile.d/cosa.sh
# System-wide COSA (CoreOS Assembler) configuration

export COREOS_ASSEMBLER_CONTAINER="quay.io/coreos-assembler/coreos-assembler:latest"

cosa() {
    env | grep COREOS_ASSEMBLER
    local -r COREOS_ASSEMBLER_CONTAINER_LATEST="quay.io/coreos-assembler/coreos-assembler:latest"
    
    if [[ -z ${COREOS_ASSEMBLER_CONTAINER} ]] && podman image exists ${COREOS_ASSEMBLER_CONTAINER_LATEST}; then
        local -r cosa_build_date_str="$(podman inspect -f "{{.Created}}" ${COREOS_ASSEMBLER_CONTAINER_LATEST} | awk '{print $1}')"
        local -r cosa_build_date="$(date -d ${cosa_build_date_str} +%s)"
        
        if [[ $(date +%s) -ge $((cosa_build_date + 60*60*24*7)) ]]; then
            echo -e "\e[0;33m----" >&2
            echo "The COSA container image is more than a week old and likely outdated." >&2
            echo "You should pull the latest version with:" >&2
            echo "podman pull ${COREOS_ASSEMBLER_CONTAINER_LATEST}" >&2
            echo -e "----\e[0m" >&2
            sleep 10
        fi
    fi
    
    set -x
    podman run --rm -ti --security-opt=label=disable --privileged \
        --userns=keep-id:uid=1000,gid=1000 \
        -v=${PWD}:/srv/ --device=/dev/kvm --device=/dev/fuse \
        --tmpfs=/tmp -v=/var/tmp:/var/tmp --name=cosa-$(uuidgen) \
        ${COREOS_ASSEMBLER_CONFIG_GIT:+-v=$COREOS_ASSEMBLER_CONFIG_GIT:/srv/src/config/:ro} \
        ${COREOS_ASSEMBLER_GIT:+-v=$COREOS_ASSEMBLER_GIT/src/:/usr/lib/coreos-assembler/:ro} \
        ${COREOS_ASSEMBLER_ADD_CERTS:+-v=/etc/pki/ca-trust:/etc/pki/ca-trust:ro} \
        ${COREOS_ASSEMBLER_CONTAINER_RUNTIME_ARGS} \
        ${COREOS_ASSEMBLER_CONTAINER:-$COREOS_ASSEMBLER_CONTAINER_LATEST} "$@"
    rc=$?
    set +x
    return $rc
}
```

```bash
# Make executable
sudo chmod +x /etc/profile.d/cosa.sh

# Reload profile
source /etc/profile.d/cosa.sh

# Test
cosa --help
```

#### Verification Steps

After setting up COSA function (any method above):

```bash
# 1. Check if function is defined
type cosa
# Output: cosa is a function

# 2. Check environment variable
echo $COREOS_ASSEMBLER_CONTAINER
# Output: quay.io/coreos-assembler/coreos-assembler:latest

# 3. Test COSA help
cosa --help
# Should show COSA help menu

# 4. Test in new terminal
# Open new terminal and run:
cosa --help
# Should work without errors
```

### Step 3: Initialize COSA
```bash
# Initialize with Fedora CoreOS config
cosa init https://github.com/coreos/fedora-coreos-config

# Or initialize with specific branch
cosa init --branch testing-devel https://github.com/coreos/fedora-coreos-config

# Verify initialization
ls -la
# You should see: builds/ cache/ overrides/ src/ tmp/
```

---

## Test Scenarios

### Scenario 1: Quick Upgrade Test (Fetch Mode)

**Use Case:** Test upgrade from latest stable build

#### Step 1: Fetch Pre-built Artifacts
```bash
cd ~/cosa-workspace

# Fetch latest stable QEMU image for ppc64le
cosa buildfetch --stream=stable --arch=ppc64le

# Verify downloaded artifacts
ls -lh builds/latest/ppc64le/
# Should show: fedora-coreos-*.qcow2.xz
```

#### Step 2: Fetch OCI Archive (Required for Upgrades)
```bash
# Fetch OCI archive (ostree artifact)
cosa buildfetch --stream=stable --artifact=ostree --arch=ppc64le

# Verify OCI archive
ls -lh builds/*/ppc64le/*ociarchive
# Should show: fedora-coreos-*-ostree.ppc64le.ociarchive
```

#### Step 3: Run Upgrade Test
```bash
# Run upgrade test with 8GB memory (recommended for ppc64le)
cosa kola run-upgrade \
    --build=latest \
    --arch=ppc64le \
    --qemu-memory 8192 \
    --upgrades \
    2>&1 | tee kola-upgrade-output.log

# Monitor in another terminal
tail -f kola-upgrade-output.log
```

#### Step 4: Check Results
```bash
# Check exit code
echo $?
# 0 = success, non-zero = failure

# View detailed logs
cat kola-upgrade-output.log

# Check for errors
grep -i error kola-upgrade-output.log
grep -i fail kola-upgrade-output.log
```

---

### Scenario 2: Upgrade Test with Specific Build Version

#### Step 1: Fetch Specific Build
```bash
cd ~/cosa-workspace

# Fetch specific build version
BUILD_VERSION="44.20260607.3.1"
cosa buildfetch --build=${BUILD_VERSION} --arch=ppc64le

# Fetch OCI archive for that build
cosa buildfetch --build=${BUILD_VERSION} --artifact=ostree --arch=ppc64le

# Verify
ls -lh builds/${BUILD_VERSION}/ppc64le/
```

#### Step 2: Run Upgrade Test
```bash
# Run upgrade from specific build
cosa kola run-upgrade \
    --build=${BUILD_VERSION} \
    --arch=ppc64le \
    --qemu-memory 8192 \
    --upgrades \
    2>&1 | tee kola-upgrade-${BUILD_VERSION}.log
```

---

### Scenario 3: Build from Scratch + Upgrade Test

#### Step 1: Initialize and Fetch
```bash
cd ~/cosa-workspace

# Initialize with specific stream
cosa init --branch testing-devel https://github.com/coreos/fedora-coreos-config

# Fetch packages
cosa fetch
```

#### Step 2: Build CoreOS
```bash
# Build QEMU image
cosa build

# Build metal image (optional)
cosa buildextend-metal

# Build metal4k image (optional)
cosa buildextend-metal4k

# Check build
ls -lh builds/latest/ppc64le/
```

#### Step 3: Build OCI Archive
```bash
# Build ostree (OCI archive) - required for upgrade tests
cosa buildextend-ostree

# Verify OCI archive
ls -lh builds/latest/ppc64le/*ociarchive
```

#### Step 4: Run Upgrade Test
```bash
# Run upgrade test on your custom build
cosa kola run-upgrade \
    --build=latest \
    --arch=ppc64le \
    --qemu-memory 8192 \
    --upgrades \
    2>&1 | tee kola-upgrade-custom.log
```

---

### Scenario 4: Extended Upgrade Tests

**Use Case:** Test upgrades across multiple Fedora versions

#### Step 1: Fetch Base Build
```bash
cd ~/cosa-workspace

# Fetch stable build
cosa buildfetch --stream=stable --arch=ppc64le
cosa buildfetch --stream=stable --artifact=ostree --arch=ppc64le
```

#### Step 2: Run Extended Upgrade Tests
```bash
# Run extended upgrade tests (tagged with extended-upgrade)
cosa kola run \
    --tag extended-upgrade \
    --arch=ppc64le \
    --qemu-memory 8192 \
    --parallel 2 \
    2>&1 | tee kola-extended-upgrade.log
```

#### Step 3: Specify Target Stream
```bash
# Upgrade to specific target stream
export COSA_UPGRADE_STREAM="testing"

cosa kola run \
    --tag extended-upgrade \
    --arch=ppc64le \
    --qemu-memory 8192 \
    2>&1 | tee kola-extended-upgrade-to-testing.log
```

---

### Scenario 5: Regular Kola Test (e.g., Kernel Replace)

#### Step 1: Fetch Build
```bash
cd ~/cosa-workspace

# Fetch latest build
cosa buildfetch --stream=stable --arch=ppc64le
```

#### Step 2: Run Specific Kola Test
```bash
# Run kernel-replace test
cosa kola run \
    ext.config.rpm-ostree.kernel-replace \
    --arch=ppc64le \
    --qemu-memory 4096 \
    2>&1 | tee kola-kernel-replace.log

# Run basic test
cosa kola run basic \
    --arch=ppc64le \
    --qemu-memory 2048 \
    2>&1 | tee kola-basic.log

# Run all tests
cosa kola run \
    --arch=ppc64le \
    --qemu-memory 4096 \
    --parallel auto \
    2>&1 | tee kola-all.log
```

---

### Scenario 6: Parallel and Multiple Test Runs

#### Run Test Multiple Times
```bash
# Run test 5 times sequentially
for i in {1..5}; do
    echo "=== Run $i of 5 ==="
    cosa kola run-upgrade \
        --build=latest \
        --arch=ppc64le \
        --qemu-memory 8192 \
        --upgrades \
        2>&1 | tee kola-upgrade-run-${i}.log
done
```

#### Run Tests in Parallel
```bash
# Run 2 tests in parallel (for extended upgrade tests only)
cosa kola run \
    --tag extended-upgrade \
    --arch=ppc64le \
    --qemu-memory 8192 \
    --parallel 2 \
    2>&1 | tee kola-parallel.log
```

**Note:** `--parallel` and `--multiply` only work with `cosa kola run`, NOT with `cosa kola run-upgrade`.

---

## Troubleshooting

### Issue 1: Missing OCI Archive

**Error:**
```
open builds/latest/ppc64le/fedora-coreos-*.ociarchive: no such file or directory
```

**Solution:**
```bash
# Fetch OCI archive explicitly
cosa buildfetch --stream=stable --artifact=ostree --arch=ppc64le

# Or for specific build
cosa buildfetch --build=44.20260607.3.1 --artifact=ostree --arch=ppc64le

# Verify
ls -lh builds/*/ppc64le/*ociarchive
```

---

### Issue 2: VM Hangs During Upgrade

**Symptoms:**
- Test hangs for >10 minutes
- High CPU usage on host
- No progress in logs

**Solution:**
```bash
# Kill hung QEMU process
pkill -9 qemu-system-ppc64

# Increase memory to 8GB or 12GB
cosa kola run-upgrade \
    --build=latest \
    --arch=ppc64le \
    --qemu-memory 12288 \
    --upgrades

# Check host resources
free -h
df -h
```

---

### Issue 3: Container Permission Issues

**Error:**
```
Error: cannot setup namespace using newuidmap: ...
```

**Solution:**
```bash
# Check subuid/subgid
cat /etc/subuid
cat /etc/subgid

# If missing, add entries
echo "$(whoami):100000:65536" | sudo tee -a /etc/subuid
echo "$(whoami):100000:65536" | sudo tee -a /etc/subgid

# Restart podman
podman system reset
```

---

### Issue 4: KVM Device Not Available

**Error:**
```
Could not access KVM kernel module: No such file or directory
```

**Solution:**
```bash
# Load KVM module
sudo modprobe kvm

# For ppc64le
sudo modprobe kvm_hv

# Set permissions
sudo chmod 666 /dev/kvm

# Verify
ls -l /dev/kvm
```

---

### Issue 5: Disk Space Issues

**Error:**
```
No space left on device
```

**Solution:**
```bash
# Check disk space
df -h

# Clean up old builds
cosa prune --keep-last 2

# Clean up podman cache
podman system prune -a -f

# Clean up tmp
sudo rm -rf /var/tmp/cosa-*
```

---

## Command Reference

### COSA Initialization
```bash
# Initialize with default stream
cosa init https://github.com/coreos/fedora-coreos-config

# Initialize with specific branch
cosa init --branch testing-devel https://github.com/coreos/fedora-coreos-config

# Initialize with custom config repo
cosa init https://github.com/YOUR-ORG/fedora-coreos-config
```

### Fetching Artifacts
```bash
# Fetch latest from stream
cosa buildfetch --stream=stable --arch=ppc64le

# Fetch specific build
cosa buildfetch --build=44.20260607.3.1 --arch=ppc64le

# Fetch specific artifact type
cosa buildfetch --stream=stable --artifact=qemu --arch=ppc64le
cosa buildfetch --stream=stable --artifact=ostree --arch=ppc64le
cosa buildfetch --stream=stable --artifact=metal --arch=ppc64le

# Force re-fetch
cosa buildfetch --stream=stable --arch=ppc64le --force
```

### Building from Scratch
```bash
# Fetch packages
cosa fetch

# Build base image
cosa build

# Build QEMU image
cosa buildextend-qemu

# Build metal images
cosa buildextend-metal
cosa buildextend-metal4k

# Build OCI archive (required for upgrades)
cosa buildextend-ostree

# Build live ISO
cosa buildextend-live
```

### Running Tests
```bash
# Run upgrade test
cosa kola run-upgrade --build=latest --arch=ppc64le --qemu-memory 8192 --upgrades

# Run specific kola test
cosa kola run ext.config.rpm-ostree.kernel-replace --arch=ppc64le --qemu-memory 4096

# Run all tests
cosa kola run --arch=ppc64le --qemu-memory 4096

# Run extended upgrade tests
cosa kola run --tag extended-upgrade --arch=ppc64le --qemu-memory 8192

# Run with parallel execution
cosa kola run --tag extended-upgrade --arch=ppc64le --parallel 2

# Run with additional arguments
cosa kola run-upgrade --build=latest --arch=ppc64le --qemu-memory 8192 --upgrades --rerun --allow-rerun-success=tags=needs-internet
```

### Listing and Inspecting
```bash
# List available tests
cosa kola list

# List builds
ls -lh builds/

# Show build metadata
cat builds/latest/ppc64le/meta.json | jq

# Show builds.json
cat builds/builds.json | jq
```

### Cleanup
```bash
# Prune old builds (keep last 2)
cosa prune --keep-last 2

# Clean all builds
cosa clean

# Remove specific build
rm -rf builds/44.20260607.3.1/
```

---

## Memory Configuration Guide

| Test Type | Recommended | Minimum | Maximum |
|-----------|-------------|---------|---------|
| **Upgrade Tests (ppc64le)** | 8192 MB | 4096 MB | 12288 MB |
| **Extended Upgrade** | 8192 MB | 6144 MB | 12288 MB |
| **Kernel Replace** | 4096 MB | 2048 MB | 8192 MB |
| **Basic Tests** | 2048 MB | 2048 MB | 4096 MB |

**Why 8GB for ppc64le upgrades?**
- ppc64le uses 64k page size (vs 4k on x86_64)
- Higher memory pressure during rpm-ostree operations
- Prevents VM hangs and I/O deadlocks

---

## Additional Tips

### Enable Debug Mode
```bash
# Set debug environment variable
export COSA_DEBUG=1

# Run with verbose output
cosa kola run-upgrade --build=latest --arch=ppc64le --qemu-memory 8192 --upgrades -v
```

### Monitor Test Progress
```bash
# In another terminal, watch logs
watch -n 2 'tail -20 kola-upgrade-output.log'

# Monitor QEMU processes
watch -n 2 'ps aux | grep qemu-system-ppc64'

# Monitor memory usage
watch -n 2 'free -h'
```

### Save Test Results
```bash
# Create results directory
mkdir -p ~/cosa-test-results/$(date +%Y%m%d-%H%M%S)

# Copy logs and artifacts
cp kola-*.log ~/cosa-test-results/$(date +%Y%m%d-%H%M%S)/
cp -r tmp/kola/ ~/cosa-test-results/$(date +%Y%m%d-%H%M%S)/ 2>/dev/null || true
```

### Quick Test Script
```bash
#!/bin/bash
# quick-upgrade-test.sh

set -e

STREAM="${1:-stable}"
MEMORY="${2:-8192}"

echo "=== Quick Upgrade Test ==="
echo "Stream: $STREAM"
echo "Memory: $MEMORY MB"

cd ~/cosa-workspace

# Fetch artifacts
echo "Fetching artifacts..."
cosa buildfetch --stream=$STREAM --arch=ppc64le
cosa buildfetch --stream=$STREAM --artifact=ostree --arch=ppc64le

# Run upgrade test
echo "Running upgrade test..."
cosa kola run-upgrade \
    --build=latest \
    --arch=ppc64le \
    --qemu-memory $MEMORY \
    --upgrades \
    2>&1 | tee kola-upgrade-$(date +%Y%m%d-%H%M%S).log

echo "=== Test Complete ==="
```

Usage:
```bash
chmod +x quick-upgrade-test.sh
./quick-upgrade-test.sh stable 8192
```

---

## Resources

- **COSA Documentation:** https://github.com/coreos/coreos-assembler
- **Kola Test Framework:** https://github.com/coreos/coreos-assembler/tree/main/mantle/kola
- **Fedora CoreOS Config:** https://github.com/coreos/fedora-coreos-config
- **Fedora CoreOS Builds:** https://builds.coreos.fedoraproject.org/

---

**Last Updated:** July 3, 2026  
**Maintained By:** CoreOS Testing Team