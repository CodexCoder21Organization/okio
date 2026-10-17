#!/usr/bin/env bash
set -euo pipefail
export LC_ALL=C.UTF-8
reviewOut="$(realpath ../../out/revokio1-evidence)"
reviewRoot="$(pwd)"
df -h / > "$reviewOut/probe-disk.txt"
if [ "$(df --output=avail -B1 / | tail -1)" -lt 21474836480 ]; then exit 80; fi
reviewSlot=''
for ((attempt=0; attempt<30; attempt++)); do
  for i in 1 2 3 4; do
    reviewCandidate="$(realpath ../../out)/build-slot-$i"
    if mkdir "$reviewCandidate" 2>/dev/null; then
      reviewSlot="$reviewCandidate"
      echo "revokio1 $(date -u +%H:%M)" > "$reviewSlot/owner"
      break 2
    fi
  done
  sleep 30
done
if [ -z "$reviewSlot" ]; then exit 81; fi
reviewTemp="$(mktemp -d "$reviewRoot/.git/review-probe.XXXXXX")"
trap 'rm -rf "$reviewTemp" "$reviewSlot"' EXIT
/usr/local/bin/coursier fetch --classpath -r https://kotlin.directory \
  com.squareup.okio:okio-jvm:3.4.0.2 \
  org.jetbrains.kotlinx:kotlinx-datetime-jvm:0.4.0 \
  org.junit.platform:junit-platform-console-standalone:1.10.0 > "$reviewTemp/classpath"
reviewClasspath="$reviewOut/gate/okiofakefs1-gate/artifact/okio-fakefilesystem-jvm-3.4.0-r576-registry-lock-2.jar:$(cat "$reviewTemp/classpath")"
javac -J-Xmx256m -cp "$reviewClasspath" -d "$reviewTemp" evidence/revokio1/NeighborEntryReviewTest.java
set +e
java -Xmx256m -cp "$reviewTemp:$reviewClasspath" org.junit.platform.console.ConsoleLauncher execute \
  --select-class NeighborEntryReviewTest --reports-dir "$reviewOut/neighbor-native" --disable-banner > "$reviewOut/neighbor-console.log" 2>&1
reviewResult=$?
set -e
echo "$reviewResult" > "$reviewOut/neighbor-exit.txt"
exit "$reviewResult"
