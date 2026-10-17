# okiofakefs3 findings
Started 2026-10-17 09:12 UTC; 60-minute budget.

## Plan
- [done] Read guidance and review evidence; adopt five public proofs and record red 3/3 each on -3; push first milestone.
- [done] Correct lookup transactions and matching move destination; review tests and code; product-only red control.
- [done] Exact-source GitHub-machine gate: five new scenarios 3/3 and prior 70/70; all-class javap against -3.
- [done] Publish authorized -4 gate bytes; verify served checksums; update existing PR, push evidence, clean up.

## Reading
OBSERVED: Read review findings revokio2, prior okiofakefs2 findings, reviewer REPRODUCE and both proof sources, repository README, and Testing Architecture/Engineering Philosophy guidance. No root AGENTS.md exists. Fresh clone and initial fetch/rebase completed; branch wip/okiofakefs1 is at ba235959 and PR is OPEN. github-repos skill applied; publication skill read, with publication already explicitly granted by the task.
The mechanism is lookupPath retaining a retired symlink across clock callbacks and atomicMove combining a resolved destination parent with the original basename, and the evidence that demonstrates it is the review's 15 native assertion failures plus the current lookup/read/move ordering. Reproducing those five scenarios before product edits.

## Invariants before product edits
Every mutator resolves symlinks, validates the target, and applies its change inside ONE critical section; a dangling link is an error for operations requiring an existing target and never a redirect to a sibling. createDirectories returns only when every component exists as a directory under the lock, using current primitive lookup results; no atomicity of the inherited whole composition is claimed against independent concurrent edits.
| Resource | Ownership/lifecycle | Required observation and re-entry rule | Public proof |
|---|---|---|---|
| Move entries | Filesystem owns entries; delete retires them | final destination entry is checked and replaced consistently, unrelated populated directory survives callback-created dangling link | moveAdmissionPreservesUnrelatedDirectory |
| Metadata read | Filesystem owns elements and snapshots | clock completes before locked current lookup; no retired symlink metadata | metadataDoesNotReturnAnEntryNeverAtTheRequestedPath |
| Canonical path | Filesystem owns tree | absent current target raises error after callback | canonicalizeRejectsAPathAbsentThroughoutItsCallback |
| Directory listing | Filesystem owns children, caller receives list | lookup/access/result share lock after callback | listDoesNotReturnAChildNeverAtTheRequestedPath |
| Inherited directory creation | Filesystem owns directories | current metadata must not accept retired-parent child | createDirectoriesCreatesTheRequestedDirectory |
No persistence/restart state exists. User callbacks must remain outside openFilesLock; all recursive lookup uses one captured time. Move replaces the final symlink entry itself, consistent with the path-operation contract; it does not require an existing referent.

## FLAKES ENCOUNTERED
None observed.

## Challenges for the orchestrator
None observed.

## First red milestone — 09:15 UTC
OBSERVED: Published -3 checksum confirmed as 3b04c346df6fb6ebe04c80d7d5bec07e2410b0be9f588a385537ff2b353c1e4a. Reviewer proof source files adopted byte-for-byte; runner selects only the counted move method and four read methods. Native XML: 15/15 expected assertion failures (five scenarios x3), 70/70 unchanged prior rows pass, zero errors/skips/attempt-history. Move asserts unrelated directory type/child retention; read methods assert absent metadata, canonicalization error, empty current listing, and actual requested directory creation. No product edit preceded this verdict. Local slot released; native XML and full assertion output retained separately in evidence.

## Boundary red milestone — 09:16 UTC
OBSERVED: Added three public boundary scenarios: existing dangling move destination, existing live move destination with differing referent basename, and canonicalization of a final dangling link. All three fail their assertions 3/3 on -3. Combined native rows: 24/24 new assertion failures, 70/70 prior passes, no errors/skips/history. Existing test source and assertions unchanged. The saved red-native XML was replaced by this expanded red run; first-red-native evidence therefore contains the expanded 94-row report, while red-console retains the original 85-row run. Both report the five reviewer scenarios red 3/3. No product edit yet.

## 09:18 UTC — implementation and control in progress
OBSERVED: The product-only control restores only FakeFileSystem.kt to ba235959 while keeping all new tests and -4 scripts. Its rebuilt JAR is byte-identical to published -3 and the build's all-class javap check passes. The native control is running; an exit trap restores fixed source and releases the local slot.
OBSERVED: Read transactions now capture the clock before locking, then resolve the current path and construct the result under openFilesLock. lookupPath requires a captured Instant, so no recursive path can call the clock under the lock. Move uses no-follow final lookup for its destination and both moves/inserts use the segment returned alongside their parent. Dangling referents return no lookup rather than a retained Symlink object paired with a different parent.
OBSERVED: Status-report skill read and applied as durable findings updates; lane-specific no-handoff and no-extra-agent instructions take precedence. Publication skill applied with the task's explicit -4 authorization.

## 09:19 UTC — product-only control and dedicated review passes
OBSERVED: Control native XML has 24/24 new assertion failures and 70/70 prior passes; zero errors/skips/attempt-history. Its artifact SHA-256 equals published -3, so the retained tests discriminate on product source rather than runner/version edits. Fixed source is restored; slot released.
OBSERVED: Dedicated test review: all five reviewer methods are unchanged, each repeated three times; callback is consumed once and fixtures are isolated in-memory filesystems. Added boundaries cover pre-existing dangling/live final destination links, differing link/referent basenames, unrelated bytes, successful move bytes and source removal, and exact canonicalization error text. All eight scenarios are assertion-red before product edits. No existing test, timeout, iteration count or assertion was changed.
OBSERVED: Dedicated code review traces every lookupPath caller and every child insertion/removal. All current path lookup, validation and mutation/result construction share openFilesLock after the clock returns. The required now parameter prevents lookup from reaching user code. Recursive symlink resolution cannot retain a link when its referent is absent. Move uses matching parent/segment from no-follow final destination/source lookups. Intermediate links still resolve; metadata and delete preserve their no-follow final semantics. Read listing copies/sorts names inside the lock. Root creation retains its current-root recheck. No new public member, class, dependency, persisted state or production operation is introduced.
OBSERVED: Brief wording is applied to primitive transactions and the tested inherited createDirectories callback case. As the review established, independently concurrent changes across inherited compound method calls have no whole-composition atomic guarantee; no new such guarantee is claimed. Known core createDirectories(existingFile) behavior is not changed in this fork.

## Exact-source gate — 09:20 UTC
OBSERVED: Candidate https://github.com/CodexCoder21Organization/okio/commit/d5151e217294a9b0aa4b6a2210fdf09c977c383d is pushed. Gate https://github.com/CodexCoder21Organization/okio/actions/runs/39020635632 checks out this exact SHA with full history on wip/gha-run/okiofakefs3, builds/checks the skinny artifact and all public declarations against checksum-verified -3, then runs eight new scenarios x3 and all prior 70 rows. Workflow/job bounds are 15 minutes, build 600 seconds and test 180 seconds. No workflow file is in the PR branch. The workflow head is its own temporary commit, not the candidate SHA; checkout explicitly verifies the candidate.
OBSERVED: Reviewer source stays byte-identical. Its two exploratory directory/symlink methods are explicitly refuted in the supplied review and are not counted defect reproducers; runner selects only its move method as the brief requests. The other adopted class selects all four required read scenarios.

## Accepted gate — 09:21 UTC
OBSERVED: https://github.com/CodexCoder21Organization/okio/actions/runs/39020635632 completed successfully. Downloaded native XML contains exactly 94/94 passing first-attempt rows: required move 3/3; four required read scenarios 4×3/3; three extra boundaries 3×3/3; prior 70/70. Zero failures/errors/skips/attempt-history. This is the accepted gate; no more test verification batches will run.
OBSERVED: Build log confirms the all-class javap comparison against checksum-verified -3 actually ran and passed; every public declaration and compiled class name is identical. This satisfies the named-public-API superset check. Exact tested JAR SHA-256 a711455100fde7bc16432e583216d10b58f66bba72e1c424363a8718d622eace; POM SHA-256 28f6648942e359711ab1530b68e8cebe71bb3f5e4516fb52fe4794173a6430e0. Publishing only these gate bytes as the explicitly authorized -4 coordinate.

## Publication and deliverables — 09:23 UTC
OBSERVED: Authorized publisher 0.0.6 exited 0 for exactly com.squareup.okio:okio-fakefilesystem-jvm:3.4.0-r576-registry-lock-4. Served JAR and POM return HTTP 200 and are byte-identical to accepted gate artifacts. JAR SHA-256 a711455100fde7bc16432e583216d10b58f66bba72e1c424363a8718d622eace; POM SHA-256 28f6648942e359711ab1530b68e8cebe71bb3f5e4516fb52fe4794173a6430e0. Independent served-POM comparison confirms only the project version changes from -3.
OBSERVED: A served-file read was started before the publisher process had finished and returned HTTP 404. After the tracked publisher exited successfully, both served files returned HTTP 200. No publication was repeated and this was not a test verdict.
OBSERVED: https://github.com/CodexCoder21Organization/okio/pull/5 is OPEN at d5151e217294a9b0aa4b6a2210fdf09c977c383d. Its description was updated through REST and read back byte-for-byte; original why-first paragraph and required campaign URL are retained, the symlink-resolution rule and both review credits are present, and all gate/checksum numbers are recorded. Orchestrator owns full-suite CI and merge decisions.

## Product file audit
 README.md                                          | 10 ++-
 .../fakefilesystem-3.4.0/src/FakeFileSystem.kt     | 84 +++++++++++++---------
 .../tests/DanglingAdmissionReviewTest.java         | 60 ++++++++++++++++
 .../tests/ReadLookupReviewTest.java                | 64 +++++++++++++++++
 .../tests/SymlinkResolutionBoundaryTest.java       | 63 ++++++++++++++++
 scripts/buildFakeFilesystemDirectoryBackport.py    | 12 ++--
 scripts/testFakeFilesystemDirectoryBackport.bash   |  3 +-
 7 files changed, 252 insertions(+), 44 deletions(-)

README.md records -4 and current locked read/mutation behavior, dangling-target errors and final move-link replacement.
backports/fakefilesystem-3.4.0/src/FakeFileSystem.kt captures clocks before reads, resolves/checks/uses entries under the lock and keeps move parent/segment consistent.
backports/fakefilesystem-3.4.0/tests/DanglingAdmissionReviewTest.java adopts the reviewer source unchanged, selecting the requested move proof.
backports/fakefilesystem-3.4.0/tests/ReadLookupReviewTest.java adopts the four reviewer public proofs unchanged.
backports/fakefilesystem-3.4.0/tests/SymlinkResolutionBoundaryTest.java covers existing dangling/live move destinations and final dangling canonicalization.
scripts/buildFakeFilesystemDirectoryBackport.py builds -4 and verifies prior -3 checksum, class names and all public declarations.
scripts/testFakeFilesystemDirectoryBackport.bash selects the five requested proofs and three new boundaries against -4.
OBSERVED: Existing-test edits: NONE. Prior 70 test rows retain original source, assertions, timeouts and iteration counts. No investigation files, workflow, build outputs or test artifacts are in the source branch.

## Brief audit
- DONE: Five reviewer reproducers adopted unchanged; move 3/3 and four read scenarios 4×3/3 assertion-red on -3 before product edits, then green at the accepted gate.
- DONE: Current symlink lookup/validation/mutation or read-result construction share one lock transaction after callbacks; no user clock remains reachable from recursive lookup.
- DONE: Dangling resolution cannot return a retained link with a mismatched referent parent; move replaces its final link entry itself and preserves unrelated populated directories.
- DONE: Three added boundary scenarios, each red 3/3 and green 3/3, cover existing links, differing basenames, content preservation and exact dangling canonicalization error.
- DONE: Product-only control red 24/24, prior 70/70 green; baseline rebuild equals published -3.
- DONE: Accepted first-attempt gate 94/94, including prior 70/70 and all 20 registry trials; no later test batch.
- DONE: Named public API superset check against -3 passes with identical declarations/classes and unchanged dependencies.
- DONE: Authorized -4 published once from exact accepted gate bytes; served checksums match.
- DONE: Existing PR branch/description updated; why-first opening, exact campaign URL, both review credits and existing-test edit list included.
- OBSERVED: As established in the review, inherited compound methods retain separate primitive transactions; no whole-composition atomicity against independent concurrent edits is promised. This does not weaken the required callback proof, which is green 3/3.
- DONE: No merge, queue, ready-state change, deployment, production-host connection, remote BuildTest submission, extra agent, handoff operation or challenge-service invocation.

## FLAKES ENCOUNTERED — final
OBSERVED: No unexpected first-attempt test failure. Deliberate red proofs and product control contain only their 24 expected assertion failures. Accepted gate has zero failures/errors/skips/earlier attempts.

## Challenges for the orchestrator — final
No ecosystem workaround was required. Only the early served-file check noted above preceded publication completion; terminal publication and served-byte verification succeeded.

## Final cleanup — 09:25 UTC
OBSERVED: Temporary wip/gha-run/okiofakefs3 branch deleted locally and remotely after terminal gate success. All tracked test/control/publication commands have terminal outcomes. ps -eo pid,args -ww (with parent IDs to exclude the current inspection command) finds zero remaining lane build/test/publication processes; no lane-owned build slot remains. Temporary build/artifact directories are removed after their evidence is pushed. Working tree is clean, and no test ran after the accepted gate.
OBSERVED: Durable evidence is https://github.com/CodexCoder21Organization/okio/tree/wip/okiofakefs3-evidence/evidence/okiofakefs3 . No -final.md file is written by this lane. Source head remains d5151e217294a9b0aa4b6a2210fdf09c977c383d.
