.PHONY: format-check format build test test-one package it native-hints native dev

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

it:
	$(MVN) verify -Pintegration-tests -Dspotless.check.skip=true

native-hints:
	$(MVN) verify -Pgenerate-native-hints -Dspotless.check.skip=true

native:
	$(MVN) -Pnative package native:compile -DskipTests -Dspotless.check.skip=true
