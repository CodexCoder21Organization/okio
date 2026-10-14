#!/usr/bin/env bash
set -euo pipefail

repo=/home/u/lanes/util20261014/workspace/okiofix1
source_root="$repo/build/nullable-use/source"
suite="$repo/build/nullable-use/suite"
out=/home/u/lanes/util20261014/out
fixed_jar="$repo/build/nullable-use/fixed.jar"
support_classes="$suite/support-classes"
classes="$suite/classes"

if [[ ! -s "$fixed_jar" ]]; then
  printf 'Fixed jar is not ready: %s\n' "$fixed_jar" >&2
  exit 2
fi

slot=
for attempt in $(seq 1 10); do
  for number in 1 2 4; do
    candidate="$out/build-slot-$number"
    if mkdir "$candidate" 2>/dev/null; then
      slot="$candidate"
      printf 'native_suite %s\n' "$(date -u '+%Y-%m-%d %H:%M:%S UTC')" > "$slot/owner"
      break
    fi
  done
  if [[ -n "$slot" ]]; then
    break
  fi
  if (( attempt < 10 )); then
    sleep 30
  fi
done
if [[ -z "$slot" ]]; then
  printf 'Could not acquire build slot 1, 2, or 4 within the bounded wait.\n' >&2
  exit 3
fi
trap 'rm -f "$slot/owner"; rmdir "$slot"' EXIT

mkdir -p "$support_classes"
rm -rf "$classes"
mkdir -p "$classes"
compiler_cp=$(<"$repo/build/nullable-use/compiler.cp")
test_cp=$(<"$repo/build/nullable-use/test.cp")
deps_cp=$(<"$suite/dependencies.all.cp")
compile_cp="$fixed_jar:$deps_cp:$test_cp"
mapfile -t support_sources < "$suite/support-sources.txt"
mapfile -t kotlin_sources < "$suite/kotlin-sources.txt"
mapfile -t hash_function_sources < <(find "$source_root/okio/src/hashFunctions/kotlin" -name '*.kt' -type f | sort)
kotlin_sources+=("${hash_function_sources[@]}")
mapfile -t java_sources < "$suite/java-sources.txt"

if [[ ! -f "$support_classes/okio/AbstractFileSystemTest.class" ]]; then
if timeout 600s java -Xmx768m -cp "$compiler_cp" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler \
  -module-name okio_testing_support \
  -jvm-target 1.8 \
  -classpath "$compile_cp" \
  -Xmulti-platform \
  -opt-in=kotlin.time.ExperimentalTime \
  -opt-in=kotlin.contracts.ExperimentalContracts \
  -d "$support_classes" \
  "${support_sources[@]}" > "$suite/support-compile.log" 2>&1; then
  cat "$suite/support-compile.log"
else
  status=$?
  cat "$suite/support-compile.log"
  exit "$status"
fi
else
  printf 'Using already compiled testing-support classes.\n'
fi

if timeout 600s java -Xmx768m -cp "$compiler_cp" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler \
  -module-name okio_jvm_tests \
  -jvm-target 1.8 \
  -classpath "$compile_cp:$support_classes" \
  -Xfriend-paths="$fixed_jar" \
  -Xmulti-platform \
  -Xjava-source-roots="$source_root/okio/src/jvmTest/java" \
  -opt-in=kotlin.time.ExperimentalTime \
  -opt-in=kotlin.contracts.ExperimentalContracts \
  -d "$classes" \
  "${kotlin_sources[@]}" > "$suite/kotlin-compile.log" 2>&1; then
  cat "$suite/kotlin-compile.log"
else
  status=$?
  cat "$suite/kotlin-compile.log"
  exit "$status"
fi

if timeout 600s javac -source 8 -target 8 -encoding UTF-8 \
  -classpath "$compile_cp:$support_classes:$classes" \
  -d "$classes" \
  "${java_sources[@]}" > "$suite/java-compile.log" 2>&1; then
  cat "$suite/java-compile.log"
else
  status=$?
  cat "$suite/java-compile.log"
  exit "$status"
fi

cp -a "$source_root/okio/src/jvmTest/resources/." "$classes/"
printf 'Compiled %s testing-support Kotlin sources, %s test Kotlin sources, and %s Java sources; copied %s JVM resources.\n' \
  "${#support_sources[@]}" "${#kotlin_sources[@]}" "${#java_sources[@]}" \
  "$(find "$source_root/okio/src/jvmTest/resources" -type f | wc -l)"
