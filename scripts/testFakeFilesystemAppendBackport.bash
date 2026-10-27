#!/usr/bin/env bash
set -euo pipefail
export LC_ALL=C.UTF-8
if [ "$#" -ne 2 ]; then
  echo "Usage: $0 <fakefilesystem-jar> <report-directory>" >&2
  exit 2
fi
okioAppendJar="$(realpath "$1")"
mkdir -p "$2"
okioAppendReports="$(realpath "$2")"
okioRoot="$(cd "$(dirname "$0")/.." && pwd)"
okioAppendTemporary="$(mktemp -d)"
trap 'rm -rf "$okioAppendTemporary"' EXIT
~/bin/coursier fetch --classpath -r https://kotlin.directory \
  com.squareup.okio:okio-jvm:3.4.0 \
  org.jetbrains.kotlinx:kotlinx-datetime-jvm:0.4.0 \
  org.junit.platform:junit-platform-console-standalone:1.10.0 \
  org.jetbrains.kotlin:kotlin-test-junit:1.8.0 > "$okioAppendTemporary/classpath"
okioAppendClasspath="$okioAppendJar:$(cat "$okioAppendTemporary/classpath")"
mkdir -p "$okioAppendTemporary/classes"
javac -J-Xmx256m -cp "$okioAppendClasspath" -d "$okioAppendTemporary/classes" \
  "$okioRoot/backports/fakefilesystem-3.4.0/tests/FakeFileSystemAppendWorkTest.java" \
  "$okioRoot/backports/fakefilesystem-3.4.0/tests/FakeFileSystemContentOwnershipTest.java"
java -Xmx256m -cp "$okioAppendTemporary/classes:$okioAppendClasspath" \
  org.junit.platform.console.ConsoleLauncher execute \
  --select-class FakeFileSystemAppendWorkTest --select-class FakeFileSystemContentOwnershipTest --reports-dir "$okioAppendReports" --disable-banner \
  2>&1 | tee "$okioAppendReports/console.log"
