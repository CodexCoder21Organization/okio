# C2 evidence manifest

Snapshot refreshed: 2026-10-22. This record-only branch is based on Okio feature
commit `9ff82726b0d4c0e0b2b0a2100187f2faa26445fc`. It contains no changes to feature,
production, or test sources. The 45 listed evidence files were copied byte-for-byte;
three rolling reports were refreshed and six new named artifacts were added. XML
reports, stack text, and JSON snapshots were not rewritten. `MANIFEST.md` is an index
and provenance note, not a test result.

## Verification record

The first native Okio full run stopped at `:okio:jvmTest` with exit 1 after about
3m03s. The historical aggregate summary records 5,102 cases across 259 XML files:
4,988 passed, 10 failed, and 104 existing skips. Eight failures were
`Cannot run program "zip"` (the task-local `zip` tool was not available to that run).
The remaining two were `fileMetadataTimestampsAreDistinct` in
`NioSystemFileSystemTest` and `NioDefaultFileSystemWrappingFileSystemTest`: Linux
`touch -t` set modified time but could not set birth time, so the old Jan 1 expectation
differed from the recorded Oct 21 birth time. This is historical failing evidence,
not the final verdict.

On final source head `9ff82726b0d4c0e0b2b0a2100187f2faa26445fc`, the final native
JVM full suite completed with exit 0: 5,121 cases across 261 XML files, 5,017 passes,
0 failures, 0 errors, and 104 existing platform skips. The three new directory
scenarios each passed 5/5 first attempts (15/15 total); the final full report also
has one passing record for each scenario. The initial five targeted fixture XML
reports retain their per-run details. No suite was rerun while preparing this
checkpoint.

ContainerNursery's final-head full suite on `2dc18ad8cec0c8c1183b48bf273a6e9d54c9b51c`
completed with exit 0: 857/857 successful records, no failures, skips, or retry
histories. Both newly covered scenarios passed. Its regular PR is open at
https://github.com/CodexCoder21Organization/ContainerNursery/pull/702. The suite
command was the repository's single full local test suite after the final-head
fetch/rebase and exact-head checks; targeted scenario commands/results are retained
in `r1-C2-cn-retry.md` and the ten final-head XML files.

SimpleFileSystemServiceServer's one required final full suite started on unchanged
head `ede34dcb8d6d5c71d69f844a97953a59cd12ec5d`, tree
`181dd763798d17b5c1c5c4b02e0db26cb3afe0aa`, with the unchanged command
`env COURSIER_EXTRA_MIRRORS=/tmp/c2-local-okio-mirrors.properties scripts/test.bash --local --test . --log <out/sfs-full.xml>`.
The wrapper exited 137 and produced no aggregate XML, so this is neither a passing
nor a failing test verdict. The launcher had reported PID 77239 killed. An orphaned
128 MiB test JVM, PID 42701, was still running the concurrent-publish scenario after
the launcher exited; its stdout/stderr pipes had no queued data or backing file, so
no child result was recoverable. The worker captured the process and resource facts,
then stopped only that verified orphan. One mandatory full-suite continuation is
queued with the same source/test settings, existing lock and deadline bounds, and
resource telemetry. The cgroup reached its 10 GiB memory cap; cumulative OOM
counters had no pre-run baseline, and no kernel record was available, so the exact
historical kill attribution for PID 77239 is unproven. No test assertion result or
aggregate XML exists for the interrupted invocation.

## Scopes and commands represented

- ContainerNursery: `testRelayRetryOnlyBeforeFrameAdmission` and
  `testRelayPeerReconnectSurvivesStaleRemovalEvidence`. The two `cn-gap*-prefx-first`
  XMLs are first-attempt old-resolver controls. The ten
  `cn-final-*-2dc18ad-*.xml` reports are five final-head attempts per scenario, each
  recorded as an attempt-1 pass. `cn-final-full-2dc18ad.xml` is the final-head full
  suite report. Targeted commands and their exact run history are in
  `r1-C2-cn-retry.md`.
- SimpleFileSystemServiceServer: `testConcurrentMultiFilePublishOverUrlKeepsMoveWorkBounded`
  was run via `scripts/test.bash --local --test
  testConcurrentMultiFilePublishOverUrlKeepsMoveWorkBounded --log <xml>`. The records
  include the old Okio 3.4.0 control, the Embedded 0.0.32 control, and five passing
  runs with Okio 3.4.0.3. The incident file captures the full-suite command and
  termination facts. The continuation has no result yet.
- Okio: the full native JVM suite scope is `:okio:jvmTest`; the recorded full run
  also includes `:okio:spotlessKotlinCheck`. Five target runs are represented by
  `okio-final-1.xml` through `okio-final-5.xml`, and `okio-pre-fix.xml` preserves
  pre-fix failure evidence. The captured full-suite XML summaries retain aggregate
  counts and failure stacks.

## CI handoff status

The Okio PR remains open at https://github.com/CodexCoder21Organization/okio/pull/7.
Its native Actions run ended in `startup_failure` before jobs because repository
rules reject two existing workflow actions; no CI rerun or repository-rule/workflow
mutation was made. The fresh PR-state and run-state JSON, the blocker report, and the
handoff report are included. The service handoff URL is
url://handoff/handoffs/hf-2026-10-22-restore-allowed-actions-for-the-okio-native-checks.

## Local mirror note

`c2-local-okio-mirrors.properties` and `c2-local-okio-repository.py` document a
local, temporary Coursier mirror used only for SFS verification. The configuration
redirected the `kotlin.directory` Maven repository to a loopback HTTP server on
`127.0.0.1:39679`. That server served only Okio 3.4.0.3 JAR/POM/checksum files from
`/tmp/c2-okio-3.4.0.3` and redirected other requests to the same kotlin.directory
URLs. The setup was enabled only for relevant commands through
`COURSIER_EXTRA_MIRRORS=/tmp/c2-local-okio-mirrors.properties`; it did not publish an
artifact or alter source. The mirror configuration and server code are copied as
historical evidence; the artifact, running server, its port file, and caches are
not included. Credential-pattern review found no GitHub token, bearer value,
private-key header, URL user-info, or credential-assignment matches in the copied
sources.

## Contents

`$SCRATCHPAD_OUT` means
`/tmp/claude-501/-code/2abf611d-5e04-4919-84a8-d7b72869d40c/scratchpad/out`.
The `/tmp` sources are shown explicitly. File hashes cover the byte-exact copy.

| File | Source | Bytes | SHA-256 |
| --- | --- | ---: | --- |
| `c2-local-okio-mirrors.properties` | `/tmp/c2-local-okio-mirrors.properties` | 117 | `d2333cb3271556dfdfd8d48302f6e38a469e4f7abba636d29eb8b2b8d6a7f7bb` |
| `c2-local-okio-repository.py` | `/tmp/c2-local-okio-repository.py` | 2704 | `82c8c8e010af6048f9f02be68c303397bf306a9c14ac3889ad3dab2d318935f0` |
| `c2-okio-actions-blocked-report.md` | `/tmp/c2-okio-actions-blocked-report.md` | 734 | `4def3182068dc5cfd3ff33d7e427ff2d75e29498c0a4bf6a454ea9293f686d7b` |
| `c2-okio-actions-blocker.md` | `/tmp/c2-okio-actions-blocker.md` | 4224 | `01fe1476253a60a0593cdf6132cfcec74995905a3a8a62c0aebca7c93e0e4d02` |
| `c2-okio-final-full-xml-summary.txt` | `/tmp/c2-okio-final-full-xml-summary.txt` | 80 | `e25f5ad9a7251d875cf09c84c558406e0e6751208753dd89c73eeab427ff8c26` |
| `c2-okio-full-xml-summary.txt` | `/tmp/c2-okio-full-xml-summary.txt` | 15412 | `e11ba5508c8258249fba0dcf12c4670e3c6f796b359e75ac908f13f64365bbed` |
| `c2-okio-handoff-run.json` | `/tmp/c2-okio-handoff-run.json` | 54 | `ff0b6a9fee55ffc362e68f25643026f32395d21e3f3f9008fc34ecc715ffe390` |
| `c2-okio-handoff-state.json` | `/tmp/c2-okio-handoff-state.json` | 139 | `8874a99c7e38a042b0b94dc06f7b331c28109181f1d6510fd082caf86f95abc2` |
| `c2-okio-pr-body.md` | `/tmp/c2-okio-pr-body.md` | 5490 | `1fd11af4b5bdaa23f0e5b9142749883f0c3e1c88d8873f2230f76cf9936f77c8` |
| `cn-final-full-2dc18ad.xml` | `/tmp/claude-501/-code/2abf611d-5e04-4919-84a8-d7b72869d40c/scratchpad/out/cn-final-full-2dc18ad.xml` | 162417 | `cab6f4b1857a6a25d6fbb80bbf36d28a8a2c83966ba2df94343950f59762bd42` |
| `cn-final-testRelayPeerReconnectSurvivesStaleRemovalEvidence-2dc18ad-1.xml` | `/tmp/claude-501/-code/2abf611d-5e04-4919-84a8-d7b72869d40c/scratchpad/out/cn-final-testRelayPeerReconnectSurvivesStaleRemovalEvidence-2dc18ad-1.xml` | 439 | `a7cf049cf4f29b1027e78d8389e5b94ca36a3d49021d86a2b13dc2719dcf9e16` |
| `cn-final-testRelayPeerReconnectSurvivesStaleRemovalEvidence-2dc18ad-2.xml` | `/tmp/claude-501/-code/2abf611d-5e04-4919-84a8-d7b72869d40c/scratchpad/out/cn-final-testRelayPeerReconnectSurvivesStaleRemovalEvidence-2dc18ad-2.xml` | 439 | `d1b2f39955cbe8f15866d9ec9402506a0a1d074f6a6c8361124070b845f3e547` |
| `cn-final-testRelayPeerReconnectSurvivesStaleRemovalEvidence-2dc18ad-3.xml` | `/tmp/claude-501/-code/2abf611d-5e04-4919-84a8-d7b72869d40c/scratchpad/out/cn-final-testRelayPeerReconnectSurvivesStaleRemovalEvidence-2dc18ad-3.xml` | 439 | `562d75bd812ab0251435747b7aa87d9392b3ae98d69925c0c67203341ba8b14b` |
| `cn-final-testRelayPeerReconnectSurvivesStaleRemovalEvidence-2dc18ad-4.xml` | `/tmp/claude-501/-code/2abf611d-5e04-4919-84a8-d7b72869d40c/scratchpad/out/cn-final-testRelayPeerReconnectSurvivesStaleRemovalEvidence-2dc18ad-4.xml` | 439 | `a2e557fe09aaa4790d4034271f69392d9a1d05ab0a6dee35e1ff1229beff2b80` |
| `cn-final-testRelayPeerReconnectSurvivesStaleRemovalEvidence-2dc18ad-5.xml` | `/tmp/claude-501/-code/2abf611d-5e04-4919-84a8-d7b72869d40c/scratchpad/out/cn-final-testRelayPeerReconnectSurvivesStaleRemovalEvidence-2dc18ad-5.xml` | 439 | `261b538818a0b171ef91d6658750a6aa5e74b6bdf30201c96ea5890c13c8b8f0` |
| `cn-final-testRelayRetryOnlyBeforeFrameAdmission-2dc18ad-1.xml` | `/tmp/claude-501/-code/2abf611d-5e04-4919-84a8-d7b72869d40c/scratchpad/out/cn-final-testRelayRetryOnlyBeforeFrameAdmission-2dc18ad-1.xml` | 416 | `db091f016292d213d4a97ab5777ce355163084be56229f51439a6b7477b2bdbb` |
| `cn-final-testRelayRetryOnlyBeforeFrameAdmission-2dc18ad-2.xml` | `/tmp/claude-501/-code/2abf611d-5e04-4919-84a8-d7b72869d40c/scratchpad/out/cn-final-testRelayRetryOnlyBeforeFrameAdmission-2dc18ad-2.xml` | 415 | `ed592c9ef82198eb20f88a0b003d175635c632d5aff4972b7b2072a3ff18d545` |
| `cn-final-testRelayRetryOnlyBeforeFrameAdmission-2dc18ad-3.xml` | `/tmp/claude-501/-code/2abf611d-5e04-4919-84a8-d7b72869d40c/scratchpad/out/cn-final-testRelayRetryOnlyBeforeFrameAdmission-2dc18ad-3.xml` | 415 | `8a1fd5f7fa831ad93a116bcf325f7862eddb2eb907085520b41e013a1bb5bb7d` |
| `cn-final-testRelayRetryOnlyBeforeFrameAdmission-2dc18ad-4.xml` | `/tmp/claude-501/-code/2abf611d-5e04-4919-84a8-d7b72869d40c/scratchpad/out/cn-final-testRelayRetryOnlyBeforeFrameAdmission-2dc18ad-4.xml` | 415 | `bf8146393f0c0d617fe0d2ba539e8d4fa86ef93fa938e486bf21a851a7f47a54` |
| `cn-final-testRelayRetryOnlyBeforeFrameAdmission-2dc18ad-5.xml` | `/tmp/claude-501/-code/2abf611d-5e04-4919-84a8-d7b72869d40c/scratchpad/out/cn-final-testRelayRetryOnlyBeforeFrameAdmission-2dc18ad-5.xml` | 415 | `b7ff19ad9dd89c697112010a4574b9897718f573a48f3759896509a9281e3ce1` |
| `cn-gap3-prefx-first.xml` | `/tmp/claude-501/-code/2abf611d-5e04-4919-84a8-d7b72869d40c/scratchpad/out/cn-gap3-prefx-first.xml` | 148164 | `fc7d8d0fd219c423840998cc37cfa4acd81938516f49790928fd73a475fb929a` |
| `cn-gap4-prefx-first.xml` | `/tmp/claude-501/-code/2abf611d-5e04-4919-84a8-d7b72869d40c/scratchpad/out/cn-gap4-prefx-first.xml` | 112241 | `a1044cb7acaf93f26f5e004c75cf4d418b8ac786f13fc5d639a7677267647e9e` |
| `okio-final-1.xml` | `/tmp/c2-okio-fixture-final-1/TEST-junit-jupiter.xml` | 6713 | `5e7b901d85f05a5251354c181d927dc3f25e0aca7e1599e2f0a726a59f8995bc` |
| `okio-final-2.xml` | `/tmp/c2-okio-fixture-final-2/TEST-junit-jupiter.xml` | 6713 | `488d0868c6efbb27ee9e769a51c5eb5aab9f064b50fbc81b644c606d43c76043` |
| `okio-final-3.xml` | `/tmp/c2-okio-fixture-final-3/TEST-junit-jupiter.xml` | 6713 | `301d4c0da7414f7dbd856696b539c4c961acb9e9bd8642c7aa9b11f90c4fce10` |
| `okio-final-4.xml` | `/tmp/c2-okio-fixture-final-4/TEST-junit-jupiter.xml` | 6709 | `e3d8b9f5590e861cd011d7f82a25fad43d744c2272060ef9a0ff897ae72ce0a4` |
| `okio-final-5.xml` | `/tmp/c2-okio-fixture-final-5/TEST-junit-jupiter.xml` | 6713 | `37b375ee1bd2d360eebbb7e0f1f4fda31e04290ccf6993c8796a5d2052148d6f` |
| `okio-pre-fix.xml` | `/tmp/c2-okio-review-baseline/TEST-junit-jupiter.xml` | 9713 | `c048df0828099262ab430f9d9d788dacf9a480a9b923380097f025fc2e22e74f` |
| `r1-C2-cn-retry.md` | `/tmp/claude-501/-code/2abf611d-5e04-4919-84a8-d7b72869d40c/scratchpad/out/r1-C2-cn-retry.md` | 46837 | `e157bc57edeb327bd2af2da9ab9b543f50ec8b8522ea48fbb55cdfe895a1ac31` |
| `r1-C2-fixture-review.md` | `/tmp/claude-501/-code/2abf611d-5e04-4919-84a8-d7b72869d40c/scratchpad/out/r1-C2-fixture-review.md` | 3475 | `7833991c2ad4c6fdebf53d246ef4cb9ab71c3613458c9a4b1227b8fe7886f1cc` |
| `r1-C2-okio-review.md` | `/tmp/claude-501/-code/2abf611d-5e04-4919-84a8-d7b72869d40c/scratchpad/out/r1-C2-okio-review.md` | 4673 | `cbe3fad7b82493f8310d5cd5b24076f33a027d80e2e87fda354c504e33cca9a5` |
| `r1-C2-okio.md` | `/tmp/claude-501/-code/2abf611d-5e04-4919-84a8-d7b72869d40c/scratchpad/out/r1-C2-okio.md` | 2973 | `108b0dc87b9d7e836ad523709abad9ca70de707e7309c1c0d4b462e3bceb6dc5` |
| `r1-C2-sfs-review.md` | `/tmp/claude-501/-code/2abf611d-5e04-4919-84a8-d7b72869d40c/scratchpad/out/r1-C2-sfs-review.md` | 5971 | `9ef5babedb48af34b04df0455099cef25ce0d635ecfea94ce942fd49ee98d670` |
| `r1-C2-sfs.md` | `/tmp/claude-501/-code/2abf611d-5e04-4919-84a8-d7b72869d40c/scratchpad/out/r1-C2-sfs.md` | 33098 | `aa230a154ede2ba603436d1b3dd5e636798349dc9250c6801f0ca14c0164f9d3` |
| `r1-C2.md` | `/tmp/claude-501/-code/2abf611d-5e04-4919-84a8-d7b72869d40c/scratchpad/out/r1-C2.md` | 84657 | `69488ce28f2561c11336b96ea8b5ce90df6c07e6c0cbcf1f786c54bf49c53b98` |
| `sfs-current-okio-3.4.0.3-local-first.xml` | `/tmp/claude-501/-code/2abf611d-5e04-4919-84a8-d7b72869d40c/scratchpad/out/sfs-current-okio-3.4.0.3-local-first.xml` | 816 | `042ff570458aedf4e9025c399343779518c9663adace227a533eb63466483ad7` |
| `sfs-embedded-0.0.32-control.xml` | `/tmp/claude-501/-code/2abf611d-5e04-4919-84a8-d7b72869d40c/scratchpad/out/sfs-embedded-0.0.32-control.xml` | 10324 | `a5e90957c225a22b71700e88cd8c883def860dc9db99537bd9c618f6a4c154ee` |
| `sfs-embedded-0.0.32-first-child.txt` | `/tmp/claude-501/-code/2abf611d-5e04-4919-84a8-d7b72869d40c/scratchpad/out/sfs-embedded-0.0.32-first-child.txt` | 22613 | `0e3f0438784ad2390aa00134e8b8e50b825981ecec5a19cdcf02c47177e33613` |
| `sfs-final-2.xml` | `/tmp/claude-501/-code/2abf611d-5e04-4919-84a8-d7b72869d40c/scratchpad/out/sfs-final-2.xml` | 813 | `2cf2af3a56c6c4e74c62a88f1715deff0afd872c4dc5f2a7a2ea7884940b0cfc` |
| `sfs-final-3.xml` | `/tmp/claude-501/-code/2abf611d-5e04-4919-84a8-d7b72869d40c/scratchpad/out/sfs-final-3.xml` | 812 | `40e5705495e04606ccf5e9fa0392650164efa8b46476b561c6e14996986b0807` |
| `sfs-final-4.xml` | `/tmp/claude-501/-code/2abf611d-5e04-4919-84a8-d7b72869d40c/scratchpad/out/sfs-final-4.xml` | 812 | `cfe0d210a628f05c98d81d71f08088a497a020ffbb1f033327b46b66d4dca846` |
| `sfs-final-5.xml` | `/tmp/claude-501/-code/2abf611d-5e04-4919-84a8-d7b72869d40c/scratchpad/out/sfs-final-5.xml` | 812 | `9f3e6e6ed7e43098ce76774a33d3ed7784f836f63935e53370556a1f0703c187` |
| `sfs-full-incident.txt` | `/tmp/claude-501/-code/2abf611d-5e04-4919-84a8-d7b72869d40c/scratchpad/out/sfs-full-incident.txt` | 3860 | `99ed367bcef1498f794006d502e68b0043af18aaeda74d3d4db858773d6bb5c9` |
| `sfs-old-okio-3.4.0-control.xml` | `/tmp/claude-501/-code/2abf611d-5e04-4919-84a8-d7b72869d40c/scratchpad/out/sfs-old-okio-3.4.0-control.xml` | 14068 | `5c92d0fd6ac408093da74e89b0b380cc0ca16c29250a24066ff0b3d8b0821be4` |
| `sfs-old-okio-first-child.txt` | `/tmp/claude-501/-code/2abf611d-5e04-4919-84a8-d7b72869d40c/scratchpad/out/sfs-old-okio-first-child.txt` | 23099 | `747a9d4e1992fbdd15dfddfb7bbf464436e05ce8045efede101829fac7d21735` |

## Preserved source formatting

`c2-okio-full-xml-summary.txt` has 16 trailing-whitespace lines in the original
summary. They are preserved byte-for-byte as part of the captured failure stacks;
`sfs-full-incident.txt` also has three trailing-whitespace lines in its captured
`fd0`–`fd2` records. They are preserved byte-for-byte. `git diff --cached --check`
reports these captured lines. No evidence text was trimmed to silence that report.

## Excluded

No JARs, build trees, caches, HTML pages, unlisted full-suite XML files, or unrelated
lane logs were copied. This branch is for evidence only. It has not been opened as
a pull request and has no merge, publication, CI, or deployment action attached.
