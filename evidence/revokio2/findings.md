# revokio2 independent review
Started 2026-10-17 09:03 UTC; 25-minute budget. Target ba235959c1bdf0ab615429ba250061900d4607d8; https://github.com/CodexCoder21Organization/okio/pull/5 .

## Plan
- [done] Read earlier findings, named guidance sections and fork README; confirm repository and initial rebase.
- [in progress] Trace all mutators, read paths, compound methods and callbacks; review native evidence and API checks.
- [pending] Run the fork build/test script once, audit native XML, push evidence and report counts.

## Reading
OBSERVED: Read revokio1 and okiofakefs2 findings, fork README, Testing Architecture named sections (TDD, machine-independent reproducer, isolation, hardware-independent verdicts), Engineering Philosophy named sections (reproduction, refutation, visible failures). No root AGENTS.md exists. Fresh checkout is the exact requested head; initial fetch/rebase reports up to date. Repository found with github-repos skill.

## Constraints
Read-only product review; evidence only under wip/revokio2-evidence. No PR edit, merge, remote test submission, publication, production access, handoff operation, challenge-service call or extra model process. Status-report skill read; lane-specific disk reporting/no-handoff instructions take precedence.

## FLAKES ENCOUNTERED
None observed.

## Challenges for the orchestrator
None observed.

## 09:08 UTC — first verification milestone
OBSERVED: Fork build and test script completed once. Native XML has 70/70 passing first-attempt rows, zero failures/errors/skips/earlier attempts: 39 new rows, 27 prior directory rows and four registry rows. Console lists registry trials 1 through 20, each PASS. Build performed all-class public javap comparison against -2 and reproduced the recorded -3 JAR checksum. Local slot was released.
OBSERVED: Audited original red and mutation XML: red files contain 18 + 18 + 3 assertion failures; product-only controls contain 36 + 3 assertion failures; prior 31 neighbors pass in their combined controls. No compilation error, skipped row or earlier attempt is counted. Accepted original gate has 70/70 green rows.
INFER: Terminal dangling symlinks may leave a resolved parent paired with the original basename in createDirectory/createSymlink/atomicMove. A callback can create such a link before admission; the ensuing insertion may replace an unrelated populated directory in the resolved parent. Designing a public proof before counting this as a finding.

## Review invariants for the dangling-link proof
| Entry operation | Ownership and required result | Re-entry condition | Public proof |
|---|---|---|---|
| createDirectory | Filesystem owns entries; unrelated /parent/target/child survives | clock creates /target -> /parent/missing | directoryAdmissionPreservesUnrelatedDirectory |
| createSymlink | Filesystem owns entries; unrelated directory survives | same one-shot callback | symlinkAdmissionPreservesUnrelatedDirectory |
| atomicMove | Filesystem owns source/destination; unrelated directory survives | same one-shot callback | moveAdmissionPreservesUnrelatedDirectory |
No product edits are authorized or made; these tests live only on the evidence branch.

## 09:07 UTC — new public finding reproduced
OBSERVED: moveAdmissionPreservesUnrelatedDirectory is assertion-red 3/3 on the exact-head rebuilt -3 artifact. Each one-shot clock callback creates /target -> /parent/missing while /parent/target/child already contains confirmed bytes. atomicMove(/source, /target) returns normally, removes /source, replaces unrelated /parent/target with the source File and makes its child inaccessible. No thread scheduling, external service, private access or timing trigger is involved.
The mechanism is atomicMove pairing the dereferenced destination parent with canonicalTarget.nameBytes instead of the destination entry's own parent/name, and the evidence that demonstrates it is three native assertion failures plus lookupPath lines 596–598 and atomicMove lines 435/453. For a dangling final link lookupPath carries its referent parent but retains the Symlink element, so the directory check cannot detect /parent/target, which is not the requested destination.
REFUTED: The preliminary same-shape directory/symlink suspicion is wrong: both createDirectory and createSymlink reject the retained Symlink element. Six exploratory rows report those correct IOException outcomes as errors because the first probe expected a normal return; those are fixture expectations, not six product failures or accepted red proofs. Their child-preservation assertions will be retained while treating rejection as the correct result. Atomic move's three assertion rows already demonstrate the product defect independently.

## Read-path invariant before proof
OBSERVED: metadataOrNull, canonicalize and list call lookupPath without a captured time; symlink traversal retains current before calling clock.now at line 587. Root creation has its own recheck, but symlink traversal has no equivalent re-resolution.
INFER: A callback can retarget /alias from /old to /new, then create /old/ghost. /alias/ghost is absent before, during and after that callback, yet the suspended lookup may report the new retired-parent entry. The owning commonCreateDirectories wrapper may consequently accept that metadata and return success without creating the requested directory.
Invariant: a public lookup must not invent an entry that never existed at its requested path; successful directory creation must leave the requested directory present after all test-controlled callbacks. Public proofs: metadataDoesNotReturnAnEntryNeverAtTheRequestedPath, canonicalizeRejectsAPathAbsentThroughoutItsCallback, listDoesNotReturnAChildNeverAtTheRequestedPath, createDirectoriesCreatesTheRequestedDirectory.

## 09:11 UTC — stale read path and compound creation reproduced
OBSERVED: ReadLookupReviewTest has 12/12 native assertion failures, zero errors/skips/earlier attempts: metadataOrNull, canonicalize, list and inherited createDirectories each repeat three times. The callback first retargets /alias from /old to /new, then creates /old/ghost; it verifies /alias/ghost remains absent. Metadata returns a directory, canonicalize returns normally, list returns [/alias/ghost], and createDirectories returns normally without creating /alias/ghost. The requested child is absent throughout the callback, so these are mixed path views rather than a permitted old snapshot.
The mechanism is lookupPath retaining a Symlink before clock.now and then following that retired object after the callback changes its directory entry; the evidence that demonstrates it is 12/12 public native assertion failures, lookupPath lines 579/586–598 and the unmodified commonCreateDirectories exists-only decision. The read path belongs to this fork, so a wrapper-only correction would leave the owning defect unchanged.
OBSERVED: Both findings are inherited neighboring gaps requested by the review's all-path/compound-path scope; neither is claimed as a newly introduced line-level regression. No product correction is made in this review.
