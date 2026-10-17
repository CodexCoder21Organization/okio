#!/usr/bin/env bash
set -euo pipefail
base=/tmp/claude-501/-code/a549f21f-144a-44f6-8143-0e07f6d01615/scratchpad/lanes/out
slot=''
for attempt in $(seq 1 30); do
  for i in 1 2 3 4; do
    if mkdir "$base/build-slot-$i" 2>/dev/null; then slot="$base/build-slot-$i"; echo revokio3 > "$slot/owner"; break; fi
  done
  if [ -n "$slot" ]; then break; fi
  sleep 30
done
if [ -z "$slot" ]; then exit 1; fi
trap 'rm -r "$slot"' EXIT
export LC_ALL=C.UTF-8
df -h /
coursier fetch --classpath -r https://kotlin.directory com.squareup.okio:okio-jvm:3.4.0.2 org.jetbrains.kotlinx:kotlinx-datetime-jvm:0.4.0 org.junit.platform:junit-platform-console-standalone:1.10.0 > revokio3-boundary-classpath
classpath="$PWD/revokio3-artifact/okio-fakefilesystem-jvm-3.4.0-r576-registry-lock-4.jar:$(cat revokio3-boundary-classpath)"
javac -J-Xmx256m -cp "$classpath" -d revokio3-boundary-classes evidence/revokio3/LookupBoundaryReviewTest.java
java -Xmx256m -cp "revokio3-boundary-classes:$classpath" org.junit.platform.console.ConsoleLauncher execute --select-class LookupBoundaryReviewTest --reports-dir evidence/revokio3/boundaries-native --disable-banner
