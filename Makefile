.PHONY: \
	server-format server-format-check server-build server-test server-package \
	server-it-image server-it server-native-hints server-native \
	client-install client-test client-build \
	docker-build docker-test \
	it-image it

# ── Server ────────────────────────────────────────────────────────────────────

server-format:
	cd server && mvn -B -ntp spotless:apply

server-format-check:
	cd server && mvn -B -ntp spotless:check

server-build:
	cd server && mvn -B -ntp compile -Dspotless.check.skip=true

server-test:
	cd server && mvn -B -ntp test -Dspotless.check.skip=true

server-package:
	cd server && mvn -B -ntp package -DskipTests -Dspotless.check.skip=true

server-it-image:
	docker build -t porthole-it:latest -f docker/Dockerfile.it .

server-it: server-it-image
	cd server && mvn -B -ntp verify -Pintegration-tests -Dspotless.check.skip=true

server-native-hints: server-it-image
	cd server && mvn -B -ntp verify -Pgenerate-native-hints -Dspotless.check.skip=true

server-native:
	cd server && mvn -Pnative,copy-client package native:compile -DskipTests -B

# ── Client ────────────────────────────────────────────────────────────────────

client-install:
	cd client && npm ci

client-test: client-install
	cd client && npm run test:coverage

client-build: client-install
	cd client && npm run build

# ── Docker ────────────────────────────────────────────────────────────────────

docker-build:
	touch porthole
	docker build -t porthole:ci-test -f docker/Dockerfile .

docker-test: docker-build
	./docker/test_entrypoint.sh porthole:ci-test
