# revokio2 independent review
Started 2026-10-17 09:03 UTC; 25-minute budget. Target ba235959c1bdf0ab615429ba250061900d4607d8; https://github.com/CodexCoder21Organization/okio/pull/5 .

## Plan
- [done] Read earlier findings, named guidance sections and fork README; confirm repository and initial rebase.
- [done] Trace all mutators, read paths, compound methods and callbacks; review native evidence and API checks.
- [done] Run the fork build/test script once, audit native XML, push evidence and report counts.

## Reading
OBSERVED: Read revokio1 and okiofakefs2 findings, fork README, Testing Architecture named sections (TDD, machine-independent reproducer, isolation, hardware-independent verdicts), Engineering Philosophy named sections (reproduction, refutation, visible failures). No root AGENTS.md exists. Fresh checkout is the exact requested head; initial fetch/rebase reports up to date. Repository found with github-repos skill.

## Constraints
Read-only product review; evidence only under wip/revokio2-evidence. No PR edit, merge, remote test submission, publication, production access, handoff operation, challenge-service call or extra model process. Status-report skill read; lane-specific disk reporting/no-handoff instructions take precedence.

## FLAKES ENCOUNTERED
None observed.

## Challenges for the orchestrator
None observed.

## 09:06 UTC — first verification milestone
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
REFUTED: The preliminary same-shape directory/symlink suspicion is wrong: both createDirectory and createSymlink reject the retained Symlink element. Six exploratory rows report those correct IOException outcomes as errors because the first probe expected a normal return; those are fixture expectations, not six product failures or accepted red proofs. The first exploratory source and all native rows are retained without alteration; reproduce the counted finding by selecting only its move method. Atomic move's three assertion rows already demonstrate the product defect independently.

## Read-path invariant before proof
OBSERVED: metadataOrNull, canonicalize and list call lookupPath without a captured time; symlink traversal retains current before calling clock.now at line 587. Root creation has its own recheck, but symlink traversal has no equivalent re-resolution.
INFER: A callback can retarget /alias from /old to /new, then create /old/ghost. /alias/ghost is absent before, during and after that callback, yet the suspended lookup may report the new retired-parent entry. The owning commonCreateDirectories wrapper may consequently accept that metadata and return success without creating the requested directory.
Invariant: a public lookup must not invent an entry that never existed at its requested path; successful directory creation must leave the requested directory present after all test-controlled callbacks. Public proofs: metadataDoesNotReturnAnEntryNeverAtTheRequestedPath, canonicalizeRejectsAPathAbsentThroughoutItsCallback, listDoesNotReturnAChildNeverAtTheRequestedPath, createDirectoriesCreatesTheRequestedDirectory.

## 09:08 UTC — stale read path and compound creation reproduced
OBSERVED: ReadLookupReviewTest has 12/12 native assertion failures, zero errors/skips/earlier attempts: metadataOrNull, canonicalize, list and inherited createDirectories each repeat three times. The callback first retargets /alias from /old to /new, then creates /old/ghost; it verifies /alias/ghost remains absent. Metadata returns a directory, canonicalize returns normally, list returns [/alias/ghost], and createDirectories returns normally without creating /alias/ghost. The requested child is absent throughout the callback, so these are mixed path views rather than a permitted old snapshot.
The mechanism is lookupPath retaining a Symlink before clock.now and then following that retired object after the callback changes its directory entry; the evidence that demonstrates it is 12/12 public native assertion failures, lookupPath lines 579/586–598 and the unmodified commonCreateDirectories exists-only decision. The read path belongs to this fork, so a wrapper-only correction would leave the owning defect unchanged.
OBSERVED: Both findings are inherited neighboring gaps requested by the review's all-path/compound-path scope; neither is claimed as a newly introduced line-level regression. No product correction is made in this review.

## Review finding — P1: move through a final destination link replaces an unrelated directory
Location: https://github.com/CodexCoder21Organization/okio/blob/ba235959c1bdf0ab615429ba250061900d4607d8/backports/fakefilesystem-3.4.0/src/FakeFileSystem.kt#L428 (parent at 435, write at 453; lookup transition at 596–598).

OBSERVED: Prepare `/parent/target/child` with confirmed content and `/source` with source content. The public clock callback creates `/target -> /parent/missing`. `atomicMove(/source, /target)` then returns success, removes `/source`, and installs its File at `/parent/target`, removing access to the unrelated committed child. The source/destination check and write are locked, but they disagree about which directory entry they operate on: target lookup returns a referent parent, while insertion uses the original basename. Native move proof is assertion-red 3/3, zero errors/skips/earlier attempts in those three rows. This is one finding, not a second count for its missing regression test.

Required correction: resolve the destination entry consistently with move semantics, including a final dangling symlink; use a matching parent and segment for the checked/written entry. Verify existing and callback-created final links, differing link/referent basenames, and preservation of unrelated populated directories. Merely protecting the write with the lock does not establish that it is the checked entry.

## Review finding — P2: read lookup retains a retired symlink across its clock callback
Location: https://github.com/CodexCoder21Organization/okio/blob/ba235959c1bdf0ab615429ba250061900d4607d8/backports/fakefilesystem-3.4.0/src/FakeFileSystem.kt#L579 (callback at 587, stale follow at 589); callers at 220, 264 and 278.

OBSERVED: `/alias/ghost` is absent before the operation. During the lookup callback, retarget `/alias` from `/old` to `/new`, then create only `/old/ghost`. `/alias/ghost` remains absent. The suspended read follows the old Symlink and reports the newly created old-parent entry: metadata returns a directory, canonicalization succeeds, and listing returns `/alias/ghost`. Inherited `createDirectories(/alias/ghost)` consequently returns success while that requested path remains absent. Four public methods each fail their assertions 3/3; 12/12 assertion failures, zero errors/skips/earlier attempts.

Required correction: finish user clock callbacks before the authoritative read lookup and keep symlink/root traversal and its read result consistent, without calling the clock under the registry lock. Apply this at the owning FakeFileSystem lookup/read layer so inherited directory creation receives valid metadata. This is one shared lookup finding with four public consequences.

## Remaining scope audit
OBSERVED: All explicit openFilesLock regions were traced. Every mutating lookup inside them supplies a non-null Instant through recursive symlink/root lookup. No reachable clock call occurs under that lock. Root-on-demand outside these transactions still reads the clock before locking and rechecks the root inside the lock. Registration snapshots, registry search and finally-based close removal contain no callbacks. Handle write/resize/close clock calls occur outside the registry lock and operate on their admitted File object.
OBSERVED: openReadWrite/openReadOnly and sink/appendingSink/source use the corrected open path; delete uses no-follow final lookup with the captured time; setExtra resolves after its callback and updates inside the lock. Configuration setters assign policy/working-directory values only. No additional same-family entry writer was found beyond the counted move issue. Directory and symlink creation reject the dangling final link used in the exploratory control, as recorded above.
OBSERVED: Read methods are the remaining callback-between-lookup-and-use path; they are covered by the counted P2. Root admission's locked recheck itself retains callback-created children, and prior root scenarios pass in the one script run. List also reads the clock after capturing a Directory, so fixing only the symlink branch would leave another stale read boundary; read the clock before lookup throughout the read paths.
OBSERVED: Inherited copy uses public source/sink with an admitted source handle and the corrected target open. It does not retain a directory lookup to write back later. Inherited deleteRecursively retains path names across list/metadata/delete calls and therefore can see changes between those calls. Core explicitly documents copy as non-atomic and deleteRecursively as not defending against concurrent tree changes; no whole-composition transaction is promised. Those wrapper boundaries do not justify a separate finding. Their list/metadata inputs remain subject to the counted read defect. Inherited createDirectories supplies the concrete failing composition proof for that defect.
REFUTED: Treating all delegated wrappers as automatically atomic is not supported by the public FileSystem contracts. The known plural createDirectories(existingFile) behavior remains the separately documented core issue from revokio1; it is not counted again.

## Proof, API and documentation audit
OBSERVED: Audited all 13 new native scenarios: each has exactly three first-attempt rows. Original red evidence has 18 + 18 + 3 assertion failures for their named mechanisms; product-only controls have 36 + 3. All 31 retained neighbors pass in the combined baseline/control runs. The exact-source original gate and this review's one fork script run each have 70/70 passing rows with zero errors/skips/earlier attempts. All four prior directory/registry test files are byte-identical to -2. Helpers create fresh isolated in-memory filesystems, use public callbacks/barriers, close opened resources and bound executor cleanup; no outside service or elapsed-time trigger decides these regression assertions.
OBSERVED: Original gate https://github.com/CodexCoder21Organization/okio/actions/runs/39009069601 is successful. Independently downloaded its artifact and native XML. The native XML is byte-identical to the saved evidence. The workflow checks out the exact requested full SHA with full history; the workflow run's own head is its temporary workflow commit, as expected. The test JAR, served -3 JAR and local rebuilt -3 JAR are byte-identical at SHA-256 3b04c346df6fb6ebe04c80d7d5bec07e2410b0be9f588a385537ff2b353c1e4a.
OBSERVED: Independently downloaded -1/-2/-3 and ran javap across all 11 classes: public declarations and class names agree exactly. All POM content agrees after normalizing only the project version. The build script actually ran its prior checksum/signature checks, not merely declared them.
OBSERVED: Fork README and the consumer docs addendum on https://github.com/CodexCoder21Organization/BuildTestEmbedded/blob/wip/okiofakefs1-docs/docs/r576-fakefilesystem-concurrency.md#defect-3-current-entry-admission-after-clock-callbacks accurately record version, signatures, dependencies, named original mechanisms and original gate counts. Their broad preservation/current-entry language needs qualification or the two corrections above: a final destination link still permits unrelated directory loss, and inherited creation still accepts stale metadata. This is not counted as an extra documentation finding.

## Reading references
- https://github.com/CodexCoder21Organization/DocumentationRepository/blob/main/architecture/TESTING.md — four required sections.
- https://github.com/CodexCoder21Organization/DocumentationRepository/blob/main/PHILOSOPHY.md — three required sections.
- https://github.com/CodexCoder21Organization/okio/blob/ba235959c1bdf0ab615429ba250061900d4607d8/README.md .
- https://github.com/CodexCoder21Organization/okio/blob/ba235959c1bdf0ab615429ba250061900d4607d8/okio/src/commonMain/kotlin/okio/FileSystem.kt — canonicalize, list, copy and deleteRecursively contracts.
- https://github.com/CodexCoder21Organization/okio/blob/ba235959c1bdf0ab615429ba250061900d4607d8/okio/src/commonMain/kotlin/okio/internal/FileSystem.kt — wrapper implementations.
- Supplied revokio1 and okiofakefs2 findings plus their native evidence and recorded workflow.
- Consumer documentation addendum linked above.

## Brief audit
- DONE: Exact requested head and open source PR confirmed; no PR edit, merge or product change.
- FINDING P1: Final move-destination link pairs the wrong parent/name; public assertion proof 3/3.
- FINDING P2: Symlink reads cross callbacks and yield mixed path views; four public consequences, 12/12 assertion-red.
- DONE: Every lock/clock boundary traced; no clock call under openFilesLock found.
- DONE: Copy/deleteRecursively composition reviewed against explicit public contracts; no unsupported atomicity requirement added. Concrete inherited createDirectories failure proves the read finding.
- DONE: All 39 original new proofs and product-only controls audited, including three repetitions, native assertion types and no earlier attempts; prior 31 rows and registry 20-trial workload pass.
- DONE: Fork test script run exactly once; 70/70 positive rows. Public API/POM and served/tested/rebuilt bytes independently compared.
- DONE: README/docs reviewed with the two qualifications above. Evidence on wip/revokio2-evidence only.

## FLAKES ENCOUNTERED — final
OBSERVED: No unexpected first-attempt failure in the unchanged suite. New review probes deliberately produce 3 move and 12 read-path assertion failures. Six initial exploratory fixture rows expected a normal return where correct existing-link rejection occurs; they are recorded and refuted, not rerun or counted as product failures. No source, timeout, iteration count or assertion in any existing test was edited.

## Challenges for the orchestrator — final
No ecosystem workaround was required. All new failures have public proof sources and native XML on the evidence branch; there is no production connection, remote BuildTest submission or CI wait to transfer.

## Final checkpoint and cleanup — 09:11 UTC
OBSERVED: Review evidence is durable at https://github.com/CodexCoder21Organization/okio/tree/wip/revokio2-evidence/evidence/revokio2 . Product diff against ba235959 for README.md/backports/scripts is empty. Temporary downloaded artifacts, compilation classes and prior-run audit directories were removed after their completed checks; native reports, proof sources and audit summaries remain in evidence. All tracked command sessions have terminal outcomes. ps -eo pid,ppid,args -ww confirms the initial lane command PIDs are absent, and no lane-owned build slot remains. No background command or test was started after cleanup.

REVIEW ba235959 FINDINGS: P1 1 P2 1
