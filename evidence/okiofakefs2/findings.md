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
