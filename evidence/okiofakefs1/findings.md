# okiofakefs1 findings

Started 2026-10-17 04:52 UTC; budget 75 minutes.

## Plan
- [x] Read required guidance, prior registry fix, and directory reproduction; inspect fork.
- [x] Add public re-entry and concurrent directory tests, prove assertion failures, commit and push first milestone.
- [x] Fix directory insertion under registry lock; run mutation control, 3/3 new tests and registry neighbours.
- [x] Compare public API, publish authorized -2 artifact, open fork PR.
- [x] Commit BuildTestEmbedded documentation on wip/okiofakefs1-docs; final cleanup.

## Observations
OBSERVED: Repository search found https://github.com/CodexCoder21Organization/okio. Prior BuildTestEmbedded concurrency note and exact registry test have been read. Publication is explicitly authorized by this brief; no further approval is needed.

## FLAKES ENCOUNTERED
None so far.

## Challenges for the orchestrator
None so far.

## Scoped implementation
OBSERVED: The fork main has a modern Okio implementation plus standalone compatible 3.4.0 build scripts. The previous registry source was only retained as a patch in BuildTestEmbedded, not as a branch in this fork. Reconstructed that exact source from parent-3.4.0 plus the saved registry patch, preserving -1 behavior before any directory edit. The new backport directory will hold the real version-specific source and tests, with a reproducible skinny-JAR build script following the fork's existing backport convention.

The mechanism is an unconditional replacement of the directory child entry after a user Clock callback, and the evidence that demonstrates it is the supplied exact-core public proof (three assertion failures) plus the reconstructed createDirectory body. A second caller can create the directory and close a child before the first caller resumes its stale assignment.

## Invariants before product edits
| Resource | Creator / owner / closer | Observable contract | Re-entry / concurrent operation / restart | Public test |
| --- | --- | --- | --- | --- |
| Directory entry and children | Filesystem creates/owns; directory operations remove it | Existing directory creation preserves children | Clock re-entry and a second thread may create it first; no persistence in this library | Adopted clock proof and two-thread ordered proof |
| Must-create directory admission | Filesystem owns check and insertion | Existing entry produces IOException; file content survives | Re-entry may create target before admission | Re-entry mustCreate error and existing-file test |
| Open-file registry | Filesystem owns; handles/sources release registration | No lost registrations; checkNoOpenFiles succeeds | Existing r576 concurrent distinct handles behavior remains | Unchanged 20-trial, nine-caller, 10,000-operation r576 gate |

Required reading completed: Testing Architecture sections specified in brief; Engineering Philosophy specified sections; fork README; BuildTestEmbedded r576-fakefilesystem-concurrency.md and r576-okio-verification.md; complete directory-proof-exact-core UPSTREAM-BRIEF.md and DirectoryProof.java. No AGENTS.md exists in this fork root.

## First red milestone
OBSERVED: Native Jupiter XML in okiofakefs1-evidence/red contains 15 tests, 15 assertion failures, zero errors/skips. The supplied callback proof fails 3/3; the ordered two-thread proof fails 3/3; direct existing-directory preservation, existing-file rejection, and re-entrant mustCreate rejection each fail 3/3. Failure messages are `Directory creation must preserve a committed child. ==> expected: <true> but was: <false>`, the supplied full child-preservation assertion, and `Expected java.io.IOException to be thrown, but nothing was thrown.` No compilation failures count as verdicts.
OBSERVED: The first run uses the served -1 artifact and exact consumer core 3.4.0.2. Product source is still unchanged from the reconstructed -1 patch. No pre-existing test has been edited. Local slot acquired and released.

OBSERVED: Root creation inside lookupPath also reads the user clock after checking for an absent root. Added the same public callback proof at the root path; it fails 3/3 against -1. Expanded baseline now has 18 assertion failures / 18 rows, zero errors/skips. The correction must reuse a clock value through directory lookup and recheck root admission under the existing registry lock. This closes root and symlink callback paths rather than fixing only the final child assignment.

## Build review
OBSERVED: The atomicfu name occurs in Kotlin metadata in both the previous artifact and the candidate; javap shows JVM monitorenter/monitorexit and no linked atomicfu class. Updated the build check to inspect actual class/member constant-pool references rather than metadata strings. This is a build-script correction, not a product/test verdict. The public API comparison remains mandatory before publication.

## Mutation control
OBSERVED: Restored only the product source to the prior registry implementation, rebuilt it with the new repeatable build script, and ran all current tests against that rebuilt JAR. Native XML reports 18/18 directory assertion failures plus 1/1 passing registry workload (all 20 trials). This proves the source backport baseline itself matches the served dependency's observed defect. The fixed source was restored by an exit trap. The build's all-class public javap comparison passed on this rebuilt baseline.

## Test review, before the passing gate
OBSERVED: Existing-root mustCreate=true also incorrectly succeeds in -1 (3/3 assertion failures); the public contract requires an error for any existing directory. Added that case. The first symlink fixture used createDirectories, whose initial metadata traversal completed the callback before it reached createDirectory; that characterization passed 3/3. Changed this new, unpushed fixture to call public createDirectory directly, which exercises the callback within the directory operation being corrected. No upstream/existing test is edited or weakened.
OBSERVED: Reused the previous three registry callback/snapshot test functions verbatim as additional neighbour coverage. Their original Kotlin test source is retained so the earlier -1 admission and close guarantees are checked alongside the 20-trial workload.

## Expanded red-first coverage
OBSERVED: Final public directory tests now have 27/27 assertion failures against the exact published -1 artifact, zero errors/skips. This is nine scenarios, each repeated three times: the supplied callback proof; ordered two-thread child preservation; simultaneous mustCreate callers (both admitted by -1); existing directory; existing file; callback mustCreate; root callback; existing-root mustCreate; and callback through a symlink parent. The simultaneous case forces both callers to reach their public clock before completing admission. One Java checked-exception compilation adjustment was required; that no-verdict compilation attempt is excluded.

## Complete mutation and independent reviews
OBSERVED: Product-only restoration with all final tests gives 27/27 intended directory assertion failures, plus all four registry neighbours passing (one 20-trial workload and three unchanged historical Kotlin tests). Native JUnit counts total 31 rows, zero errors/skips. No internal retries are enabled by this native runner.
OBSERVED: Dedicated test review checked public-only access, fresh in-memory state, deterministic callback/thread ordering, executor/handle cleanup, roots, files, symlinks, and mustCreate semantics. Dedicated code review checked that all directory lookup/check/insertion is held under openFilesLock, the clock is read before this transaction, its value propagates through symlink traversal, and root admission rechecks under the same lock after any callback. No file-content or deletion synchronization claim is made. Public API and served artifact checks remain required before release.

## 04:58 UTC — final source and GitHub gate submitted
OBSERVED: Final candidate is https://github.com/CodexCoder21Organization/okio/commit/b942f8201b3f35605a5a8c3849026c5dc978607e . A temporary wip/gha-run/okiofakefs1 workflow checks out this exact SHA with full history, builds the artifact, checks all public JVM signatures and compiled class names against -1, and runs the 27 directory rows plus four registry neighbours. The workflow exists only on its temporary branch, never on the PR branch. No remote buildtest submission and no local full suite.
OBSERVED: A fresh BuildTestEmbedded clone is inside this lane's .git/consumer solely for the authorized documentation-only branch. No other lane directory is used. No consumer dependency or test edit is planned here.

OBSERVED: Targeted gate URL is https://github.com/CodexCoder21Organization/okio/actions/runs/39004533954 . It is bounded by a 15-minute workflow limit, a 600-second build command and a 180-second test command. The accepted gate will count native rows, including any attempt-history siblings; no later successful retry replaces a first failure.
OBSERVED: Mutation XML has 28 Jupiter rows (27 intended directory failures and the passing registry workload) plus three passing Vintage registry rows. Both engines report zero errors/skips. Evidence checkpoint is https://github.com/CodexCoder21Organization/okio/tree/wip/okiofakefs1-evidence/evidence/okiofakefs1 . Temporary local build/test directories have been removed after this run.

## 05:00 UTC — final gate met
OBSERVED: https://github.com/CodexCoder21Organization/okio/actions/runs/39004533954 completed successfully. Downloaded native XML has 28 Jupiter rows and three Vintage rows, all successful FIRST attempts, zero failures/errors/skips and zero attempt-history elements. All nine directory methods have exactly three rows each (27/27); each of the four registry neighbour methods has exactly one row (4/4), including 20/20 internal trials and all 1.8 million operations in concurrentDistinctFileOpenClose. This is the accepted gate; no further verification batch will run.
OBSERVED: The build checked identical compiled class names and identical javap public signatures for every -1 class. No linked atomicfu class/member reference remains. Tested JAR SHA-256: 21e60aadff3bf4906847103742c28b6a197349ecf58f132bd4eebf07e4c9e7a4. POM SHA-256: 1113b0475c7fe40b6f36ae6302895487c489831adbcea234ff5c2a58f4adb67d. The POM changes only the project version from -1 to -2; all four dependency versions are unchanged.

## Publication complete
OBSERVED: The artifact-publisher 0.0.6 command exited 0 using --artifact-dir and the authorized HTTP upload API. Served JAR and POM both return HTTP 200 and match the exact tested hashes above. Published coordinate: com.squareup.okio:okio-fakefilesystem-jvm:3.4.0-r576-registry-lock-2. No new test execution followed the accepted gate.

## Challenges for the orchestrator — GitHub PR creation
OBSERVED: `gh pr create` returned `HTTP 502: 502 Bad Gateway (https://api.github.com/graphql)`. A subsequent branch lookup reported no pull requests. Publication is complete; the remaining write is the authorized fork PR. Checking via REST before retrying avoids creating a duplicate if the first response was lost. No CI job is requeued and no product/test change is involved.

## Completed deliverables
OBSERVED: Fork PR is OPEN at https://github.com/CodexCoder21Organization/okio/pull/5 with exact candidate b942f8201b3f35605a5a8c3849026c5dc978607e. Its description was read back and compared with the local body; the required campaign URL is unchanged. The second creation request also returned HTTP 502, but readback confirmed that the write completed. No duplicate PR was created. No merge, queue, mark-ready or deployment action was taken. The orchestrator owns the PR full-suite CI wait.
OBSERVED: Documentation-only commit https://github.com/CodexCoder21Organization/BuildTestEmbedded/commit/7ba7f424c36e22fa938ce3a13b8ff0594dfa29bd is pushed on https://github.com/CodexCoder21Organization/BuildTestEmbedded/tree/wip/okiofakefs1-docs . Exactly one file changes: docs/r576-fakefilesystem-concurrency.md gains a short section describing defect 2, the -2 artifact, the fork PR and gate counts. No consumer PR, dependency edit or consumer test run is part of this lane.

## Product file audit
README.md documents the compatible artifact, behavior and build/test commands.
backports/fakefilesystem-3.4.0/src/FakeFileSystem.kt retains the prior registry fix and corrects directory and root admission.
backports/fakefilesystem-3.4.0/src/FileMetadataCommon.kt preserves the unchanged 3.4.0 metadata companion source required to build the module.
backports/fakefilesystem-3.4.0/tests/DirectoryProof.java adopts the supplied public callback reproduction unchanged.
backports/fakefilesystem-3.4.0/tests/DirectoryConcurrencyTest.java covers the remaining eight directory scenarios through public APIs.
backports/fakefilesystem-3.4.0/tests/FakeFileSystemRegistryTest.kt retains the historical registry callback and snapshot tests byte-for-byte.
backports/fakefilesystem-3.4.0/tests/RegistryConcurrencyTest.java preserves the 20-trial, nine-caller, 10,000-operation r576 workload.
scripts/buildFakeFilesystemDirectoryBackport.py builds the skinny JAR and unchanged-dependency POM and verifies the previous checksum, class names and public API.
scripts/testFakeFilesystemDirectoryBackport.bash runs the native public tests with exact consumer core and writes XML.
OBSERVED: No existing upstream test, timeout, iteration count or assertion changed. Source branch contains only source, tests, build/test scripts and README. No investigation file, log, XML or JAR is in the PR.

## Brief audit
- DONE: Required reading and prior -1 source/publication verification.
- DONE: First pushed milestone is a demonstrated-red public test; final red-first 27/27.
- DONE: Clock read before locked directory transaction; existing directories/children preserved; existing files and must-create admissions rejected; root/symlink paths covered.
- DONE: Product-only mutation control red 27/27, registry neighbours green 4/4.
- DONE: Exact-source GitHub gate green 27/27 new rows plus 4/4 neighbours; no later positive batch.
- DONE: Public javap API and compiled class names identical to -1; POM dependency versions unchanged.
- DONE: Authorized -2 publication; served JAR/POM HTTP 200 and hashes identical to tested artifact.
- DONE: Fork PR open; consuming documentation branch committed/pushed without a PR as requested.
- DONE: No production host connection, buildtest submission, nested model invocation, handoff action or challenge-service write.

## FLAKES ENCOUNTERED — final
No unexpected first-attempt test failure. Every red directory row was a registered defect reproduction. All registry neighbours and final-source gate rows passed their first attempt.
 README.md                                          |  27 +
 .../fakefilesystem-3.4.0/src/FakeFileSystem.kt     | 803 +++++++++++++++++++++
 .../fakefilesystem-3.4.0/src/FileMetadataCommon.kt |  47 ++
 .../tests/DirectoryConcurrencyTest.java            | 221 ++++++
 .../fakefilesystem-3.4.0/tests/DirectoryProof.java |  47 ++
 .../tests/FakeFileSystemRegistryTest.kt            | 152 ++++
 .../tests/RegistryConcurrencyTest.java             |  58 ++
 scripts/buildFakeFilesystemDirectoryBackport.py    |  86 +++
 scripts/testFakeFilesystemDirectoryBackport.bash   |  31 +
 9 files changed, 1472 insertions(+)

## Final cleanup
OBSERVED: Both working trees are clean. The temporary wip/gha-run/okiofakefs1 branch is deleted. All tracked local commands and the GitHub gate are terminal. A ps process audit found no remaining lane-owned background process; no build slot is held. No positive verification ran after the accepted gate.
