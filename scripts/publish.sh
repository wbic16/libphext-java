#!/usr/bin/env bash
# libphext-java publish step: there is no registry for this port —
# distribution is a git tag on this repo (the family pattern for
# non-registry ports, as in libphext-cpp).
# With no environment variables it is a dry run: it runs the tests and the
# build, prints the tag it would create, and stops. With
# PUBLISH_CONFIRM=1 it creates the tag on HEAD and pushes it.
set -euo pipefail
cd "$(dirname "$0")/.."

VERSION="$(grep -oPm1 '(?<=VERSION = ")[^"]+' src/main/java/io/phext/libphext/LibPhext.java)"
TAG="v${VERSION}"

echo "== libphext-java publish: version ${VERSION} =="
echo "-- step 1/3: tests"
"$(dirname "$0")/test.sh"
echo "-- step 2/3: build"
"$(dirname "$0")/build.sh"
echo "-- step 3/3: tag"
echo "Will create and push tag: ${TAG} on $(git rev-parse --short HEAD) (HEAD of the current branch)"
if [ "${PUBLISH_CONFIRM:-}" != "1" ]; then
  echo "STOP: dry run only. Re-run with PUBLISH_CONFIRM=1 to create and push the tag."
  exit 0
fi
if git rev-parse "${TAG}" >/dev/null 2>&1; then
  echo "ERROR: tag ${TAG} already exists locally." >&2
  exit 1
fi
git tag "${TAG}"
git push origin "${TAG}"
echo "Published tag ${TAG}."
