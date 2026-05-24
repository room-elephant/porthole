.PHONY: jvm-image build test

# Docker builds require the repo root as context (files are copied from server/target, docker/, etc.)
ROOT := $(abspath $(dir $(lastword $(MAKEFILE_LIST)))..)

jvm-image:
	docker build -t porthole-jvm:latest -f $(ROOT)/docker/Dockerfile.jvm $(ROOT)


build:
	touch $(ROOT)/porthole
	docker build -t porthole:ci-test -f $(ROOT)/docker/Dockerfile $(ROOT)

test: build
	$(ROOT)/docker/test_entrypoint.sh porthole:ci-test
