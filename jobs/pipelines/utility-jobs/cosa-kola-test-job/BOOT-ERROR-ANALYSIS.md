# Boot Error Analysis - Fedora CoreOS ppc64le Kernel Replace Test

## Executive Summary

The system failed to boot during the `ext.config.rpm-ostree.kernel-replace` kola test on **ppc64le** architecture. The failure is caused by **BTF (BPF Type Format) validation errors** preventing critical kernel modules from loading, specifically the `scsi_transport_iscsi` module that dracut requires.

## Critical Error Chain

```
BTF validation fails for modules (-22 EINVAL)
    ↓
Modules cannot be loaded (scsi_transport_iscsi, nfnetlink, etc.)
    ↓
systemd-modules-load.service fails
    ↓
dracut detects missing iSCSI support
    ↓
dracut refuses to continue (FATAL error)
    ↓
System powers off
```

## Primary Errors

### 1. BTF Validation Failures (Root Cause)

**Error Pattern** (repeated 200+ times):
```
[    1.285406] failed to validate module [nfnetlink] BTF: -22
[    4.881171] failed to validate module [scsi_transport_iscsi] BTF: -22
[    4.206780] failed to validate module [i2c_dev] BTF: -22
[    4.349595] failed to validate module [scsi_dh_alua] BTF: -22
[    4.364327] failed to validate module [scsi_dh_emc] BTF: -22
[    4.446959] failed to validate module [scsi_dh_rdac] BTF: -22
[    4.217789] failed to validate module [dm_multipath] BTF: -22
```

**Error Code**: `-22` = `EINVAL` (Invalid argument)

**Root Cause Analysis**:
- Kernel 7.0.13-200.fc44.ppc64le compiled with BTF support
- Modules have embedded BTF information
- BTF type validation is rejecting modules due to type mismatches
- Likely caused by **gcc 16.1.1** (very new, released 2026-05-15) having BTF generation bugs
- Kernel 7.0.x is RC (Release Candidate) with potential BTF format changes

### 2. Module Loading Failures

```
systemd-modules-load[277]: Failed to insert module 'fuse': Invalid argument
systemd-modules-load[277]: Failed to insert module 'i2c_dev': Invalid argument
systemd-modules-load[277]: Failed to insert module 'scsi_dh_alua': Invalid argument
systemd-modules-load[277]: Failed to insert module 'scsi_dh_emc': Invalid argument
systemd-modules-load[277]: Failed to insert module 'scsi_dh_rdac': Invalid argument
systemd-modules-load[277]: Failed to insert module 'scsi_transport_iscsi': Invalid argument
```

**Impact**: Critical modules required for boot cannot be loaded.

### 3. Dracut Fatal Error

```
[    4.902194] dracut: FATAL: iscsiroot requested but kernel/initrd does not support iscsi
[    4.902372] dracut: Refusing to continue
[    4.932633] systemd[1]: Poweroff requested from client PID 419 ('systemctl')
```

**Why iSCSI?**: The initramfs was built with iSCSI support enabled by default, but the `scsi_transport_iscsi` module failed to load due to BTF errors.

### 4. BPF Subsystem Errors

```
[    0.146744] BPF: Invalid type_id
[    0.807989] error while registering bpf memcontrol kfuncs: -22
[    0.981225] hid_bpf: error while setting HID BPF tracing kfuncs: -22
```

**Impact**: BPF functionality is broken, affecting modern kernel features.

## System Configuration

### Hardware
- **Architecture**: ppc64le (POWER10)
- **Platform**: IBM pSeries (QEMU/KVM emulation)
- **Memory**: 4GB (as configured in test)
- **CPUs**: 1 CPU

### Software
- **OS**: Fedora CoreOS 45.20260225.91.1
- **Kernel**: 7.0.13-200.fc44.ppc64le
- **Compiler**: gcc 16.1.1 20260515 (Red Hat 16.1.1-2)
- **Build Date**: Fri Jun 19 22:36:50 UTC 2026

### Test Context
- **Test**: `ext.config.rpm-ostree.kernel-replace`
- **Purpose**: Verify kernel replacement via derived container image
- **Stage**: System failed during initial boot (Stage 0)

## Why This Affects kernel-replace Test

The `kernel-replace` test specifically:
1. **Builds a derived container** with a different kernel
2. **Requires working module loading** to verify kernel functionality
3. **Needs BTF support** for modern BPF-based features
4. **Cannot proceed** if the system won't boot

The test is currently **denylisted for ppc64le** in `kola-denylist.yaml`:
```yaml
- pattern: ext.config.rpm-ostree.kernel-replace
  tracker: https://github.com/coreos/fedora-coreos-tracker/issues/2115
  snooze: 2026-07-03
  warn: true
  arches:
    - ppc64le
```

## Solutions

### Solution 1: Disable iSCSI in Dracut (Immediate Fix)

**Location**: `fedora-coreos-config/overlay.d/`

Create a dracut configuration to omit iSCSI:

```bash
# In fedora-coreos-config repository
mkdir -p overlay.d/99disable-iscsi/usr/lib/dracut/dracut.conf.d/
cat > overlay.d/99disable-iscsi/usr/lib/dracut/dracut.conf.d/99-no-iscsi.conf << 'EOF'
# Disable iSCSI support in initramfs
# Not needed for local disk boot and causes issues with BTF validation
omit_dracutmodules+=" iscsi "
EOF
```

**Add to manifest.yaml**:
```yaml
ostree-layers:
  - overlay.d/99disable-iscsi
```

### Solution 2: Disable BTF in Kernel Modules (Workaround)

**Location**: `kernel/linux` repository

Modify kernel config for ppc64le:

```bash
# In kernel config for ppc64le
CONFIG_DEBUG_INFO_BTF=n
CONFIG_DEBUG_INFO_BTF_MODULES=n
```

**Pros**: Eliminates BTF validation errors
**Cons**: Loses BPF functionality, not a long-term solution

### Solution 3: Use Stable Kernel Version (Recommended)

**Location**: `fedora-coreos-config/manifest.yaml`

Instead of kernel 7.0.x RC, use stable 6.x kernel:

```yaml
# In manifests/fedora-coreos.yaml
packages:
  # Use stable kernel instead of 7.0.x RC
  - kernel-6.12  # or latest stable 6.x
```

**Rationale**:
- Kernel 7.0.x is Release Candidate (unstable)
- BTF format may have changed
- Stable 6.x kernels have proven BTF support

### Solution 4: Downgrade GCC Compiler (Build System Fix)

**Location**: `coreos-assembler` build configuration

Use gcc 15.x or 14.x instead of 16.1.1:

```dockerfile
# In coreos-assembler/Dockerfile
RUN dnf install -y gcc-15 gcc-c++-15
ENV CC=gcc-15
ENV CXX=g++-15
```

**Rationale**: gcc 16.1.1 is very new and may have BTF generation bugs.

### Solution 5: Blacklist Problematic Modules

**Location**: `fedora-coreos-config/overlay.d/`

Create module blacklist:

```bash
mkdir -p overlay.d/99blacklist-modules/etc/modprobe.d/
cat > overlay.d/99blacklist-modules/etc/modprobe.d/blacklist-btf-broken.conf << 'EOF'
# Blacklist modules with BTF validation issues
blacklist scsi_transport_iscsi
blacklist nfnetlink
# Add others as needed
EOF
```

## Recommended Action Plan

### Phase 1: Immediate (Enable Testing)
1. ✅ **Disable iSCSI in dracut** (Solution 1)
2. ✅ **Update kola-denylist.yaml** to extend snooze date or remove ppc64le entry once fixed

### Phase 2: Short-term (Stabilize)
3. ✅ **Use stable kernel 6.x** instead of 7.0.x RC (Solution 3)
4. ✅ **Test with gcc 15.x** to verify compiler issue (Solution 4)

### Phase 3: Long-term (Proper Fix)
5. ✅ **Wait for gcc 16.1.2+** with BTF fixes
6. ✅ **Wait for kernel 7.0 stable release** with finalized BTF format
7. ✅ **Re-enable BTF** once toolchain is stable

## Implementation Steps

### Step 1: Disable iSCSI in fedora-coreos-config

```bash
cd fedora-coreos-config

# Create overlay directory
mkdir -p overlay.d/99disable-iscsi/usr/lib/dracut/dracut.conf.d/

# Create dracut config
cat > overlay.d/99disable-iscsi/usr/lib/dracut/dracut.conf.d/99-no-iscsi.conf << 'EOF'
# Disable iSCSI support in initramfs for ppc64le
# Workaround for BTF validation issues with scsi_transport_iscsi module
# See: https://github.com/coreos/fedora-coreos-tracker/issues/2115
omit_dracutmodules+=" iscsi "
EOF

# Add to manifest
# Edit manifests/fedora-coreos.yaml and add:
# ostree-layers:
#   - overlay.d/99disable-iscsi
```

### Step 2: Update Jenkinsfile to Use Stable Kernel

```groovy
// In jenkins-infra/jobs/pipelines/utility-jobs/cosa-kola-test-job/Jenkinsfile
parameters {
    choice(
        name: 'KERNEL_VERSION_PREFERENCE',
        choices: ['stable-6.x', 'latest-7.x-rc'],
        description: 'Kernel version preference (use stable-6.x for ppc64le)'
    )
}
```

### Step 3: Test Configuration

```bash
# In COSA workspace
cosa init --branch rawhide https://github.com/coreos/fedora-coreos-config
cosa fetch
cosa build
cosa kola run ext.config.rpm-ostree.kernel-replace --arch ppc64le --qemu-memory 4096
```

## Verification Steps

After implementing fixes:

1. **Check dracut config**:
   ```bash
   lsinitrd | grep iscsi  # Should show no iSCSI modules
   ```

2. **Verify module loading**:
   ```bash
   systemctl status systemd-modules-load.service  # Should succeed
   ```

3. **Check BTF status**:
   ```bash
   ls -l /sys/kernel/btf/vmlinux  # Should exist
   bpftool btf dump file /sys/kernel/btf/vmlinux format c | head  # Should work
   ```

4. **Run kernel-replace test**:
   ```bash
   cosa kola run ext.config.rpm-ostree.kernel-replace --arch ppc64le
   ```

## Related Issues

- **Tracker**: https://github.com/coreos/fedora-coreos-tracker/issues/2115
- **Snooze Date**: 2026-07-03 (currently denylisted)
- **Architecture**: ppc64le specifically affected

## Additional Notes

### Why ppc64le is Affected

1. **Less testing coverage** on ppc64le compared to x86_64
2. **Compiler toolchain** may have architecture-specific BTF bugs
3. **Kernel 7.0.x RC** may have ppc64le-specific issues
4. **QEMU emulation** may expose timing or resource issues

### Test Requirements

From `tests/kola/rpm-ostree/kernel-replace`:
```yaml
timeoutMin: 30
minMemory: 4096  # Increased for ppc64le
minDisk: 20
tags: "needs-internet platform-independent"
```

The test is already configured with appropriate resources for ppc64le.

## Conclusion

The boot failure is caused by **BTF validation bugs** in the bleeding-edge toolchain (gcc 16.1.1 + kernel 7.0.x RC). The immediate fix is to **disable iSCSI in dracut** since it's not needed for local disk boot. The long-term solution is to use **stable kernel 6.x** and wait for **gcc 16.1.2+** with BTF fixes.

**Priority Actions**:
1. ✅ Implement Solution 1 (disable iSCSI) - **IMMEDIATE**
2. ✅ Implement Solution 3 (use stable kernel) - **SHORT-TERM**
3. ⏳ Monitor gcc 16.1.2+ release - **LONG-TERM**
4. ⏳ Wait for kernel 7.0 stable - **LONG-TERM**