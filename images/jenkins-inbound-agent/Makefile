# Makefile for building multi-architecture jenkins-inbound-agent images
# Supports: amd64, ppc64le
# Compatible with both Docker and Podman

# Configuration variables
BASE_IMAGE_TAG ?= 3355.v388858a_47b_33-21-jdk17
BASE_IMAGE_REPO ?= jenkins/inbound-agent
BASE_IMAGE = $(BASE_IMAGE_REPO):$(BASE_IMAGE_TAG)

DOCKER_REGISTRY ?= quay.io
DOCKER_REPO ?= pgottimu/jenkins-inbound-agent
IMAGE_TAG ?= $(BASE_IMAGE_TAG)

FULL_IMAGE_NAME = $(DOCKER_REGISTRY)/$(DOCKER_REPO):$(IMAGE_TAG)
FULL_IMAGE_NAME_AMD64 = $(DOCKER_REGISTRY)/$(DOCKER_REPO):$(IMAGE_TAG)-amd64
FULL_IMAGE_NAME_PPC64LE = $(DOCKER_REGISTRY)/$(DOCKER_REPO):$(IMAGE_TAG)-ppc64le
FULL_IMAGE_NAME_LATEST = $(DOCKER_REGISTRY)/$(DOCKER_REPO):latest

DOCKERFILE = Dockerfile.multiarch

# Docker buildx builder name
BUILDER_NAME = multiarch-builder

# Detect container runtime (docker or podman)
CONTAINER_RUNTIME := $(shell command -v docker 2>/dev/null)
ifdef CONTAINER_RUNTIME
    ifeq ($(shell docker --version 2>/dev/null | grep -q podman && echo podman),podman)
        CONTAINER_RUNTIME = podman
        USE_PODMAN = true
    else
        CONTAINER_RUNTIME = docker
        USE_PODMAN = false
    endif
else
    CONTAINER_RUNTIME := $(shell command -v podman 2>/dev/null)
    ifdef CONTAINER_RUNTIME
        USE_PODMAN = true
    else
        $(error Neither docker nor podman found in PATH)
    endif
endif

.PHONY: help
help: ## Show this help message
	@echo "Multi-Architecture Jenkins Inbound Agent Build System"
	@echo ""
	@echo "Usage: make [target] [VARIABLE=value]"
	@echo ""
	@echo "Variables:"
	@echo "  BASE_IMAGE_TAG       Base image tag (default: $(BASE_IMAGE_TAG))"
	@echo "  DOCKER_REGISTRY      Registry URL (default: $(DOCKER_REGISTRY))"
	@echo "  DOCKER_REPO          Repository name (default: $(DOCKER_REPO))"
	@echo "  IMAGE_TAG            Output image tag (default: $(IMAGE_TAG))"
	@echo ""
	@echo "Targets:"
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | sort | awk 'BEGIN {FS = ":.*?## "}; {printf "  %-20s %s\n", $$1, $$2}'

.PHONY: setup
setup: ## Setup container runtime for multi-architecture builds
	@echo "Detected container runtime: $(CONTAINER_RUNTIME)"
ifeq ($(USE_PODMAN),true)
	@echo "Using Podman - multi-arch builds use manifest lists"
	@echo "Podman setup complete!"
else
	@echo "Setting up Docker buildx..."
	@docker buildx create --name $(BUILDER_NAME) --use 2>/dev/null || docker buildx use $(BUILDER_NAME)
	@docker buildx inspect --bootstrap
	@echo "Buildx setup complete!"
endif

.PHONY: info
info: ## Display build configuration
	@echo "========================================="
	@echo "Build Configuration"
	@echo "========================================="
	@echo "Base Image:        $(BASE_IMAGE)"
	@echo "Destination:       $(FULL_IMAGE_NAME)"
	@echo "AMD64 Tag:         $(FULL_IMAGE_NAME_AMD64)"
	@echo "PPC64LE Tag:       $(FULL_IMAGE_NAME_PPC64LE)"
	@echo "Latest Tag:        $(FULL_IMAGE_NAME_LATEST)"
	@echo "Dockerfile:        $(DOCKERFILE)"
	@echo "========================================="

.PHONY: build
build: setup ## Build multi-architecture image (local only, no push)
	@echo "Building multi-architecture image..."
ifeq ($(USE_PODMAN),true)
	@echo "Note: Podman doesn't support --load with multi-platform. Building individual architectures..."
	@$(MAKE) build-all
else
	docker buildx build \
		--platform linux/amd64,linux/ppc64le \
		--build-arg BASE_IMAGE=$(BASE_IMAGE) \
		-f $(DOCKERFILE) \
		-t $(FULL_IMAGE_NAME) \
		--load \
		.
	@echo "Build complete: $(FULL_IMAGE_NAME)"
endif

.PHONY: build-amd64
build-amd64: setup ## Build AMD64 image only
	@echo "Building AMD64 image..."
ifeq ($(USE_PODMAN),true)
	$(CONTAINER_RUNTIME) build \
		--platform linux/amd64 \
		--build-arg BASE_IMAGE=$(BASE_IMAGE) \
		-f $(DOCKERFILE) \
		-t $(FULL_IMAGE_NAME_AMD64) \
		.
else
	docker buildx build \
		--platform linux/amd64 \
		--build-arg BASE_IMAGE=$(BASE_IMAGE) \
		-f $(DOCKERFILE) \
		-t $(FULL_IMAGE_NAME_AMD64) \
		--load \
		.
endif
	@echo "Build complete: $(FULL_IMAGE_NAME_AMD64)"

.PHONY: build-ppc64le
build-ppc64le: setup ## Build PPC64LE image only
	@echo "Building PPC64LE image..."
ifeq ($(USE_PODMAN),true)
	$(CONTAINER_RUNTIME) build \
		--platform linux/ppc64le \
		--build-arg BASE_IMAGE=$(BASE_IMAGE) \
		-f $(DOCKERFILE) \
		-t $(FULL_IMAGE_NAME_PPC64LE) \
		.
else
	docker buildx build \
		--platform linux/ppc64le \
		--build-arg BASE_IMAGE=$(BASE_IMAGE) \
		-f $(DOCKERFILE) \
		-t $(FULL_IMAGE_NAME_PPC64LE) \
		--load \
		.
endif
	@echo "Build complete: $(FULL_IMAGE_NAME_PPC64LE)"

.PHONY: build-all
build-all: build-amd64 build-ppc64le ## Build both AMD64 and PPC64LE images separately

.PHONY: push
push: setup ## Build and push multi-architecture image to registry
	@echo "Building and pushing multi-architecture image..."
ifeq ($(USE_PODMAN),true)
	@echo "Building individual architectures with Podman..."
	@$(MAKE) build-amd64
	@$(MAKE) build-ppc64le
	@echo "Pushing images..."
	$(CONTAINER_RUNTIME) push $(FULL_IMAGE_NAME_AMD64)
	$(CONTAINER_RUNTIME) push $(FULL_IMAGE_NAME_PPC64LE)
	@echo "Creating manifest..."
	$(CONTAINER_RUNTIME) manifest create $(FULL_IMAGE_NAME) \
		$(FULL_IMAGE_NAME_AMD64) \
		$(FULL_IMAGE_NAME_PPC64LE)
	$(CONTAINER_RUNTIME) manifest push $(FULL_IMAGE_NAME)
	@echo "Pushed: $(FULL_IMAGE_NAME)"
else
	docker buildx build \
		--platform linux/amd64,linux/ppc64le \
		--build-arg BASE_IMAGE=$(BASE_IMAGE) \
		-f $(DOCKERFILE) \
		-t $(FULL_IMAGE_NAME) \
		--push \
		.
	@echo "Pushed: $(FULL_IMAGE_NAME)"
endif

.PHONY: push-latest
push-latest: setup ## Build and push with both version tag and latest tag
	@echo "Building and pushing with latest tag..."
ifeq ($(USE_PODMAN),true)
	@$(MAKE) push
	@echo "Creating latest manifest..."
	$(CONTAINER_RUNTIME) manifest create $(FULL_IMAGE_NAME_LATEST) \
		$(FULL_IMAGE_NAME_AMD64) \
		$(FULL_IMAGE_NAME_PPC64LE)
	$(CONTAINER_RUNTIME) manifest push $(FULL_IMAGE_NAME_LATEST)
	@echo "Pushed: $(FULL_IMAGE_NAME_LATEST)"
else
	docker buildx build \
		--platform linux/amd64,linux/ppc64le \
		--build-arg BASE_IMAGE=$(BASE_IMAGE) \
		-f $(DOCKERFILE) \
		-t $(FULL_IMAGE_NAME) \
		-t $(FULL_IMAGE_NAME_LATEST) \
		--push \
		.
	@echo "Pushed: $(FULL_IMAGE_NAME)"
	@echo "Pushed: $(FULL_IMAGE_NAME_LATEST)"
endif

.PHONY: push-all
push-all: setup ## Build and push all image variants (multi-arch, amd64, ppc64le)
	@echo "Building and pushing all variants..."
ifeq ($(USE_PODMAN),true)
	@$(MAKE) build-amd64
	@$(MAKE) build-ppc64le
	$(CONTAINER_RUNTIME) push $(FULL_IMAGE_NAME_AMD64)
	$(CONTAINER_RUNTIME) push $(FULL_IMAGE_NAME_PPC64LE)
	$(CONTAINER_RUNTIME) manifest create $(FULL_IMAGE_NAME) \
		$(FULL_IMAGE_NAME_AMD64) \
		$(FULL_IMAGE_NAME_PPC64LE)
	$(CONTAINER_RUNTIME) manifest push $(FULL_IMAGE_NAME)
else
	@$(MAKE) push
	@echo "Pushing AMD64 variant..."
	docker buildx build \
		--platform linux/amd64 \
		--build-arg BASE_IMAGE=$(BASE_IMAGE) \
		-f $(DOCKERFILE) \
		-t $(FULL_IMAGE_NAME_AMD64) \
		--push \
		.
	@echo "Pushing PPC64LE variant..."
	docker buildx build \
		--platform linux/ppc64le \
		--build-arg BASE_IMAGE=$(BASE_IMAGE) \
		-f $(DOCKERFILE) \
		-t $(FULL_IMAGE_NAME_PPC64LE) \
		--push \
		.
endif
	@echo "All variants pushed successfully!"

.PHONY: test
test: build ## Build and test the image locally
	@echo "Testing image..."
ifeq ($(USE_PODMAN),true)
	@echo "Testing AMD64 image..."
	@$(CONTAINER_RUNTIME) run --rm $(FULL_IMAGE_NAME_AMD64) java -version
	@$(CONTAINER_RUNTIME) run --rm $(FULL_IMAGE_NAME_AMD64) python3 --version
	@$(CONTAINER_RUNTIME) run --rm $(FULL_IMAGE_NAME_AMD64) ansible --version
else
	@$(CONTAINER_RUNTIME) run --rm $(FULL_IMAGE_NAME) java -version
	@$(CONTAINER_RUNTIME) run --rm $(FULL_IMAGE_NAME) python3 --version
	@$(CONTAINER_RUNTIME) run --rm $(FULL_IMAGE_NAME) ansible --version
endif
	@echo "Tests passed!"

.PHONY: inspect
inspect: ## Inspect the built multi-architecture image
	@echo "Inspecting image: $(FULL_IMAGE_NAME)"
ifeq ($(USE_PODMAN),true)
	$(CONTAINER_RUNTIME) manifest inspect $(FULL_IMAGE_NAME)
else
	docker buildx imagetools inspect $(FULL_IMAGE_NAME)
endif

.PHONY: list
list: ## List local jenkins-inbound-agent images
	@echo "Local jenkins-inbound-agent images:"
	@$(CONTAINER_RUNTIME) images | grep jenkins-inbound-agent || echo "No images found"

.PHONY: clean
clean: ## Remove local images
	@echo "Removing local images..."
	-$(CONTAINER_RUNTIME) rmi $(FULL_IMAGE_NAME) 2>/dev/null || true
	-$(CONTAINER_RUNTIME) rmi $(FULL_IMAGE_NAME_AMD64) 2>/dev/null || true
	-$(CONTAINER_RUNTIME) rmi $(FULL_IMAGE_NAME_PPC64LE) 2>/dev/null || true
	@echo "Cleanup complete!"

.PHONY: clean-builder
clean-builder: ## Remove buildx builder
ifeq ($(USE_PODMAN),true)
	@echo "Podman doesn't use persistent builders - nothing to clean"
else
	@echo "Removing buildx builder..."
	-docker buildx rm $(BUILDER_NAME) 2>/dev/null || true
	@echo "Builder removed!"
endif

.PHONY: clean-all
clean-all: clean clean-builder ## Remove all images and builder

# Example usage targets
.PHONY: example-build
example-build: ## Example: Build with custom base image tag
	@$(MAKE) build BASE_IMAGE_TAG=latest-jdk17

.PHONY: example-push
example-push: ## Example: Build and push with custom configuration
	@$(MAKE) push \
		BASE_IMAGE_TAG=3355.v388858a_47b_33-21-jdk17 \
		DOCKER_REGISTRY=quay.io \
		DOCKER_REPO=pgottimu/jenkins-inbound-agent \
		IMAGE_TAG=3355.v388858a_47b_33-21-jdk17

# Made with Bob
