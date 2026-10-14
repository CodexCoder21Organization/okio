#!/bin/bash
set -euo pipefail
cd /home/u/lanes/util20261014/workspace/okiofix1
git fetch origin
git -c rebase.autoStash=true rebase origin/main
df -h /
limit=$((SECONDS+600))
slot=''
while [[ -z "$slot" && $SECONDS -lt $limit ]]; do
 for n in 1 2 4; do
  if mkdir /home/u/lanes/util20261014/out/build-slot-$n 2>/dev/null; then
   slot=/home/u/lanes/util20261014/out/build-slot-$n
   echo "okiofix1-focused $(date -u +%FT%TZ)" > "$slot/owner"
   break
  fi
 done
 if [[ -z "$slot" ]]; then sleep 30; fi
done
[[ -n "$slot" ]] || exit 124
trap 'rm -f "$slot/owner"; rmdir "$slot"' EXIT
LC_ALL=C.UTF-8 timeout 750 python3 /home/u/lanes/util20261014/out/okiofix1-focused-gate.py
