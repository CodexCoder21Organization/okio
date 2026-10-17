#!/usr/bin/env bash
set -euo pipefail
base=/tmp/claude-501/-code/a549f21f-144a-44f6-8143-0e07f6d01615/scratchpad/lanes/out
slot=''
for attempt in $(seq 1 30); do
  for i in 1 2 3 4; do
    if mkdir "$base/build-slot-$i" 2>/dev/null; then
      slot="$base/build-slot-$i"
      echo "revokio3 $(date -u +%H:%M)" > "$slot/owner"
      break
    fi
  done
  if [ -n "$slot" ]; then break; fi
  sleep 30
done
if [ -z "$slot" ]; then echo 'No build slot within 15 minutes'; exit 1; fi
trap 'rm -r "$slot"' EXIT
export LC_ALL=C.UTF-8
df -h /
python3 scripts/buildFakeFilesystemDirectoryBackport.py --output-directory "$PWD/revokio3-artifact"
scripts/testFakeFilesystemDirectoryBackport.bash "$PWD/revokio3-artifact" "$PWD/evidence/revokio3/native"
