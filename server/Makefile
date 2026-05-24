.PHONY: format-check build test package it native-hints native

MVN ?= mvn

format-check:
	$(MVN) spotless:check

build:
	$(MVN) compile -Dspotless.check.skip=true

package:
	$(MVN) package -DskipTests -Dspotless.check.skip=true

test:
	$(MVN) test -Dspotless.check.skip=true

it:
	$(MVN) verify -Pintegration-tests -Dspotless.check.skip=true

native-hints:
	$(MVN) verify -Pgenerate-native-hints -Dspotless.check.skip=true

native:
	$(MVN) -Pnative,copy-client package native:compile -DskipTests -Dspotless.check.skip=true
