# okiofakefs2 findings
Started 2026-10-17 06:07 UTC; 60-minute budget.

## Plan
- [done] Read prior findings, named guidance sections, fork README; confirm open PR and rebase once.
- [in progress] Adopt reviewer proofs, add ordered concurrent admissions, record red-first native XML and push tests.
- [pending] Correct three admission transactions; audit neighboring mutators; product-only mutation control.
- [pending] Run accepted gate: each new scenario 3/3, prior directory and registry tests once; public API identical to -2.
- [pending] Publish authorized -3 bytes, update PR body and consumer documentation branch, push evidence and cleanup.

## Reading
OBSERVED: Read Testing Architecture (Test-Driven Development, Never Calibrate a Reproducer to a Machine, Tests Must Be Hermetically Sealed, Test Verdicts Must Be Hardware-Independent); Engineering Philosophy (Reproduce a Bug Before You Fix It, A Refuted Hypothesis Is a Successful Outcome, Never Hide a Failure); fork README; supplied revokio1 and okiofakefs1 findings; reviewer public test source. No root AGENTS.md exists.
OBSERVED: https://github.com/CodexCoder21Organization/okio/pull/5 is OPEN on wip/okiofakefs1 at b942f8201b3f35605a5a8c3849026c5dc978607e. Fresh clone and initial fetch/rebase completed. No source edit yet.
The mechanism is an entry lookup/check retained across a user Clock callback in open, createSymlink and atomicMove, and the evidence that demonstrates it is the review's three public proofs (each assertion-red 3/3 on -2), now adopted unchanged, plus verification of the current lookup/callback/write ordering.

## Invariants before product edits
| Resource | Creator/owner/closer | Observable contract | Re-entry/concurrency | Public proof |
|---|---|---|---|---|
| File admission and registry | Filesystem owns entries; caller closes handle | mustCreate rejects any entry, directory children survive | callback or second caller creates entry before admission | adopted open proof and ordered concurrent variant |
| Symlink admission | Filesystem owns entry; delete removes it | existing entry rejected without replacement | callback or second caller creates populated directory | adopted symlink proof and ordered concurrent variant |
| Move source/destination | Filesystem owns both entries | target directory rejected, source preserved | source-parent traversal callback or concurrent destination creation | adopted move proof and ordered concurrent variant |
| Directory admission | Filesystem owns entries | existing directory preserved, mustCreate rejected | existing round-1 callback and concurrent tests | unchanged prior 27 rows |
| Registry lifecycle | Filesystem registers, caller close retires | no lost registration or callback under lock | inherited registry tests, snapshot and close | unchanged four registry neighbors |
No persisted state or restart contract exists in FakeFileSystem.

## FLAKES ENCOUNTERED
None observed.

## Challenges for the orchestrator
No remote test submission, handoff update, challenge-service call, production connection or additional agent will be made. Status updates stay in this findings file as required by the lane brief.

## First red milestone — 06:09 UTC
OBSERVED: Native XML reports six new scenarios x3 = 18/18 assertion failures, zero errors/skips; the unchanged prior directory and registry neighbors pass 31/31. Reviewer proofs are byte-identical. Three additional ordered two-thread variants pause the outer operation in its public Clock, commit the directory and child from a second caller, then resume; all fail the missing-error/type/child assertions (move also loses source). The console process exits 1 as expected. No internal attempt histories exist in this native runner. Local slot acquired/released; no product source edit yet.
OBSERVED: First pushed milestone contains only the new tests and runner selection. Full native failure details are retained on the evidence branch, rather than copied into the PR.

## Neighbor audit red-first — 06:11 UTC
OBSERVED: Six additional public boundaries each fail 3/3 on -2 (18/18 assertion failures, zero errors/skips): sink and appendingSink replace callback-created directories; mustCreate misses callback-created files; mustExist recreates callback-deleted files; read-only open reads the pre-callback file; delete follows a symlink parent retained before its callback changes the link. Delete's proof keeps /old/target and /new/target distinct, then expects deletion through /alias to affect the current parent only. Its old implementation instead removes /old/target and leaves /new/target. No product edit preceded these verdicts.
OBSERVED: An initial Java checked-exception declaration error produced no XML and is excluded; casting calls to the public FileSystem API corrected only this new unpushed fixture's compilation. No existing assertion or test has been changed.
INFER: delete must join the same clock-before-lock/current-lookup transaction so its emptiness/open-file checks and removal cannot be separated by callback or another entry writer. Added invariant: filesystem owns deletion; current parent is resolved after callbacks; nonempty directories and open files are rejected; proof is deleteResolvesCurrentSymlinkParentAfterClock.

## Mutation control and remaining mutator audit — 06:11 UTC
OBSERVED: Reverted only product FakeFileSystem.kt to b942f820, rebuilt with the -3 build script, and ran the retained tests. The built JAR is byte-identical to published -2 (SHA-256 21e60aadff3bf4906847103742c28b6a197349ecf58f132bd4eebf07e4c9e7a4); all-class javap check succeeded. Native XML reports the same 36/36 new assertion failures and 31/31 unchanged directory/registry passes, zero errors/skips. An exit trap restored fixed source and released the local slot.
OBSERVED: The audit also finds setExtra traverses a symlink parent while reading the clock, retaining the old link as delete does. A separate metadata test will require the update to land on the current parent and preserve the retired parent's entry. This is the same callback/current-lookup rule; no new public API is needed.
OBSERVED: metadataUpdateResolvesCurrentSymlinkParentAfterClock is assertion-red 3/3 against -2 (zero errors/skips), confirming this additional mutator rather than assuming it. Invariant: filesystem owns entry extras; metadata writes resolve the current parent after callbacks and never update a retired parent's entry. This new scenario is checkpointed before changing setExtra.

## Dedicated reviews before the accepted gate — 06:12 UTC
OBSERVED: Test review confirms adopted reviewer source is byte-identical, including missing-error, changed-type and missing-child assertions, plus source preservation for move. Three concurrent variants use two real callers, one-shot public clock barriers, fresh in-memory filesystems, closed handles/streams, and bounded executor cleanup. Six additional boundaries and metadata use one-shot callback state, not elapsed-time triggers. Existing directory/registry tests are unchanged; no timeout, iteration count or assertion is reduced.
OBSERVED: Source review traces all openFilesLock regions. Open, directory, move, delete, symlink and metadata update read the clock before locking, resolve current paths inside the lock, supply that time through recursive symlink/root lookup, then check and mutate without a clock call. Root fallback retains its locked recheck for callers outside these transactions. Registry snapshot/removal remain unchanged. No public interface, dependency or persisted shape changed.

## Public mutator audit
- OBSERVED: openReadWrite: stale pre-callback lookup corrected; current existence/type checks, insertion and registration share the lock.
- OBSERVED: sink/appendingSink/source/openReadOnly: use the corrected open path; named public tests cover directory rejection and current read-only content.
- OBSERVED: atomicMove: both lookups and all checks/removal/insertion share the lock after the clock callback.
- OBSERVED: createSymlink: current absence check and insertion share the lock after the clock callback.
- OBSERVED: delete: corrected current parent resolution, emptiness/open-file checks and removal share the lock; new public proof fails on -2.
- OBSERVED: createDirectory(mustCreate): round-1 implementation already follows this rule; retained unchanged and covered by all prior directory tests.
- OBSERVED: setExtra: corrected current parent lookup and extras update share the lock; new public proof fails on -2.
- OBSERVED: workingDirectory and behavior-policy setters assign configuration only; no lookup/check/insertion sequence.
- OBSERVED: handle resize/write/close operate on the admitted File object; they do not admit or replace path entries. Existing registry close tests remain unchanged.
- OBSERVED: inherited copy/createDirectories/deleteRecursively delegate to primitive operations. The known plural createDirectories(existingFile) behavior belongs to core and is unchanged, as the review established. Read operations list/canonicalize/metadata do not admit child entries; on-demand root creation already has the round-1 locked recheck.

## Exact-source gate submitted — 06:12 UTC
OBSERVED: Candidate is https://github.com/CodexCoder21Organization/okio/commit/ba235959c1bdf0ab615429ba250061900d4607d8 . The throwaway workflow checks out that exact SHA with full history, builds/checks the skinny JAR, compares every public declaration/class name with checksum-verified -2, then runs 39 new rows (13 scenarios x3) and all 31 prior directory/registry rows. Gate: https://github.com/CodexCoder21Organization/okio/actions/runs/39009069601 . Workflow/job/build/test bounds are 15 minutes/600 seconds/180 seconds. No workflow file is in the PR branch.
OBSERVED: Metadata-only product control uses the rebuilt, byte-identical -2 source JAR from the mutation run: native XML reports 3/3 assertion failures, zero errors/skips. Combined product-only controls cover all 39 new rows. No accepted positive gate has run yet.
OBSERVED: A fresh consumer checkout under this lane's .git/consumer is solely for the authorized docs branch. Its initial fetch/rebase and README reading are in progress; no consumer test or dependency edit is planned.
