.PHONY: help server-it server-native-hints bundle bundle-native native

MVN ?= mvn

# Cross-component targets — all other targets live in their respective folder Makefiles.

bundle: ## Build the client and package it with the JVM server
	$(MAKE) -C client build
	$(MVN) -f server/pom.xml package -DskipTests -Pcopy-client -Dspotless.check.skip=true

native: ## Build a native executable using the existing client build
	$(MVN) -f server/pom.xml -Pnative,copy-client package native:compile -DskipTests -Dspotless.check.skip=true

bundle-native: ## Build the client and package it with a native executable
	$(MAKE) -C client build
	$(MVN) -f server/pom.xml -Pnative,copy-client package native:compile -DskipTests -Dspotless.check.skip=true

server-it: ## Package the server, rebuild the JVM image, and run integration tests
	$(MAKE) -C server package
	$(MAKE) -C docker jvm-image
	$(MAKE) -C server it

server-native-hints: ## Package the server, rebuild the JVM image, and regenerate native hints
	$(MAKE) -C server package
	$(MAKE) -C docker jvm-image
	$(MAKE) -C server native-hints

help: ## Show available root targets
	@printf 'Usage: make <target>\n\nTargets:\n'
	@awk 'BEGIN { FS = ":.*## " } /^[a-zA-Z0-9_-]+:.*## / { printf "  %-22s %s\n", $$1, $$2 }' $(MAKEFILE_LIST)
	@printf '\nComponent targets are defined in client/Makefile, server/Makefile, and docker/Makefile.\n'

