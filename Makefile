.PHONY: server-it server-native-hints

# Cross-component targets that require the IT Docker image before running server ITs.
# All other targets live in their respective folder Makefiles.

server-it:
	$(MAKE) -C docker it-image
	$(MAKE) -C server it

server-native-hints:
	$(MAKE) -C docker it-image
	$(MAKE) -C server native-hints

