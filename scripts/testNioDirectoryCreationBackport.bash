#!/usr/bin/env bash
set -euo pipefail
export LC_ALL=C.UTF-8
if [ "$#" -ne 2 ]; then
  echo "Usage: $0 <okio-jvm-jar> <report-directory>" >&2
  exit 2
fi
okioDirectoryJar="$(realpath "$1")"
mkdir -p "$2"
okioDirectoryReports="$(realpath "$2")"
okioDirectoryRoot="$(cd "$(dirname "$0")/.." && pwd)"
okioDirectoryTemporary="$(mktemp -d)"
trap 'rm -rf "$okioDirectoryTemporary"' EXIT
coursier fetch --classpath -r https://kotlin.directory \
  org.jetbrains.kotlin:kotlin-stdlib:1.8.22 \
  org.jetbrains.kotlin:kotlin-test:1.8.22 \
  com.google.jimfs:jimfs:1.3.0 \
  org.junit.platform:junit-platform-console-standalone:1.10.0 > "$okioDirectoryTemporary/dependencies"
okioDirectoryClasspath="$okioDirectoryJar:$(cat "$okioDirectoryTemporary/dependencies")"
coursier fetch --classpath -r https://kotlin.directory \
  org.jetbrains.kotlin:kotlin-compiler-embeddable:1.8.22 > "$okioDirectoryTemporary/compiler"
# The compatible 3.4.0 artifact retains its original mustCreate message spelling.
sed 's/already exists\./already exist./g' \
  "$okioDirectoryRoot/okio/src/jvmTest/kotlin/okio/ConcurrentNioDirectoryCreationTest.kt" \
  > "$okioDirectoryTemporary/ConcurrentNioDirectoryCreationTest.kt"
java -Xmx128m -cp "$(cat "$okioDirectoryTemporary/compiler")" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler \
  -no-stdlib -no-reflect -jvm-target 1.8 -cp "$okioDirectoryClasspath" -d "$okioDirectoryTemporary/classes" \
  "$okioDirectoryRoot/okio/src/jvmTest/kotlin/okio/DirectoryCreationNioFileSystem.kt" \
  "$okioDirectoryTemporary/ConcurrentNioDirectoryCreationTest.kt" \
  "$okioDirectoryRoot/okio/src/jvmTest/kotlin/okio/ReplacedNioDirectoryCreationTest.kt" \
  "$okioDirectoryRoot/okio/src/jvmTest/kotlin/okio/ExistingNioDirectoryFailureTest.kt"
java -Xmx128m -cp "$okioDirectoryTemporary/classes:$okioDirectoryClasspath" \
  org.junit.platform.console.ConsoleLauncher execute \
  --select-class okio.ConcurrentNioDirectoryCreationTest \
  --select-class okio.ReplacedNioDirectoryCreationTest \
  --select-class okio.ExistingNioDirectoryFailureTest \
  --reports-dir "$okioDirectoryReports" --disable-banner \
  2>&1 | tee "$okioDirectoryReports/console.log"
