#!/usr/bin/env bash
# libphext-java build step. Prerequisite: JDK 21 (javac + java) on PATH.
# Plain javac build: fetches the pinned XZ for Java dependency, compiles
# the library, and packs target/libphext-<version>.jar.
set -euo pipefail
cd "$(dirname "$0")/.."

if ! command -v javac >/dev/null 2>&1; then
  echo "ERROR: 'javac' not found. Install a JDK 21 (e.g. openjdk-21-jdk-headless) first." >&2
  exit 1
fi

VERSION="$(grep -oPm1 '(?<=VERSION = ")[^"]+' src/main/java/io/phext/libphext/LibPhext.java)"
XZ_VERSION="1.9"
XZ_SHA256="211b306cfc44f8f96df3a0a3ddaf75ba8c5289eed77d60d72f889bb855f535e5"
XZ_JAR="target/deps/xz-${XZ_VERSION}.jar"
XZ_URL="https://repo.maven.apache.org/maven2/org/tukaani/xz/${XZ_VERSION}/xz-${XZ_VERSION}.jar"

mkdir -p target/deps target/classes
if [ ! -f "${XZ_JAR}" ]; then
  echo "-- fetching XZ for Java ${XZ_VERSION} (org.tukaani:xz, pure-Java XZ) --"
  curl -fsSL -o "${XZ_JAR}" "${XZ_URL}"
fi
echo "${XZ_SHA256}  ${XZ_JAR}" | sha256sum -c - >/dev/null
echo "xz dependency verified: ${XZ_JAR} (sha256 ok)"

find src/main/java -name '*.java' > target/sources.txt
javac -encoding UTF-8 -cp "${XZ_JAR}" -d target/classes @target/sources.txt
(cd target/classes && jar --create --file "../libphext-${VERSION}.jar" .)
echo "built target/libphext-${VERSION}.jar"
