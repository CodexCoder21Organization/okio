#!/usr/bin/env bash
set -euo pipefail
base=/tmp/claude-501/-code/a549f21f-144a-44f6-8143-0e07f6d01615/scratchpad/lanes/out
slot=''
for attempt in $(seq 1 30); do
  for i in 1 2 3 4; do
    if mkdir "$base/build-slot-$i" 2>/dev/null; then
      slot="$base/build-slot-$i"
      echo "okiofix4 $(date -u +%H:%M)" > "$slot/owner"
      echo "$i" > "$base/okiofix4-slot"
      break
    fi
  done
  if [ -n "$slot" ]; then break; fi
  sleep 30
done
if [ -z "$slot" ]; then echo 'No build slot within 15 minutes'; exit 1; fi
trap 'rm -rf "$slot"; rm -f "$base/okiofix4-slot"' EXIT
export LC_ALL=C.UTF-8
freebytes=$(df --output=avail -B1 / | tail -1)
if [ "$freebytes" -lt 21474836480 ]; then echo 'NOT READY: local disk has less than 20 GB available'; exit 1; fi
mkdir -p .okiofix4-run
set +e
python3 scripts/buildFakeFilesystemDirectoryBackport.py --output-directory "$PWD/.okiofix4-run/artifact"
result=$?
if [ "$result" -eq 0 ]; then
 scripts/testFakeFilesystemDirectoryBackport.bash "$PWD/.okiofix4-run/artifact" "$PWD/.okiofix4-run/native"
 result=$?
fi
set -e
echo "$result" > "$base/okiofix4-run.exit"
exit "$result"
