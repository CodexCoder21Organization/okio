# Optional Okio timestamp-fixture patch review

Status: review complete. The proposed patch remains unapplied. No tests or builds were run, as requested.

## Plan

- [x] Read the repository README and check for repository-local instructions.
- [x] Read the supplied Testing and Engineering Philosophy documents.
- [x] Inspect the proposal, affected test and Gradle tasks, and the shared fixture paths used by JVM, Native, Node, and WASI tests.
- [x] Assess whether the patch changes the public assertion, adds an unsupported setup assumption, or omits fixture setup/transport.

## Findings

No concrete review gap found in this patch.

## OBSERVED

- The root README and `okio-testing-support/README.md` were read. No `AGENTS.md` exists in this checkout; the applicable global instructions were supplied in the task.
- The patch applies cleanly as a check against this checkout (`git apply --check`); it has not been applied.
- Before the patch, the common test asserts an exact `createdAt` of `2026-01-01T01:01:01Z` and an exact modified time of `2026-02-02T02:02:02Z` (`okio-testing-support/src/commonMain/kotlin/okio/AbstractFileSystemTest.kt:1486-1496`). The Gradle tasks set those values with `touch` (`okio-testing-support/build.gradle.kts:89-107`).
- The proposal keeps the exact modified-time assertion and exact creation-time comparison. It changes only the expected creation value: after setting the modified time, the Gradle task records the host NIO `creationTime` in an adjacent `created-at.txt`; the common test reads that fixture and compares `metadata.createdAt` to it. It also asserts creation and modified times differ.
- `fromIso8601String` and the `FileMetadata` timestamp accessors both reduce timestamps to milliseconds (`okio-testing-support/src/commonMain/kotlin/okio/TestingCommon.kt:51-70`), so serializing `FileTime.toInstant()` and parsing it does not add a precision mismatch to the asserted contract.
- The marker is written under the same existing `build/AbstractFileSystemTestFiles` directory as `metadata.txt`. Both existing timestamp fixture tasks are already dependencies of every `KotlinCompileTool` task. JVM, Native, Node, and WASI test configurations use the shared `okioRoot` fixture tree; the timestamp test already reads `metadata.txt` from that tree.
- `fileSystemHasGoodMetadata` and the existing skips are unchanged. The proposed test reads the marker through the public `FileSystem.read` API and keeps the public `FileSystem.metadata` assertions.

## INFER

- The changed expected value is an external fixture oracle: the Gradle/JDK code records the host filesystem value, while the test obtains Okio's public metadata for `metadata.txt`. The test still detects an adapter that reports the modified timestamp as the creation timestamp because the values are explicitly required to differ.
- The added marker has no separate transport hook to update: it is created beside a fixture that already has to be available to the same test paths, by a task already wired ahead of Kotlin test compilation.
- I found no weakening of the existing metadata assertions and no new required setup beyond the existing `touch` fixture tasks.

## Documents read

- Repository `README.md` and `okio-testing-support/README.md`.
- `/tmp/c2-TESTING.md`, including its public-API, hermetic-test, side-effect, and test-verdict guidance.
- `/tmp/c2-PHILOSOPHY.md`, including its guidance against weakening assertions and its public-API testing principle.
