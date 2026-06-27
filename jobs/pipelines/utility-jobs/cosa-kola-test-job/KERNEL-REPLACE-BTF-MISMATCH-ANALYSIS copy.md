# Kernel Replace Test BTF Mismatch Analysis - ppc64le

## Executive Summary

The `ext.config.rpm-ostree.kernel-replace` test **succeeds on first boot** but **fails on second boot** after rebasing to a derived image with a replaced kernel. The failure is caused by **BTF (BPF Type Format) incompatibility** between the old kernel's modules (in initramfs) and the new kernel.

## Test Flow and Failure Point

### ✅ First Boot - SUCCESS
```
Kernel: 7.0.0-0.rc1.15.fc45.ppc64le (original)
Modules: Built for 7.0.0-0.rc1.15.fc45.ppc64le
BTF: Kernel and modules match
Result: System boots successfully
```

**Evidence from log**:
```
Fedora CoreOS 45.20260225.91.1
Kernel 7.0.0-0.rc1.15.fc45.ppc64le on ppc64le (hvc0)
...
qemu0 login: [successful login prompt]
[  118.528760] watchdog: watchdog0: watchdog did not stop!
[  119.081685] reboot: Restarting system
```

### ❌ Second Boot - FAILURE (After Kernel Replace)
```
Kernel: 7.0.13-200.fc44.ppc64le (NEW - replaced kernel)
Modules: Still from 7.0.0-0.rc1.15.fc45.ppc64le (OLD - in initramfs)
BTF: MISMATCH between kernel and modules
Result: BTF validation fails → modules can't load → boot fails
```

**Evidence from log**:
```
[    0.000000] Linux version 7.0.13-200.fc44.ppc64le (mockbuild@7c74367eab3e48f892079208e14d875c)
...
[    1.285406] failed to validate module [nfnetlink] BTF: -22
[    4.881171] failed to validate module [scsi_transport_iscsi] BTF: -22
...
[    4.902194] dracut: FATAL: iscsiroot requested but kernel/initrd does not support iscsi
[    4.902372] dracut: Refusing to continue
```

## Root Cause Analysis

### The Problem: Initramfs Not Rebuilt

When the kernel-replace test creates a derived image:

1. **Base image** contains:
   - Kernel: `7.0.0-0.rc1.15.fc45.ppc64le`
   - Initramfs: Built with modules for `7.0.0-0.rc1.15.fc45.ppc64le`
   - BTF info: Matches kernel `7.0.0-0.rc1.15.fc45`

2. **Derived image** (after `rpm-ostree override replace`):
   - Kernel: `7.0.13-200.fc44.ppc64le` (NEW)
   - Initramfs: **STILL has old modules** for `7.0.0-0.rc1.15.fc45.ppc64le` (OLD)
   - BTF info: **MISMATCH** - modules have BTF for fc45, kernel expects BTF for fc44

3. **On boot**:
   - New kernel `7.0.13-200.fc44` loads
   - Tries to load modules from initramfs
   - Modules have BTF metadata for `7.0.0-0.rc1.15.fc45`
   - Kernel's BTF verifier rejects them: **BTF validation error -22 (EINVAL)**
   - Critical modules fail: `scsi_transport_iscsi`, `nfnetlink`, etc.
   - Dracut can't find iSCSI support → FATAL error → system powers off

### Why BTF Validation Fails

**BTF (BPF Type Format)** contains:
- Kernel data structure layouts
- Function signatures
- Type information for BPF programs

**Between kernel versions**, BTF can change:
- Different compiler versions (gcc 16.1.1 vs others)
- Different kernel configs
- Different structure layouts
- Different type IDs

When kernel `7.0.13-200.fc44` tries to load a module built for `7.0.0-0.rc1.15.fc45`:
```
Module BTF: "This is type ID 1234 for struct foo"
Kernel BTF: "I don't have type ID 1234, or struct foo is different"
Result: EINVAL (-22) - Invalid argument
```

## Test Stages Breakdown

### Stage 0: Build Images
```bash
# From tests/kola/rpm-ostree/kernel-replace script
build_base_image()      # Encapsulate current deployment (7.0.0-0.rc1.15.fc45)
build_derived_image()   # Download kernel 7.0.13-200.fc44 and create derived image
```

**What happens in `build_derived_image()`**:
```dockerfile
FROM localhost/coreos-base:latest
RUN rpm-ostree override replace /tmp/buildcontext/*rpm && \
    rpm-ostree cleanup -m && \
    ostree container commit
```

**The Issue**: `rpm-ostree override replace` replaces the kernel RPMs but **does NOT rebuild the initramfs** with the new kernel's modules!

### Stage 1: Rebase to Derived Image
```bash
rpm-ostree rebase "ostree-unverified-image:$derived_imagespec"
/tmp/autopkgtest-reboot 1
```

**What happens**:
- System prepares new deployment with kernel `7.0.13-200.fc44`
- Initramfs is copied from base image (still has old modules)
- System reboots

### Stage 2: Boot Failure
```
GRUB loads kernel 7.0.13-200.fc44
Kernel starts
Initramfs unpacks (contains modules for 7.0.0-0.rc1.15.fc45)
Kernel tries to load modules
BTF validation fails
Modules can't load
Dracut fails
System powers off
```

## Why This Affects ppc64le Specifically

1. **Less testing coverage** - x86_64 may have workarounds or different module loading
2. **Compiler differences** - ppc64le may use different gcc optimization flags
3. **BTF generation** - ppc64le-specific BTF bugs in gcc 16.1.1
4. **Module dependencies** - ppc64le may require more modules in initramfs

## Solutions

### Solution 1: Rebuild Initramfs After Kernel Replace (CORRECT FIX)

**Location**: `fedora-coreos-config/tests/kola/rpm-ostree/kernel-replace`

Modify the Containerfile to rebuild initramfs:

```dockerfile
FROM $baseimage
# Disable yum repos
RUN ls /etc/yum.repos.d/*.repo 2>/dev/null | xargs --no-run-if-empty sed -i s/enabled=1/enabled=0/

# Replace kernel
RUN rpm-ostree override replace /tmp/buildcontext/*rpm && \
    rpm-ostree cleanup -m

# CRITICAL: Rebuild initramfs with new kernel modules
RUN rpm-ostree initramfs --enable --arg=--rebuild && \
    ostree container commit
```

**Why this works**:
- `rpm-ostree initramfs --enable --arg=--rebuild` forces initramfs regeneration
- New initramfs will contain modules built for the new kernel
- BTF information will match between kernel and modules

### Solution 2: Disable BTF Validation (WORKAROUND)

**Location**: `fedora-coreos-config/image-base.yaml`

Add kernel parameter to disable BTF:

```yaml
extra-kargs:
    - mitigations=auto,nosmt
    - bpf.btf_enforce=0  # Disable BTF enforcement
```

**Pros**: Test can proceed
**Cons**: Loses BPF functionality, not a real fix

### Solution 3: Use Matching Kernel Versions (WORKAROUND)

**Location**: `fedora-coreos-config/tests/kola/rpm-ostree/kernel-replace`

Modify to download kernel from same Fedora version:

```bash
# Instead of downloading from previous version (fc44)
# Download from same version (fc45) but different build
VERSION_ID=$(. /etc/os-release; echo $VERSION_ID)
# Use same version, just different build number
dnf download --releasever "${VERSION_ID}" \
  --resolve --disablerepo=* --enablerepo=updates --enablerepo=fedora kernel
```

**Why this helps**: Reduces BTF incompatibility between versions

### Solution 4: Disable iSCSI in Dracut (PARTIAL FIX)

**Location**: `fedora-coreos-config/overlay.d/`

```bash
mkdir -p overlay.d/99disable-iscsi/usr/lib/dracut/dracut.conf.d/
cat > overlay.d/99disable-iscsi/usr/lib/dracut/dracut.conf.d/99-no-iscsi.conf << 'EOF'
omit_dracutmodules+=" iscsi "
EOF
```

**Why this helps**: Removes the immediate failure (iSCSI module), but other modules will still fail

## Recommended Fix Implementation

### Primary Fix: Rebuild Initramfs

**File**: `fedora-coreos-config/tests/kola/rpm-ostree/kernel-replace`

**Change in `build_derived_image()` function** (around line 187):

```bash
echo "--- Creating Containerfile ---"
cat > Containerfile << EOF
FROM $baseimage
# Disable yum repos since we are overriding local files and we don't
# want to reach out to the repos. This is done in a way such that if
# there are no repo files (i.e. like on RHCOS) then it succeeds anyway.
RUN ls /etc/yum.repos.d/*.repo 2>/dev/null | xargs --no-run-if-empty sed -i s/enabled=1/enabled=0/

# Replace kernel RPMs
RUN rpm-ostree override replace /tmp/buildcontext/*rpm && \
    rpm-ostree cleanup -m

# CRITICAL FIX: Rebuild initramfs with new kernel modules
# This ensures BTF information matches between kernel and modules
RUN rpm-ostree initramfs --enable --arg=--rebuild && \
    rpm-ostree cleanup -m && \
    ostree container commit
EOF
```

### Verification Steps

After implementing the fix:

1. **Check initramfs was rebuilt**:
   ```bash
   # In the derived image
   rpm-ostree status --json | jq '.deployments[0].initramfs'
   # Should show initramfs is enabled
   ```

2. **Verify module BTF matches kernel**:
   ```bash
   # After booting new kernel
   modinfo -F vermagic scsi_transport_iscsi
   # Should match: 7.0.13-200.fc44.ppc64le
   
   uname -r
   # Should match: 7.0.13-200.fc44.ppc64le
   ```

3. **Check BTF validation**:
   ```bash
   # Should not see BTF errors in dmesg
   dmesg | grep -i "btf"
   ```

4. **Run the test**:
   ```bash
   cosa kola run ext.config.rpm-ostree.kernel-replace --arch ppc64le --qemu-memory 4096
   ```

## Additional Improvements

### Add Diagnostic Output

**File**: `fedora-coreos-config/tests/kola/rpm-ostree/kernel-replace`

Add after kernel replacement (around line 295):

```bash
echo ""
echo "=== Verifying initramfs rebuild ==="
rpm-ostree status --json | jq -r '.deployments[0] | {
  version: .version,
  checksum: .checksum,
  initramfs: .initramfs,
  "base-checksum": ."base-checksum"
}'

echo ""
echo "=== Checking kernel module versions in derived image ==="
podman run --rm "${derived_imagespec}" \
  rpm -q kernel kernel-core kernel-modules
```

### Update Test Documentation

**File**: `fedora-coreos-config/tests/kola/rpm-ostree/kernel-replace`

Update the kola metadata (lines 2-15):

```yaml
## kola:
##   timeoutMin: 30
##   minMemory: 4096
##   minDisk: 20
##   tags: "needs-internet platform-independent"
##   description: Verify that build of a container image with a new kernel
##     and reboot into it succeeds. Tests kernel replacement via rpm-ostree
##     override replace and verifies that initramfs is properly rebuilt with
##     new kernel modules to avoid BTF validation errors.
##   # Known issue on ppc64le: BTF validation fails if initramfs not rebuilt
##   # See: https://github.com/coreos/fedora-coreos-tracker/issues/2115
```

## Related Issues and References

- **GitHub Issue**: https://github.com/coreos/fedora-coreos-tracker/issues/2115
- **Denylist Entry**: `kola-denylist.yaml` line 11-16 (ppc64le)
- **Snooze Date**: 2026-07-03

## Conclusion

The kernel-replace test fails on ppc64le because:

1. ✅ **First boot succeeds** - kernel and modules match
2. ❌ **Second boot fails** - new kernel, old modules in initramfs
3. 🔧 **Fix**: Rebuild initramfs after kernel replacement using `rpm-ostree initramfs --enable --arg=--rebuild`

The BTF validation errors are **correct behavior** - the kernel is properly rejecting modules with incompatible BTF metadata. The bug is in the test not rebuilding the initramfs after replacing the kernel.

**Priority**: HIGH - This is a test bug, not a product bug. The fix is simple and should be implemented immediately.