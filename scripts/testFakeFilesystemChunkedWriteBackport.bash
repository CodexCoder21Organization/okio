#!/usr/bin/env bash
set -euo pipefail
if [ "$#" -ne 2 ]; then
  echo "Usage: $0 <fakefilesystem-jar> <report-directory>" >&2
  exit 2
fi
okioWriteJar="$(realpath "$1")"
mkdir -p "$2"
okioWriteReports="$(realpath "$2")"
okioWriteRoot="$(cd "$(dirname "$0")/.." && pwd)"
okioWriteTemporary="$(mktemp -d)"
trap 'rm -rf "$okioWriteTemporary"' EXIT
coursier fetch --classpath -r https://kotlin.directory \
  com.squareup.okio:okio-jvm:3.4.0 \
  org.jetbrains.kotlinx:kotlinx-datetime-jvm:0.4.0 \
  org.junit.platform:junit-platform-console-standalone:1.10.0 > "$okioWriteTemporary/classpath"
okioWriteClasspath="$okioWriteJar:$(cat "$okioWriteTemporary/classpath")"
javac -J-Xmx256m -cp "$okioWriteClasspath" -d "$okioWriteTemporary/classes" \
  "$okioWriteRoot/backports/fakefilesystem-3.4.0/tests/FakeFileSystemChunkedWriteTest.java"
java -Xmx256m -cp "$okioWriteTemporary/classes:$okioWriteClasspath" \
  org.junit.platform.console.ConsoleLauncher execute \
  --select-class FakeFileSystemChunkedWriteTest --reports-dir "$okioWriteReports" --disable-banner
