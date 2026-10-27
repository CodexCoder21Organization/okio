#!/usr/bin/env bash
set -euo pipefail
export LC_ALL=C.UTF-8
if [ "$#" -ne 2 ]; then
  echo "Usage: $0 <artifact-directory> <report-directory>" >&2
  exit 2
fi
okioArtifactDirectory="$(realpath "$1")"
mkdir -p "$2"
okioReports="$(realpath "$2")"
okioRoot="$(cd "$(dirname "$0")/.." && pwd)"
mapfile -t okioFakeFilesystemJars < <(find "$okioArtifactDirectory" -maxdepth 1 -type f -name 'okio-fakefilesystem-jvm-*.jar' -print | sort)
if [ "${#okioFakeFilesystemJars[@]}" -ne 1 ]; then
  echo "Expected exactly one okio-fakefilesystem-jvm JAR in $okioArtifactDirectory, found ${#okioFakeFilesystemJars[@]}" >&2
  exit 2
fi
okioTemporary="$(mktemp -d)"
trap 'rm -rf "$okioTemporary"' EXIT
coursier fetch --classpath -r https://kotlin.directory \
  com.squareup.okio:okio-jvm:3.4.0.2 \
  org.jetbrains.kotlinx:kotlinx-datetime-jvm:0.4.0 \
  org.junit.platform:junit-platform-console-standalone:1.10.0 \
  org.jetbrains.kotlin:kotlin-test-junit:1.8.0 > "$okioTemporary/classpath"
okioClasspath="${okioFakeFilesystemJars[0]}:$(cat "$okioTemporary/classpath")"
coursier fetch --classpath -r https://kotlin.directory \
  org.jetbrains.kotlin:kotlin-compiler-embeddable:1.8.0 > "$okioTemporary/compiler"
java -Xmx2g -cp "$(cat "$okioTemporary/compiler")" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler \
  -no-stdlib -no-reflect -jvm-target 1.8 -cp "$okioClasspath" -d "$okioTemporary/classes" \
  "$okioRoot"/backports/fakefilesystem-3.4.0/tests/*.kt
javac -J-Xmx256m -cp "$okioClasspath" -d "$okioTemporary/classes" \
  "$okioRoot"/backports/fakefilesystem-3.4.0/tests/*.java
java -Xmx256m -cp "$okioTemporary/classes:$okioClasspath" \
  org.junit.platform.console.ConsoleLauncher execute \
  --select-method DanglingAdmissionReviewTest#moveAdmissionPreservesUnrelatedDirectory --select-class ReadLookupReviewTest --select-class SymlinkResolutionBoundaryTest \
  --select-class DirectoryProof --select-class DirectoryConcurrencyTest \
  --select-class NeighborEntryReviewTest --select-class EntryAdmissionConcurrencyTest --select-class EntryAdmissionBoundaryTest --select-class EntryMetadataAdmissionTest \
  --select-class FakeFileSystemBulkReadTest \
  --select-class FakeFileSystemAppendWorkTest --select-class FakeFileSystemContentOwnershipTest \
  --select-class okio.fakefilesystem.FakeFileSystemRegistryTest \
  --select-class RegistryConcurrencyTest --reports-dir "$okioReports" --disable-banner
