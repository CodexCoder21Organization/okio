# okiofakefs3 findings
Started 2026-10-17 09:12 UTC; 60-minute budget.

## Plan
- [in progress] Read guidance and review evidence; adopt five public proofs and record red 3/3 each on -3; push first milestone.
- [pending] Correct lookup transactions and matching move destination; review tests and code; product-only red control.
- [pending] Exact-source GitHub-machine gate: five new scenarios 3/3 and prior 70/70; all-class javap against -3.
- [pending] Publish authorized -4 gate bytes; verify served checksums; update existing PR, push evidence, clean up.

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
