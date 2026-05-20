#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")" && pwd)"
IMAGE_TAG="${PORTHOLE_IT_IMAGE_TAG:-porthole-it:latest}"

cd "$REPO_ROOT"

docker build \
  --cache-from "$IMAGE_TAG" \
  -t "$IMAGE_TAG" \
  -f docker/Dockerfile.it \
  .

echo ""
echo "Image ready: $IMAGE_TAG"
echo "Run tests:   cd server && PORTHOLE_IT_IMAGE_TAG=$IMAGE_TAG mvn -Pintegration-tests verify"
