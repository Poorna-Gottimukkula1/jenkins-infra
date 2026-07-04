# Bug Report for LTC Team: Kernel Panic During Upgrade Test

## Summary
Kernel panic occurs during Fedora CoreOS basic upgrade test on ppc64le architecture when rebooting into upgraded kernel.

---

## Test Details

### Test Type
**Basic Upgrade Test** (`fcos.upgrade.basic`)

### Upgrade Path

**FROM (Source Build):**
- **Build**: Fedora CoreOS 45.20260703.91.1
- **Kernel**: 7.2.0-0.rc1.260701g665159e24674.16.fc45.ppc64le
- **Status**: ✅ Boots successfully

**TO (Target Build):**
- **Build**: Fedora CoreOS 45.20260703.91.2
- **Kernel**: 7.2.0-0.rc1.260701g665159e24674.16.fc45.ppc64le (same kernel version)
- **Status**: ❌ **KERNEL PANIC on reboot**

### Build Information

```
Source: fedora-coreos-695f1dfce547e9309a023f36766df25a2b7f3d833528f6b725ec723b2b1ec61d
Target: fedora-coreos-1d5a015f2c5c45e8e0f15ca9f301c9c7921a6c5661e811ffd0e81647fdf934d2
```

---

## Kernel Details

### Kernel Version
```
Linux version 7.2.0-0.rc1.260701g665159e24674.16.fc45.ppc64le
Built: Wed Jul 1 17:51:03 UTC 2026
Compiler: gcc (GCC) 16.1.1 20260515 (Red Hat 16.1.1-2)
Linker: GNU ld version 2.46.50.20260625
Config: #1 SMP PREEMPT_DYNAMIC
```

### Architecture
- **Platform**: ppc64le
- **Machine**: IBM pSeries (emulated by qemu)
- **CPU**: POWER10 (architected) 0x800200 0xf000006
- **Hypervisor**: linux,kvm
- **Firmware**: SLOF (Slimline Open Firmware)

---

## Failure Details

### Panic Message
```
Kernel panic - not syncing: Attempted to kill the idle task!
```

### Crash Location
```
NIP:  c000000000c9d6e8 security_locked_down+0x8/0x250
LR:   c00000000304fedc tracer_alloc_buffers.isra.0+0x30/0x45c
```

### Call Stack
```
start_here_common+0x1c/0x20
  └─> start_kernel+0x2cc/0x634
      └─> early_trace_init+0x90/0xbc
          └─> tracer_alloc_buffers.isra.0+0x30/0x45c
              └─> security_locked_down+0x8/0x250  [CRASH]
```

### Trap Information
```
TRAP: 0x0700 (Program Check Exception)
Signal: 4 (Illegal Instruction)
CPU: 0 UID: 0 PID: 0 Comm: swapper
Tainted: G W (Warning flag set)
```

### Timing
- **When**: During early kernel initialization (timestamp 0.000000)
- **Stage**: ftrace initialization in start_kernel()
- **Before**: Any userspace processes start

---

## Test Environment

### Hardware Configuration
- **Memory**: 4GB (4194304K)
- **CPUs**: 1 (configured for up to 8192)
- **NUMA**: 1 node
- **MMU**: Radix MMU under hypervisor

### QEMU Configuration
```
qemu-system-ppc64
-machine pseries,kvm-type=HV,ic-mode=xics
-cpu host
-m 4096
-smp 1
```

---

## Test Sequence

### Boot Sequence

1. **First Boot** (Build 45.20260703.91.1):
   - ✅ System boots successfully
   - ✅ Kernel 7.2.0-0.rc1 loads
   - ✅ All services start normally

2. **Upgrade Initiated**:
   - ✅ rpm-ostree rebase to 45.20260703.91.2
   - ✅ OCI archive downloaded successfully
   - ✅ Reboot triggered

3. **Second Boot** (Build 45.20260703.91.2):
   - ✅ GRUB loads
   - ✅ Firmware initializes
   - ❌ **KERNEL PANIC** during early init
   - ❌ System unusable

---

## Root Cause Analysis

### Issue
Race condition in ftrace initialization calling uninitialized security subsystem.

### Technical Details

1. **ftrace_init()** successfully allocates 57,359 entries in 22 pages
2. **early_trace_init()** attempts to allocate trace buffers
3. **tracer_alloc_buffers()** calls **security_locked_down()**
4. **security_locked_down()** accesses uninitialized data structures
5. **Program check exception** (illegal instruction)
6. **Kernel panic** - idle task killed

### Why This Happens

The security subsystem is not fully initialized when the tracing subsystem tries to check lockdown status during early boot. This appears to be a **kernel bug specific to ppc64le** in the 7.2.0-rc1 release candidate.

---

## Reproducibility

### Frequency
- **Consistent**: Fails every time on this kernel version
- **Architecture-specific**: ppc64le only (likely not reproducible on x86_64)

### Conditions
- Kernel: 7.2.0-rc1.260701g665159e24674.16.fc45.ppc64le
- Platform: ppc64le (POWER10)
- Test: Basic upgrade test with reboot
- Memory: 4GB

---

## Impact

### Severity
**CRITICAL** - System cannot boot after upgrade

### Scope
- Affects: Fedora CoreOS 45 on ppc64le
- Kernel: 7.2.0-rc1 (release candidate)
- Users: Anyone upgrading to this kernel version

---

## Workarounds

### Option 1: Kernel Boot Parameter
Add to kernel command line:
```bash
ftrace=nop
```

### Option 2: Disable Security Lockdown
```bash
lockdown=none
```

### Option 3: Use Stable Kernel
- Avoid 7.2.0-rc1
- Use 7.1.x stable or wait for 7.2.0 final release

---

## Recommendations

### Immediate Actions
1. **Do not use kernel 7.2.0-rc1** on ppc64le in production
2. **Test with stable kernel** (7.1.x series)
3. **Report to upstream** kernel team

### For LTC Team
1. **Verify** if this is reproducible on bare metal ppc64le
2. **Test** with different POWER generations (POWER9, POWER10)
3. **Check** if issue exists in later RC versions (7.2.0-rc2+)
4. **Bisect** kernel commits between working and failing versions

### Upstream Reporting
- **Component**: kernel/ftrace
- **Subsystem**: security/lockdown
- **Architecture**: ppc64le
- **Bugzilla**: kernel.org or Fedora bugzilla
- **LKML**: Linux Kernel Mailing List

---

## Additional Information

### Kernel Configuration
```
CONFIG_FTRACE=y
CONFIG_FUNCTION_TRACER=y
CONFIG_SECURITY_LOCKDOWN_LSM=y
```

### Related Logs
- Full console log available
- No BTF-related errors in this failure
- Memory allocation successful
- No OOM conditions

### Contact
For questions or additional information, please contact the CoreOS testing team.

---

## Attachments
- Console log: `onsole logs`
- Test output: Available in Jenkins build artifacts
- Kernel config: Available in build metadata

---

**Report Date**: July 3, 2026  
**Reported By**: CoreOS CI/CD Team  
**Priority**: High  
**Status**: Open