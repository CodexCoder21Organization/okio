#!/usr/bin/env bash
set -euo pipefail
export LC_ALL=C.UTF-8
if [ "$#" -lt 2 ] || [ "$#" -gt 3 ]; then
  echo "Usage: $0 <fakefilesystem-jar> <report-directory> [junit-method-selector]" >&2
  exit 2
fi
okioFakeFilesystemJar="$(realpath "$1")"
mkdir -p "$2"
okioBulkReports="$(realpath "$2")"
okioRoot="$(cd "$(dirname "$0")/.." && pwd)"
okioBulkTemporary="$(mktemp -d)"
trap 'rm -rf "$okioBulkTemporary"' EXIT
coursier fetch --classpath -r https://kotlin.directory \
  com.squareup.okio:okio-jvm:3.4.0 \
  org.jetbrains.kotlinx:kotlinx-datetime-jvm:0.4.0 \
  org.junit.platform:junit-platform-console-standalone:1.10.0 \
  org.jetbrains.kotlin:kotlin-test-junit:1.8.0 > "$okioBulkTemporary/classpath"
okioBulkClasspath="$okioFakeFilesystemJar:$(cat "$okioBulkTemporary/classpath")"
javac -J-Xmx256m -cp "$okioBulkClasspath" -d "$okioBulkTemporary/classes" \
  "$okioRoot/backports/fakefilesystem-3.4.0/tests/FakeFileSystemBulkReadTest.java"
if [ "$#" -eq 3 ]; then
  java -Xmx256m -cp "$okioBulkTemporary/classes:$okioBulkClasspath" \
    org.junit.platform.console.ConsoleLauncher execute \
    --select-method "$3" --reports-dir "$okioBulkReports" --disable-banner \
    2>&1 | tee "$okioBulkReports/console.log"
else
  java -Xmx256m -cp "$okioBulkTemporary/classes:$okioBulkClasspath" \
    org.junit.platform.console.ConsoleLauncher execute \
    --select-class FakeFileSystemBulkReadTest --reports-dir "$okioBulkReports" --disable-banner \
    2>&1 | tee "$okioBulkReports/console.log"
fi
