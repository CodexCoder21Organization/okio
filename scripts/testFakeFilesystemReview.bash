#!/usr/bin/env bash
set -euo pipefail
if [ "$#" -lt 2 ] || [ "$#" -gt 3 ]; then
  echo "Usage: $0 <fakefilesystem-jar> <report-directory> [additional-classpath]" >&2
  exit 2
fi
okioReviewJar="$(realpath "$1")"
mkdir -p "$2"
okioReviewReports="$(realpath "$2")"
okioReviewRoot="$(cd "$(dirname "$0")/.." && pwd)"
okioReviewTemporary="$(mktemp -d)"
trap 'rm -rf "$okioReviewTemporary"' EXIT
coursier fetch --classpath -r https://kotlin.directory \
  com.squareup.okio:okio-jvm:3.4.0 \
  org.jetbrains.kotlinx:kotlinx-datetime-jvm:0.4.0 \
  org.junit.platform:junit-platform-console-standalone:1.10.0 \
  org.ow2.asm:asm:9.7.1 > "$okioReviewTemporary/classpath"
okioReviewClasspath="$okioReviewJar:$(cat "$okioReviewTemporary/classpath")"
if [ -n "${3:-}" ]; then
  okioReviewClasspath="$okioReviewJar:$3:$(cat "$okioReviewTemporary/classpath")"
fi
javac -J-Xmx256m -cp "$okioReviewClasspath" -d "$okioReviewTemporary/classes" \
  "$okioReviewRoot/backports/fakefilesystem-3.4.0/review-tests/FakeFileSystemWorkAgent.java" \
  "$okioReviewRoot/backports/fakefilesystem-3.4.0/review-tests/FakeFileSystemReviewTest.java" \
  "$okioReviewRoot/backports/fakefilesystem-3.4.0/review-tests/FakeFileSystemAccessWorkTest.java" \
  "$okioReviewRoot/backports/fakefilesystem-3.4.0/review-tests/FakeFileSystemRejectedResizeTest.java" \
  "$okioReviewRoot/backports/fakefilesystem-3.4.0/review-tests/FakeFileSystemReplacementPublicationTest.java"
printf 'Premain-Class: FakeFileSystemWorkAgent\n' > "$okioReviewTemporary/MANIFEST.MF"
jar cfm "$okioReviewTemporary/work-agent.jar" "$okioReviewTemporary/MANIFEST.MF" -C "$okioReviewTemporary/classes" .
java -Xmx256m -javaagent:"$okioReviewTemporary/work-agent.jar" \
  -cp "$okioReviewTemporary/classes:$okioReviewClasspath" \
  org.junit.platform.console.ConsoleLauncher execute \
  --select-class FakeFileSystemReviewTest --select-class FakeFileSystemAccessWorkTest \
  --select-class FakeFileSystemRejectedResizeTest --select-class FakeFileSystemReplacementPublicationTest \
  --reports-dir "$okioReviewReports" --disable-banner
