.PHONY: format-check format build test test-one arch package it native-hints native dev openapi

MVN ?= mvn

format-check:
	$(MVN) spotless:check

format:
	$(MVN) spotless:apply

dev:
	$(MVN) spring-boot:run -Dspotless.check.skip=true

build:
	$(MVN) compile -Dspotless.check.skip=true

package:
	$(MVN) package -DskipTests -Dspotless.check.skip=true

test:
	$(MVN) test -Dspotless.check.skip=true

test-one:
	$(MVN) test -Dtest=$(TEST) -Dspotless.check.skip=true

# The slice rules and the diagrams in docs/architecture, which `make test` leaves out.
arch:
	$(MVN) test -Dtest='ArchitectureTest,C3ArchTest,PackagesArchTest' -Dspotless.check.skip=true

it:
	$(MVN) verify -Pintegration-tests -Dspotless.check.skip=true

native-hints:
	$(MVN) verify -Pgenerate-native-hints -Dspotless.check.skip=true

openapi:
	$(MVN) verify -Popenapi

native:
	$(MVN) -Pnative package native:compile -DskipTests -Dspotless.check.skip=true
