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

## First red milestone — 09:19 UTC
OBSERVED: Published -3 checksum confirmed as 3b04c346df6fb6ebe04c80d7d5bec07e2410b0be9f588a385537ff2b353c1e4a. Reviewer proof source files adopted byte-for-byte; runner selects only the counted move method and four read methods. Native XML: 15/15 expected assertion failures (five scenarios x3), 70/70 unchanged prior rows pass, zero errors/skips/attempt-history. Move asserts unrelated directory type/child retention; read methods assert absent metadata, canonicalization error, empty current listing, and actual requested directory creation. No product edit preceded this verdict. Local slot released; native XML and full assertion output retained separately in evidence.
