.PHONY: server-it server-native-hints bundle bundle-native

MVN ?= mvn

# Cross-component targets — all other targets live in their respective folder Makefiles.

bundle:
	$(MAKE) -C client build
	$(MVN) -f server/pom.xml package -DskipTests -Pcopy-client -Dspotless.check.skip=true

bundle-native:
	$(MAKE) -C client build
	$(MVN) -f server/pom.xml -Pnative,copy-client package native:compile -DskipTests -Dspotless.check.skip=true

server-it:
	$(MAKE) -C docker it-image
	$(MAKE) -C server it

server-native-hints:
	$(MAKE) -C docker it-image
	$(MAKE) -C server native-hints

