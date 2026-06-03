# Jenkins Inbound Agent - Multi-Architecture Build

This directory contains the configuration for building multi-architecture Docker images for Jenkins inbound agents, supporting both **AMD64** and **PPC64LE** architectures.

## Overview

The build system creates customized Jenkins inbound agent images based on the official `jenkins/inbound-agent` base image, with additional tools and configurations for PowerVS and OpenShift environments.

### Supported Architectures
- **linux/amd64** (x86_64)
- **linux/ppc64le** (IBM POWER)

## Files

- **Dockerfile.multiarch** - Multi-architecture Dockerfile with build arguments
- **Jenkinsfile** - Jenkins pipeline for automated CI/CD builds
- **Makefile** - Local development and testing only (NOT used by Jenkins)
- **pvm.crt** - Certificate file for PowerVM environments
- **README.md** - This documentation

## Quick Start

### Prerequisites

1. **Container Runtime**:
   - **Docker with Buildx support** (Docker 19.03+), OR
   - **Podman** (version 3.0+)
2. **QEMU** (required for cross-platform builds on AMD64 hosts)
3. **Make** (optional, for local testing)
4. **Access to Docker registry** (for pushing images)

> **Note**: The Makefile automatically detects whether you're using Docker or Podman and adjusts commands accordingly.

### ⚠️ Important: QEMU Setup for Cross-Platform Builds

If you're building on an **AMD64 system** and want to build **PPC64LE** images, you MUST set up QEMU emulation first.

**Quick Setup (Recommended):**
```bash
# Run the setup script
chmod +x setup-qemu.sh
./setup-qemu.sh
```

**Manual Setup:**
```bash
# Method 1: Using container (works with both Docker and Podman)
sudo podman run --rm --privileged multiarch/qemu-user-static --reset -p yes

# Method 2: Install package
sudo dnf install qemu-user-static  # RHEL/CentOS/Fedora
# or
sudo apt-get install qemu-user-static  # Debian/Ubuntu

# Verify it works
podman run --rm --platform linux/ppc64le alpine uname -m
# Should output: ppc64le
```

**Without QEMU setup, you will get this error:**
```
exec container process `/bin/sh`: Exec format error
```

## Local Development & Testing

The Makefile is provided for **local development and testing only**. It is NOT used by the Jenkins pipeline.

### Using Make (Local Testing)

```bash
# Show available commands
make help

# Build multi-architecture image locally
make build

# Build specific architecture
make build-amd64
make build-ppc64le

# Test the built image
make test

# View build configuration
make info

# Clean up local images
make clean
```

### Using Docker Buildx Directly (Local Testing)

```bash
# Setup buildx
docker buildx create --name multiarch-builder --use
docker buildx inspect --bootstrap

# Build multi-arch image locally
docker buildx build \
  --platform linux/amd64,linux/ppc64le \
  --build-arg BASE_IMAGE=jenkins/inbound-agent:3355.v388858a_47b_33-21-jdk17 \
  -f Dockerfile.multiarch \
  -t quay.io/pgottimu/jenkins-inbound-agent:3355.v388858a_47b_33-21-jdk17 \
  --load \
  .

# Build and push to registry
docker buildx build \
  --platform linux/amd64,linux/ppc64le \
  --build-arg BASE_IMAGE=jenkins/inbound-agent:3355.v388858a_47b_33-21-jdk17 \
  -f Dockerfile.multiarch \
  -t quay.io/pgottimu/jenkins-inbound-agent:3355.v388858a_47b_33-21-jdk17 \
  --push \
  .
```

### Using Podman (Local Testing)

```bash
# Build AMD64 image (using Docker format to avoid SHELL warnings)
podman build \
  --format docker \
  --platform linux/amd64 \
  --build-arg BASE_IMAGE=jenkins/inbound-agent:3355.v388858a_47b_33-21-jdk17 \
  -f Dockerfile.multiarch \
  -t quay.io/pgottimu/jenkins-inbound-agent:3355.v388858a_47b_33-21-jdk17-amd64 \
  .

# Build PPC64LE image (using Docker format to avoid SHELL warnings)
podman build \
  --format docker \
  --platform linux/ppc64le \
  --build-arg BASE_IMAGE=jenkins/inbound-agent:3355.v388858a_47b_33-21-jdk17 \
  -f Dockerfile.multiarch \
  -t quay.io/pgottimu/jenkins-inbound-agent:3355.v388858a_47b_33-21-jdk17-ppc64le \
  .

# Push images
podman push quay.io/pgottimu/jenkins-inbound-agent:3355.v388858a_47b_33-21-jdk17-amd64
podman push quay.io/pgottimu/jenkins-inbound-agent:3355.v388858a_47b_33-21-jdk17-ppc64le

# Create and push manifest
podman manifest create quay.io/pgottimu/jenkins-inbound-agent:3355.v388858a_47b_33-21-jdk17 \
  quay.io/pgottimu/jenkins-inbound-agent:3355.v388858a_47b_33-21-jdk17-amd64 \
  quay.io/pgottimu/jenkins-inbound-agent:3355.v388858a_47b_33-21-jdk17-ppc64le

podman manifest push quay.io/pgottimu/jenkins-inbound-agent:3355.v388858a_47b_33-21-jdk17
```

## Jenkins Pipeline (CI/CD)

The Jenkins pipeline is completely independent of the Makefile and uses Docker Buildx directly.

### Pipeline Parameters

| Parameter | Description | Default |
|-----------|-------------|---------|
| BASE_IMAGE_TAG | Base image tag from jenkins/inbound-agent | `3355.v388858a_47b_33-21-jdk17` |
| DOCKER_REGISTRY | Docker registry URL | `quay.io` |
| DOCKER_REPO | Docker repository name | `pgottimu/jenkins-inbound-agent` |
| TAG_STRATEGY | Tag strategy (same-as-base, date-tag, custom) | `same-as-base` |
| CUSTOM_TAG | Custom tag (if TAG_STRATEGY is custom) | - |
| PUSH_TO_REGISTRY | Push images to registry | `false` |
| BUILD_LATEST | Also tag as "latest" | `false` |

### Tag Strategies

1. **same-as-base** - Use the same tag as the base image
   - Example: Base `3355.v388858a_47b_33-21-jdk17` → Output `3355.v388858a_47b_33-21-jdk17`

2. **date-tag** - Generate timestamp-based tag (YYYYMMDD-HHMM)
   - Example: `20260603-1430`

3. **custom** - Use custom tag specified in CUSTOM_TAG parameter
   - Example: `v1.0.0`, `production`, etc.

### Setting Up the Jenkins Job

1. Create a new **Pipeline** job in Jenkins
2. Configure the pipeline:
   - **Definition**: Pipeline script from SCM
   - **SCM**: Git
   - **Repository URL**: Your repository URL
   - **Script Path**: `images/jenkins-inbound-agent/Jenkinsfile`
3. Save the configuration
4. Run with parameters as needed

### Example Pipeline Runs

**Example 1: Build and test locally (no push)**
```
BASE_IMAGE_TAG: 3355.v388858a_47b_33-21-jdk17
DOCKER_REGISTRY: quay.io
DOCKER_REPO: pgottimu/jenkins-inbound-agent
TAG_STRATEGY: same-as-base
PUSH_TO_REGISTRY: false
BUILD_LATEST: false
```

**Example 2: Build and push with date tag**
```
BASE_IMAGE_TAG: latest-jdk17
DOCKER_REGISTRY: quay.io
DOCKER_REPO: pgottimu/jenkins-inbound-agent
TAG_STRATEGY: date-tag
PUSH_TO_REGISTRY: true
BUILD_LATEST: false
```

**Example 3: Build and push as latest**
```
BASE_IMAGE_TAG: 3355.v388858a_47b_33-21-jdk17
DOCKER_REGISTRY: quay.io
DOCKER_REPO: pgottimu/jenkins-inbound-agent
TAG_STRATEGY: same-as-base
PUSH_TO_REGISTRY: true
BUILD_LATEST: true
```

## Build Configuration

### Dockerfile Build Arguments

The Dockerfile accepts the following build argument:

- **BASE_IMAGE** - Base Jenkins inbound agent image
  - Default: `jenkins/inbound-agent:3355.v388858a_47b_33-21-jdk17`
  - Can be overridden during build

### Makefile Variables (Local Testing Only)

For local development, customize builds using these variables:

```bash
# Base image configuration
BASE_IMAGE_TAG=3355.v388858a_47b_33-21-jdk17  # Base image tag
BASE_IMAGE_REPO=jenkins/inbound-agent          # Base image repository

# Destination image configuration
DOCKER_REGISTRY=quay.io                        # Registry URL
DOCKER_REPO=pgottimu/jenkins-inbound-agent     # Repository name
IMAGE_TAG=3355.v388858a_47b_33-21-jdk17        # Output tag
```

Example:
```bash
# Build with different base image
make build BASE_IMAGE_TAG=latest-jdk17

# Push to different registry
make push \
  DOCKER_REGISTRY=docker.io \
  DOCKER_REPO=myorg/jenkins-agent \
  IMAGE_TAG=custom-v1.0
```

## Image Contents

### Installed Software

The image includes the following additional software:

#### Common Tools
- Python 3 with pip and venv
- Ansible
- yq (YAML processor)
- OpenShift CLI (oc/kubectl)
- IBM Cloud CLI with plugins:
  - cloud-object-storage
  - container-service (amd64 only)
  - power-iaas
- nerdctl (container CLI)
- Git, curl, wget, rsync
- jq, unzip, make

#### Architecture-Specific
- **AMD64**: Google Chrome
- **PPC64LE**: Chromium browser, RPM

### Environment Variables

- `PATH` - Includes Python venv and Go binaries
- `CHROME_BIN` - Browser executable path (architecture-specific)
  - AMD64: `/usr/bin/google-chrome`
  - PPC64LE: `/usr/bin/chromium-browser`

### Customizations

- SSL certificate support (pvm.crt)
- MTU configuration for eth0 (1420)
- SSH key generation
- wget certificate checking disabled

## Usage Examples

### Pull the Image

```bash
# Pull multi-arch image (automatically selects correct architecture)
docker pull quay.io/pgottimu/jenkins-inbound-agent:3355.v388858a_47b_33-21-jdk17

# Pull specific architecture
docker pull quay.io/pgottimu/jenkins-inbound-agent:3355.v388858a_47b_33-21-jdk17-amd64
docker pull quay.io/pgottimu/jenkins-inbound-agent:3355.v388858a_47b_33-21-jdk17-ppc64le
```

### Run the Agent

```bash
# Run as Jenkins agent
docker run -d \
  --name jenkins-agent \
  -e JENKINS_URL=http://jenkins-server:8080 \
  -e JENKINS_SECRET=<agent-secret> \
  -e JENKINS_AGENT_NAME=<agent-name> \
  quay.io/pgottimu/jenkins-inbound-agent:3355.v388858a_47b_33-21-jdk17
```

### Test the Image

```bash
# Test installed tools
docker run --rm quay.io/pgottimu/jenkins-inbound-agent:3355.v388858a_47b_33-21-jdk17 java -version
docker run --rm quay.io/pgottimu/jenkins-inbound-agent:3355.v388858a_47b_33-21-jdk17 python3 --version
docker run --rm quay.io/pgottimu/jenkins-inbound-agent:3355.v388858a_47b_33-21-jdk17 ansible --version
docker run --rm quay.io/pgottimu/jenkins-inbound-agent:3355.v388858a_47b_33-21-jdk17 oc version
docker run --rm quay.io/pgottimu/jenkins-inbound-agent:3355.v388858a_47b_33-21-jdk17 ibmcloud --version
```

## Troubleshooting

### Build Issues

**Problem**: Buildx not available
```bash
# Solution: Enable Docker buildx
docker buildx version
# If not available, update Docker to 19.03+
```

**Problem**: Platform not supported
```bash
# Solution: Ensure QEMU is installed for cross-platform builds
docker run --privileged --rm tonistiigi/binfmt --install all
```

**Problem**: Certificate errors during build
```bash
# Solution: Ensure pvm.crt is present in the directory
# Or update Dockerfile to handle missing cert gracefully
```

### Push Issues

**Problem**: Authentication required
```bash
# Solution: Login to registry
docker login quay.io
# Enter username and password when prompted
```

**Problem**: Permission denied
```bash
# Solution: Ensure you have push access to the repository
# Contact repository administrator or use your own repository
```

### Podman-Specific Issues

**Problem**: `Error: unrecognized command 'podman buildx use'`
```bash
# Solution: This is expected - Podman doesn't use buildx
# The Makefile will automatically use Podman's native multi-arch support
# Just run: make build
```

**Problem**: Podman build fails with platform error
```bash
# Solution: Ensure QEMU is installed for cross-platform builds
sudo dnf install qemu-user-static  # For RHEL/CentOS/Fedora
# or
sudo apt-get install qemu-user-static  # For Debian/Ubuntu
```

**Problem**: Manifest creation fails
```bash
# Solution: Ensure images are pushed before creating manifest
podman push <image-amd64>
podman push <image-ppc64le>
# Then create manifest
```

### Jenkins Pipeline Issues

**Problem**: Pipeline fails at buildx setup
```bash
# Solution: Ensure Jenkins agent has Docker with buildx support
# Update Docker on Jenkins agent to version 19.03+
```

**Problem**: Multi-arch build fails
```bash
# Solution: Ensure QEMU is installed on Jenkins agent
docker run --privileged --rm tonistiigi/binfmt --install all
```

## Maintenance

### Updating Base Image

To update to a newer base image version:

**Via Jenkins Pipeline:**
1. Run the pipeline with updated `BASE_IMAGE_TAG` parameter
2. Test the new image
3. Push to registry if tests pass

**Via Local Testing:**
```bash
# Test new version locally first
make build BASE_IMAGE_TAG=latest-jdk17
make test

# If tests pass, push via Jenkins or manually
make push BASE_IMAGE_TAG=latest-jdk17
```

### Updating Dependencies

To update installed software versions:

1. Edit `Dockerfile.multiarch`
2. Update version numbers or package names
3. Test locally: `make build && make test`
4. Commit changes to repository
5. Run Jenkins pipeline to build and push

## CI/CD Integration

### GitHub Actions Example

```yaml
name: Build Multi-Arch Image

on:
  push:
    branches: [ main ]
  workflow_dispatch:

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      
      - name: Set up Docker Buildx
        uses: docker/setup-buildx-action@v2
      
      - name: Login to Quay.io
        uses: docker/login-action@v2
        with:
          registry: quay.io
          username: ${{ secrets.QUAY_USERNAME }}
          password: ${{ secrets.QUAY_PASSWORD }}
      
      - name: Build and Push
        working-directory: images/jenkins-inbound-agent
        run: |
          docker buildx build \
            --platform linux/amd64,linux/ppc64le \
            --build-arg BASE_IMAGE=jenkins/inbound-agent:3355.v388858a_47b_33-21-jdk17 \
            -f Dockerfile.multiarch \
            -t quay.io/pgottimu/jenkins-inbound-agent:3355.v388858a_47b_33-21-jdk17 \
            --push \
            .
```

## Architecture Differences

### AMD64 vs PPC64LE

| Component | AMD64 | PPC64LE |
|-----------|-------|---------|
| Browser | Google Chrome | Chromium |
| IBM Cloud CLI | Full plugins | Limited plugins (no container-service) |
| OpenShift CLI | amd64 binary | ppc64le binary |
| nerdctl | amd64 binary | ppc64le binary |

## Contributing

When contributing changes:

1. Test builds locally using `make build`
2. Verify both architectures work: `make build-all`
3. Run tests: `make test`
4. Update documentation if needed
5. Submit pull request

## License

This project follows the same license as the Jenkins project.

## Support

For issues or questions:
- Open an issue in the repository
- Contact the infrastructure team
- Check Jenkins documentation: https://www.jenkins.io/doc/

## References

- [Jenkins Inbound Agent Documentation](https://github.com/jenkinsci/docker-inbound-agent)
- [Docker Buildx Documentation](https://docs.docker.com/buildx/working-with-buildx/)
- [Multi-platform Images](https://docs.docker.com/build/building/multi-platform/)
- [IBM Cloud CLI](https://cloud.ibm.com/docs/cli)
- [OpenShift CLI](https://docs.openshift.com/container-platform/latest/cli_reference/openshift_cli/getting-started-cli.html)