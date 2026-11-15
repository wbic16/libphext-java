#!/usr/bin/env bash
# libphext-java test step. Prerequisite: JDK 21 (javac + java) on PATH.
# Builds the library, compiles the test tree, and runs the harness.
set -euo pipefail
cd "$(dirname "$0")/.."

"$(dirname "$0")/build.sh"

XZ_JAR="target/deps/xz-1.9.jar"
mkdir -p target/test-classes
find src/test/java -name '*.java' > target/test-sources.txt
javac -encoding UTF-8 -cp "target/classes:${XZ_JAR}" -d target/test-classes @target/test-sources.txt
java -cp "target/classes:target/test-classes:${XZ_JAR}" io.phext.libphext.TestMain "$(pwd)"
