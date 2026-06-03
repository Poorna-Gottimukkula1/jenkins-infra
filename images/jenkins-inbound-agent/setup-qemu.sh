#!/bin/bash
# Setup QEMU for cross-platform builds with Podman/Docker

set -e

echo "========================================="
echo "QEMU Setup for Multi-Architecture Builds"
echo "========================================="

# Detect if running as root
if [ "$EUID" -ne 0 ]; then 
    SUDO="sudo"
else
    SUDO=""
fi

# Detect container runtime
if command -v docker &> /dev/null; then
    RUNTIME="docker"
elif command -v podman &> /dev/null; then
    RUNTIME="podman"
else
    echo "Error: Neither docker nor podman found"
    exit 1
fi

echo "Detected runtime: $RUNTIME"
echo ""

# Method 1: Try using multiarch/qemu-user-static container
echo "Method 1: Setting up QEMU using multiarch/qemu-user-static..."
if $SUDO $RUNTIME run --rm --privileged multiarch/qemu-user-static --reset -p yes; then
    echo "✅ QEMU setup successful using container method"
    echo ""
    echo "Testing cross-platform support..."
    if $RUNTIME run --rm --platform linux/ppc64le alpine uname -m; then
        echo "✅ Cross-platform builds are working!"
        exit 0
    fi
fi

echo ""
echo "Method 2: Installing qemu-user-static package..."

# Detect OS and install qemu-user-static
if [ -f /etc/redhat-release ]; then
    # RHEL/CentOS/Fedora
    echo "Detected RHEL-based system"
    $SUDO dnf install -y qemu-user-static
elif [ -f /etc/debian_version ]; then
    # Debian/Ubuntu
    echo "Detected Debian-based system"
    $SUDO apt-get update
    $SUDO apt-get install -y qemu-user-static
else
    echo "Unknown OS. Please install qemu-user-static manually."
    exit 1
fi

echo ""
echo "Verifying QEMU installation..."
if [ -f /proc/sys/fs/binfmt_misc/qemu-ppc64le ]; then
    echo "✅ QEMU for PPC64LE is registered"
else
    echo "⚠️  QEMU for PPC64LE not found in binfmt_misc"
    echo "Trying to register..."
    $SUDO systemctl restart systemd-binfmt.service 2>/dev/null || true
fi

echo ""
echo "Testing cross-platform support..."
if $RUNTIME run --rm --platform linux/ppc64le alpine uname -m; then
    echo "✅ Cross-platform builds are working!"
else
    echo "❌ Cross-platform builds still not working"
    echo ""
    echo "Manual steps to try:"
    echo "1. Restart systemd-binfmt service:"
    echo "   sudo systemctl restart systemd-binfmt.service"
    echo ""
    echo "2. Or manually register QEMU:"
    echo "   sudo $RUNTIME run --rm --privileged multiarch/qemu-user-static --reset -p yes"
    exit 1
fi

echo ""
echo "========================================="
echo "Setup complete! You can now build multi-architecture images."
echo "========================================="

# Made with Bob
