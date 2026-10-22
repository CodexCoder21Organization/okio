# Evidence for https://github.com/CodexCoder21Organization/okio/pull/9

Throwaway evidence branch. Nothing here is meant to be merged.

## Red / green / mutation controls

GitHub Actions run: https://github.com/CodexCoder21Organization/okio/actions/runs/39861103040
(workflow on branch `gha-run/ok1-evidence`, commit 2aacf46e). Every job checks out the exact SHA
shown, applies the named mutation (if any), runs
`./gradlew -Dkjs=false -Dknative=false -Dkwasm=false -Dtest.java.version=19 --continue <args>`
on JDK 19 (zulu), and saves `console.txt`, `summary.txt` (per-suite counts, `RESULT` lines and
every failure message), `run-info.txt` and the raw JUnit XML.

Mutations:
- `stock-source`: `FakeFileSystem.kt` restored from tag 3.10.0, new tests kept.
- `unlocked-observers`: final head with the lock removed from `openPaths` and the
  `checkNoOpenFiles` snapshot only.

| Job | Source SHA | Mutation | Gradle args | Exit | Counts |
| --- | --- | --- | --- | --- | --- |
| [control-head-without-observer-locks](evidence-run/control-head-without-observer-locks) | `96284029` | unlocked-observers | `:okio-fakefilesystem:jvmTest --tests okio.fakefilesystem.*` | 1 | TOTAL tests=12 failures=1 errors=0 skipped=0 passed=11 |
| [control-new-tests-on-stock-3.10.0-source](evidence-run/control-new-tests-on-stock-3.10.0-source) | `1791b38e` | stock-source | `:okio-fakefilesystem:jvmTest --tests okio.fakefilesystem.*` | 1 | TOTAL tests=12 failures=11 errors=0 skipped=0 passed=1 |
| [green-fix-commit](evidence-run/green-fix-commit) | `2bc09c30` | none | `:okio-fakefilesystem:jvmTest --tests okio.fakefilesystem.*` | 0 | TOTAL tests=12 failures=0 errors=0 skipped=0 passed=12 |
| [green-head-full-jvm-build](evidence-run/green-head-full-jvm-build) | `96284029` | none | `build` | 1 | TOTAL tests=4915 failures=3 errors=0 skipped=47 passed=4865 |
| [green-head-selected](evidence-run/green-head-selected) | `96284029` | none | `:okio-fakefilesystem:apiCheck :okio-fakefilesystem:spotlessCheck :okio-fakefilesystem:jvmTest --tests okio.fakefilesystem.* :okio:jvmTest --tests okio.FakeUnixFileSystemTest --tests okio.FakeWindowsFileSystemTest --tests okio.StrictFakeFileSystemTest --tests okio.ForwardingFileSystemTest --tests okio.UseTest --tests okio.FileSystemJavaTest --tests okio.FileLeakTest` | 0 | TOTAL tests=816 failures=0 errors=0 skipped=4 passed=812 |
| [red-registry-tests-on-stock-3.10.0](evidence-run/red-registry-tests-on-stock-3.10.0) | `b096f39e` | none | `:okio-fakefilesystem:jvmTest --tests okio.fakefilesystem.*` | 1 | TOTAL tests=4 failures=3 errors=0 skipped=0 passed=1 |
| [red-tree-tests-on-registry-lock-only](evidence-run/red-tree-tests-on-registry-lock-only) | `1791b38e` | none | `:okio-fakefilesystem:jvmTest --tests okio.fakefilesystem.*` | 1 | TOTAL tests=12 failures=5 errors=0 skipped=0 passed=7 |

`green-head-full-jvm-build` checks out the source into a `src/` subdirectory, which breaks three
`canonicalizeDotReturnsCurrentWorkingDirectory` tests (JvmSystemFileSystemTest,
NioSystemFileSystemTest, NioDefaultFileSystemWrappingFileSystemTest). Those tests assert that the
working directory ends with `okio/okio`, meaning the checkout directory must be named `okio`; here it
was `src/okio` (assertion message `/home/runner/work/okio/okio/src/okio`). Those three tests pass in the
PR's own build run below, where the checkout is the repository root. Use the PR run, not this job,
for the full-suite status.

## Full suite on the final head

PR build run: https://github.com/CodexCoder21Organization/okio/actions/runs/39861049579 on
`96284029d6adf7e0518d82e427f42a2737903fb2`. This is the fork's own `build.yml` (`./gradlew build`)
with all jobs green: jvm (8, 11, 17, 19), loom, all-platforms (ubuntu-latest, windows-latest,
macos-14). Counts below are parsed from that run's uploaded JUnit artifacts. Each folder keeps the
summary and the FakeFileSystem XML (the full artifacts remain on the run page).

| Artifact | Counts |
| --- | --- |
| [test-results-all-platforms-macos-14](pr-run/test-results-all-platforms-macos-14/summary.txt) | TOTAL tests=27346 failures=0 errors=0 skipped=97 passed=27249 |
| [test-results-all-platforms-ubuntu-latest](pr-run/test-results-all-platforms-ubuntu-latest/summary.txt) | TOTAL tests=14925 failures=0 errors=0 skipped=66 passed=14859 |
| [test-results-all-platforms-windows-latest](pr-run/test-results-all-platforms-windows-latest/summary.txt) | TOTAL tests=12686 failures=0 errors=0 skipped=123 passed=12563 |
| [test-results-jvm-11](pr-run/test-results-jvm-11/summary.txt) | TOTAL tests=4915 failures=0 errors=0 skipped=47 passed=4868 |
| [test-results-jvm-17](pr-run/test-results-jvm-17/summary.txt) | TOTAL tests=4915 failures=0 errors=0 skipped=47 passed=4868 |
| [test-results-jvm-19](pr-run/test-results-jvm-19/summary.txt) | TOTAL tests=4915 failures=0 errors=0 skipped=47 passed=4868 |
| [test-results-jvm-8](pr-run/test-results-jvm-8/summary.txt) | TOTAL tests=4915 failures=0 errors=0 skipped=47 passed=4868 |

## RV47 follow-up: createDirectory keeps an existing directory (head 743488f4)

Evidence run: https://github.com/CodexCoder21Organization/okio/actions/runs/39867473016 (workflow
commit 8e17cb4e on `gha-run/ok1-evidence`). Every job is run attempt 1, with no retries.

| Job | Source SHA | Mutation | Counts |
| --- | --- | --- | --- |
| [red-directory-tests-on-stock-3.10.0-source](evidence-run-rv47/red-directory-tests-on-stock-3.10.0-source) | `743488f4` | `FakeFileSystem.kt` from 3.10.0 | 8 tests, 7 failed, 1 passed (`createDirectoryMustCreateRejectsAnExistingDirectory`, which 3.10.0 already satisfies) |
| [control-head-without-directory-preservation](evidence-run-rv47/control-head-without-directory-preservation) | `743488f4` | the `if (lookupResult?.element is Directory) return` line deleted | 8 tests, 2 failed (`createDirectoryKeepsAnExistingDirectory`, `directoryCreationAfterParentJudgedMissingKeepsSibling`), 6 passed |
| [green-head-743488f4](evidence-run-rv47/green-head-743488f4) | `743488f4` | none | 15 tests, 15 passed |

The first two jobs ran `:okio-fakefilesystem:jvmTest --tests okio.fakefilesystem.FakeFileSystemDirectoryTest --tests okio.fakefilesystem.FakeFileSystemTreeConcurrencyTest`. The green job ran `--tests okio.fakefilesystem.*`.

PR build run on 743488f4: https://github.com/CodexCoder21Organization/okio/actions/runs/39867438127.
jvm 8/11/17/19, loom, ubuntu and windows passed. macos-14 failed on one test,
`okio.PipeTest.sourceBlocksOnSlowWriter[jvm]` (`expected:<4000.0> but was:<4250.91233400011>`),
a wall-clock timing assertion in okio core's Pipe tests. That test uses no FakeFileSystem code, and
none of this PR's commits touch it. It passed on macos-14 in the previous head's run. Upstream later
rewrote these tests to use position signals instead of wall-clock time:
https://github.com/CodexCoder21Organization/okio/commit/214134f7 (#1686, first released in 3.16.2).
Because of that failure Gradle stopped early, so macos-14 reports 27086 tests instead of 27346. The
summaries for each artifact are in `pr-run-743488f4/`.
