#!/usr/bin/env bash
# Focused local verification of the upstream JVM file-handle implementation.
set -euo pipefail
if [[ $# != 2 || "$1" != --test || "$2" != fileHandlePreservesAppendBetweenShortReads ]]; then
  echo "Expected --test fileHandlePreservesAppendBetweenShortReads, but received: $*" >&2
  exit 2
fi
repo_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
build_dir="$repo_dir/build/short-read"
mkdir -p "$build_dir/classes" "$build_dir/harness"
cs_path="$build_dir/coursier"
if [[ ! -x "$cs_path" ]]; then
  curl -fsSL https://github.com/coursier/launchers/raw/master/coursier -o "$cs_path"
  chmod +x "$cs_path"
fi
compiler_cp="$("$cs_path" fetch org.jetbrains.kotlin:kotlin-compiler-embeddable:1.9.22 --classpath)"
runtime_cp="$("$cs_path" fetch com.squareup.okio:okio-jvm:3.4.0 org.ow2.asm:asm:9.7 --classpath)"
java -Xmx512m -cp "$compiler_cp" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler \
  -no-stdlib -no-reflect -classpath "$runtime_cp" -d "$build_dir/classes" \
  "$repo_dir/okio/src/jvmMain/kotlin/okio/JvmFileHandle.kt"
javac -cp "$runtime_cp" -d "$build_dir/harness" \
  "$repo_dir/tests/fileHandlePreservesAppendBetweenShortReads/ShortReadRegression.java"
printf 'Manifest-Version: 1.0\nPremain-Class: ShortReadRegression\n\n' > "$build_dir/manifest.mf"
jar cfm "$build_dir/ordering-agent.jar" "$build_dir/manifest.mf" -C "$build_dir/harness" .
# Build a local validation copy of the published jar with exactly the compiled upstream class.
# This is never uploaded and does not modify any Maven or kompile cache.
python3 - "$runtime_cp" "$build_dir" <<'PY'
import pathlib, sys, zipfile
base = next(p for p in sys.argv[1].split(':') if p.endswith('/okio-jvm-3.4.0.jar'))
root = pathlib.Path(sys.argv[2])
name = 'okio/JvmFileHandle.class'
with zipfile.ZipFile(base) as old, zipfile.ZipFile(root / 'okio-jvm-under-test.jar', 'w') as new:
    for entry in old.infolist():
        data = (root / 'classes' / name).read_bytes() if entry.filename == name else old.read(entry)
        new.writestr(entry, data)
PY
java -Xmx128m -javaagent:"$build_dir/ordering-agent.jar" \
  -cp "$build_dir/okio-jvm-under-test.jar:$build_dir/ordering-agent.jar:$runtime_cp" ShortReadRegression
