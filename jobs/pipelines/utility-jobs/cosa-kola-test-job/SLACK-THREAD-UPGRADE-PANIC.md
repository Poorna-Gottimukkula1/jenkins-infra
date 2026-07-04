# Slack Thread: Kernel Panic in Upgrade Test

## Initial Message

🚨 **Intermittent Kernel Panic in Fedora CoreOS Upgrade Test (ppc64le)**

We're seeing intermittent kernel panics during basic upgrade tests on ppc64le. The system crashes on reboot after upgrade.

**Test**: `fcos.upgrade.basic`  
**Architecture**: ppc64le (POWER10)  
**Reproducibility**: Intermittent (not every run)

---

## Thread Reply 1: Build Details

**Upgrade Path:**
```
FROM: Fedora CoreOS 45.20260703.91.1
      Kernel: 7.2.0-0.rc1.260701g665159e24674.16.fc45.ppc64le
      Status: ✅ Boots fine

TO:   Fedora CoreOS 45.20260703.91.2
      Kernel: 7.2.0-0.rc1.260701g665159e24674.16.fc45.ppc64le (same version)
      Status: ❌ KERNEL PANIC on reboot (intermittent)
```

Note: Same kernel version in both builds, so not a kernel upgrade issue.

---

## Thread Reply 2: Panic Details

**Crash Location:**
```
Kernel panic - not syncing: Attempted to kill the idle task!

NIP: c000000000c9d6e8 security_locked_down+0x8/0x250
LR:  c00000000304fedc tracer_alloc_buffers.isra.0+0x30/0x45c
```

**Call Stack:**
```
start_kernel+0x2cc
  └─> early_trace_init+0x90
      └─> tracer_alloc_buffers+0x30
          └─> security_locked_down+0x8  [CRASH]
```

**When**: Very early boot (timestamp 0.000000) during ftrace initialization

---

## Thread Reply 3: Root Cause

**Issue**: Race condition in kernel 7.2.0-rc1 on ppc64le

The ftrace subsystem tries to check security lockdown status before the security subsystem is fully initialized. This causes a program check exception (illegal instruction) that kills the idle task.

**Why intermittent**: Timing-dependent race condition - sometimes security init completes before ftrace checks it, sometimes not.

**Why ppc64le only**: Architecture-specific initialization order issue in this RC kernel.

---

## Thread Reply 4: Workarounds

**Option 1** - Kernel parameter (quick fix):
```bash
ftrace=nop
```

**Option 2** - Use stable kernel:
- Avoid 7.2.0-rc1 (release candidate)
- Use 7.1.x stable series
- Wait for 7.2.0 final release

**Option 3** - Disable lockdown:
```bash
lockdown=none
```

---

## Thread Reply 5: Action Items

**Immediate:**
- [ ] Report to Fedora kernel team
- [ ] Test with 7.2.0-rc2 (if available)
- [ ] Verify on bare metal ppc64le
- [ ] Run multiple iterations to determine failure rate

**For Testing:**
- [ ] Add retry logic for this kernel version
- [ ] Use stable kernel for CI/CD
- [ ] Monitor for kernel updates
- [ ] Track failure rate over time

**Upstream:**
- [ ] File bug in Fedora Bugzilla
- [ ] Report to LKML (Linux Kernel Mailing List)
- [ ] Tag kernel/ftrace and security maintainers

---

## Thread Reply 6: Additional Info

**Full details**: See `LTC-BUG-REPORT.md` for complete technical analysis

**Console logs**: Available in Jenkins artifacts

**Test environment**:
- QEMU/KVM on pSeries
- 4GB RAM
- POWER10 (architected)

**Priority**: High - intermittently blocks upgrade testing on ppc64le

---

## Quick Copy-Paste for Slack

```
🚨 Intermittent Kernel Panic: Fedora CoreOS Upgrade Test (ppc64le)

Test: fcos.upgrade.basic
From: 45.20260703.91.1 (kernel 7.2.0-rc1)
To: 45.20260703.91.2 (kernel 7.2.0-rc1)
Result: ❌ KERNEL PANIC on reboot (intermittent)

Crash: security_locked_down() in ftrace init
Cause: Race condition in 7.2.0-rc1 (ppc64le only)
Workaround: Add kernel param `ftrace=nop`

Full report: LTC-BUG-REPORT.md
Priority: High