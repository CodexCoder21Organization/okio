#!/usr/bin/env bash
set -euo pipefail

repo=/home/u/lanes/util20261014/workspace/okiofix1
suite="$repo/build/nullable-use/suite"
fixed_jar="$repo/build/nullable-use/fixed.jar"
classes="$suite/classes"
reports="$suite/reports"

if [[ ! -s "$fixed_jar" || ! -d "$classes" ]]; then
  printf 'Fixed jar or compiled test classes are missing.\n' >&2
  exit 2
fi

rm -rf "$reports"
mkdir -p "$reports"
test_cp=$(<"$repo/build/nullable-use/test.cp")
deps_cp=$(<"$suite/dependencies.all.cp")
runtime_cp="$fixed_jar:$classes:$suite/support-classes:$deps_cp:$test_cp"

exec timeout 900s java -Xmx512m -cp "$runtime_cp" org.junit.platform.console.ConsoleLauncher execute \
  --disable-banner \
  --disable-ansi-colors \
  --details=verbose \
  --scan-class-path="$classes" \
  --include-package=okio \
  --reports-dir="$reports" \
  --fail-if-no-tests
