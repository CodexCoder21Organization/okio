# revokio3 independent review
Started 2026-10-17; 25-minute budget. Target d5151e217294a9b0aa4b6a2210fdf09c977c383d; https://github.com/CodexCoder21Organization/okio/pull/5 .

## Plan
- [done] Read required guidance, earlier reviews, exact source and README; map lookup/clock/lock boundaries and inherited contracts.
- [done] Run fork script once; audit native 94 rows, prior controls, hermetic tests, and all-class public signatures.
- [done] Review PR motivation and three review credits; save evidence to wip/revokio3-evidence; clean up and report.

## Reading and initial state
OBSERVED: Read the required Testing Architecture and Engineering Philosophy sections, revokio2 and okiofakefs3 findings, and the fork README. No root AGENTS.md exists. github-repos skill applied and repository located. Fresh clone is at exact d5151e217294a9b0aa4b6a2210fdf09c977c383d; initial fetch/rebase left the head unchanged. Local disk has over 1 TB free.
OBSERVED: Review only; no product edit, PR edit, publication, merge, production connection, extra agent, remote BuildTest submission or handoff operation is authorized.

## FLAKES ENCOUNTERED
None so far.

## Challenges for the orchestrator
None so far.

## First verification milestone
OBSERVED: Fork build and test script ran exactly once, exited 0, and released its local build slot. Native XML has exactly 94/94 passing rows, zero failures/errors/skips/attempt-history; eight new scenarios have exactly three repetitions each. All 20 registry trials report PASS. The actual build performed the all-class public javap comparison with checksum-verified -3. Built JAR SHA-256 a711455100fde7bc16432e583216d10b58f66bba72e1c424363a8718d622eace and POM 28f6648942e359711ab1530b68e8cebe71bb3f5e4516fb52fe4794173a6430e0 match the accepted gate.
OBSERVED: Independently downloaded https://github.com/CodexCoder21Organization/okio/actions/runs/39020635632 artifacts; its native XML also has 94/94 passing first-attempt rows and no errors/skips/history. PR is OPEN at the exact requested head.
OBSERVED: Every lookupPath caller enters openFilesLock after its clock read; recursion requires/reuses now and reaches no clock/user callback. Read results are built inside the same lock. Handle resize/write/close read the clock outside that registry lock and operate on their admitted File, without path re-resolution.
OBSERVED: Inherited copy/deleteRecursively/createDirectories remain separate primitive calls. Core copy explicitly documents non-atomic behavior; deleteRecursively explicitly permits concurrent-tree changes to cause failure. The README describes primitive transactions but does not explicitly state the compound-method scope exception present in the PR body. Reviewing whether that omission needs a requested documentation correction.

## Continued review — 11:05 UTC
OBSERVED: Continued from the existing durable evidence checkpoint without rerunning the fork script. It already ran exactly once. Read and verified the required documentation sections again; github-repos lookup confirms the named fork. Status-report skill was read; the explicit disk-only, no-handoff, no-extra-agent lane rules take precedence.
OBSERVED: Three saved public boundary probes pass 3/3 native rows with zero errors/skips/history. Self and two-link loops fail with the complete expected message `symlink cycle?`; a chain of 40 links resolves, a chain of 41 is rejected. Each canonicalization reads the clock exactly once. Eight intermediate-loop public entry operations reject the loop and subsequently permit a different thread to read metadata. Eight primitive/read operations allow their clock callback to wait for a different filesystem caller before completing; none holds openFilesLock across that callback.
OBSERVED: Independent native XML audit confirms the original gate and review run each have 91 Jupiter + 3 Vintage passing rows. The new eight scenarios each have exactly three rows. Saved first-red and product-only control Jupiter reports each contain precisely 24 assertion failures, 67 prior passes, zero errors/skips/history; the prior three Vintage rows are separately retained in upstream evidence. Failure messages demonstrate the named unrelated-entry and retired-parent effects, rather than compilation or runner failures.
OBSERVED: Read the inherited copy/createDirectories/deleteRecursively implementations and public contracts. Copy is explicitly non-atomic; recursive deletion explicitly allows concurrent-tree changes to fail. All call primitive methods separately. None of the 94 tests requires a whole-composition guarantee: their callback or barrier completes before the owning primitive admission proceeds. The creation callback proof establishes current metadata and successful final creation in its controlled sequence only.
OBSERVED: README describes primitive critical sections but omits the explicit compound-method scope note that the task requires. The PR body contains that limitation only for createDirectories; the supplied implementation findings name copy and deleteRecursively too.
OBSERVED: The current OPEN PR body starts with the original observed directory loss and campaign motivation. It credits revokio1 and revokio2, with full evidence URLs. It does not yet include this third independent review; the orchestrator must add its evidence credit after receiving the result. No PR edit was made.

## Finding P2 — document the limit on compound methods in README
Location: https://github.com/CodexCoder21Organization/okio/blob/d5151e217294a9b0aa4b6a2210fdf09c977c383d/README.md#compatible-340-fakefilesystem-jvm-artifact .
OBSERVED: The README explains preservation during concurrent/re-entrant admission and says inherited createDirectories uses current metadata, but never records that copy, deleteRecursively and recursive createDirectories consist of separate primitive transactions. That specific scope limitation is a required deliverable of this review. It exists in implementation findings, and partly in the PR body, but neither is the artifact's public README contract.
INFER: A consumer reading the new concurrency guarantees can apply them to an inherited multi-call operation and expect it to preserve one consistent tree view throughout the composition. The implementation supplies no such guarantee. This is a documentation-contract gap, not evidence of another failing primitive.
Required correction: Add a short README sentence stating that copy, deleteRecursively and createDirectories recursion use separate primitive transactions and do not gain whole-operation atomicity against independent concurrent changes. Keep the current controlled callback proof and its narrower claim. No product change or extra test batch is needed for this correction.

## Administrative description check
OBSERVED: Why-first description and both earlier review credits are present. The third credit, to this completed independent review, is not yet present. The orchestrator should add https://github.com/CodexCoder21Organization/okio/tree/wip/revokio3-evidence/evidence/revokio3 when updating the description. This is recorded separately from the P2 README contract finding and does not add a second product finding.

## API and boundary conclusion
OBSERVED: Independently fetched checksum-verified -3 and ran javap -public on every one of the 11 class names in both JARs. Text and class sets are identical. No now argument appears in the public declarations; lookupPath remains private. New artifact checksum agrees with the tested/published artifact. The public before/after declarations and XML summary are in this evidence branch.
OBSERVED: Every direct lookupPath call holds openFilesLock; each outer caller reads the clock before locking and recursive lookup requires/reuses that captured Instant. The internal root lock is re-entrant on the same registry monitor and calls no user clock. Read snapshots are built inside the outer critical section. Handle data operations call clocks outside that monitor and do not resolve paths. No remaining same-family code defect was found within the requested scope.

## Brief audit
- DONE: Exact source head and open PR confirmed; no product edit, PR edit, merge, publication or production connection.
- DONE: Lookup and clock/lock boundaries traced; independent public callback and loop checks pass 3/3 native rows.
- DONE: Loop error and depth bound checked, including 40 accepted and 41 rejected, one captured time and lock release on failure.
- DONE: Compound-method limitation matches core public contracts; no existing proof requires a stronger guarantee.
- FINDING P2: Explicit compound-method scope note is absent from README. Required wording is specified above.
- DONE: Every public class/declaration identical to -3; independent javap output saved.
- DONE: Fork script ran exactly once, 94/94 first-attempt rows; original exact-source gate independently downloaded, also 94/94. Eight new scenarios x3; original red and product-only control each 24/24 assertion-red with prior 70/70 passing. No extra fork batch.
- DONE: Tests use isolated in-memory filesystems, public callbacks/barriers and closed handles; no shared service or timing trigger determines the named assertions. Registry workload retains its 20 genuine-concurrency trials.
- DONE: Why-first description verified. Two earlier credits exist; orchestrator must add this third completed review credit. No description was edited by this lane.
- DONE: Evidence only on wip/revokio3-evidence. No existing test was edited, reduced or disabled.

## Reading references
Read named sections of https://github.com/CodexCoder21Organization/DocumentationRepository/blob/main/architecture/TESTING.md and https://github.com/CodexCoder21Organization/DocumentationRepository/blob/main/PHILOSOPHY.md; repository README (no root AGENTS.md); prior revokio2 and okiofakefs3 findings; public FileSystem contracts and inherited wrapper implementations; all fork test sources, runner/build scripts and native gate/red/control evidence. github-repos and status-report skills were read. Lane-specific reporting restrictions take precedence over unrelated skill actions.

## FLAKES ENCOUNTERED — final
OBSERVED: None in the fork run or added public boundary probes. The 24 baseline/control assertion failures are the intended discriminating proofs, not unexpected failures. Zero first-attempt retry histories in accepted XML.

## Challenges for the orchestrator — final
OBSERVED: No tooling workaround was needed. The checkout contained an earlier durable checkpoint and uncommitted boundary evidence on continuation; these were verified and retained without repeating the requested once-only fork run.

## Result
REVIEW d5151e21 FINDINGS: P1 0 P2 1

## Final cleanup
OBSERVED: All started commands have terminal outcomes. A ps-based inspection and per-process working-directory check found zero remaining lane build/test processes. Temporary artifact/class/gate-download directories were removed; source files remain untouched. No lane-owned build slot remains. Final evidence is pushed only to wip/revokio3-evidence.
