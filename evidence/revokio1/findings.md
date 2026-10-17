# revokio1 review
Started 2026-10-17 05:57 UTC. Budget 25 minutes. Target b942f8201b3f35605a5a8c3849026c5dc978607e, https://github.com/CodexCoder21Organization/okio/pull/5 .

## Plan
- [in progress] Read guidance and prior evidence; confirm exact target.
- [pending] Compare backport with parent-3.4.0 and prior registry implementation; trace callback and mutation paths.
- [pending] Audit native red/green results, test isolation, signature/checksum build evidence.
- [pending] Push review evidence, report counted findings, confirm background commands ended.

## Reading
OBSERVED: Read prior okiofakefs1 findings and teardownfix1 consumer findings around the directory-loss proof. Retrieved Testing Architecture and Engineering Philosophy; reading their named sections before review. GitHub repository search locates CodexCoder21Organization/okio.

## Constraints
Read-only product review. No product edits, PR edits, test resubmissions, production access, publication, merge, handoff, challenge-service call, or additional model process. Evidence only on wip/revokio1-evidence.

## FLAKES ENCOUNTERED
None observed in this lane.

## Challenges for the orchestrator
None observed in this lane.

## 05:59 UTC — source and native evidence audit
OBSERVED: The PR head is exactly the requested SHA. No repository AGENTS.md is present; README read. Named Testing Architecture and Engineering Philosophy sections read. Source comparison with the recorded -1 source is limited to directory admission, root admission/recheck, clock-value propagation through symlink lookup, and the lock comment. FileMetadataCommon.kt is byte-identical to parent-3.4.0.
OBSERVED: Every openFilesLock region was traced. No reachable user clock call remains under that lock: createDirectory supplies a non-null Instant into lookupPath, including recursive symlink traversal; open calls the clock before its lock; root lookup reads the clock before its local lock; close reads the clock before registry removal in finally. Snapshot and findOpenFile lock regions contain no user callbacks.
INFER: Three neighboring mutators still have stale path lookups across a callback: openReadWrite checks/inserts using a lookup captured before its clock; createSymlink checks absence before its clock; atomicMove looks up its destination before source traversal can call the clock through a symlink parent. These can replace a directory created by the callback even though createDirectory itself now preserves it. Public proof is the next step; no product edit will be made.

## Review invariants for public proof
| Entry operation | Owner and required terminal state | Re-entry condition | Public proof |
|---|---|---|---|
| openReadWrite mustCreate | filesystem owns entry; existing entry rejected and children preserved | clock creates directory and child after outer lookup | outer mustCreate must reject; child must remain |
| createSymlink | filesystem owns entry; existing entry rejected and children preserved | clock creates directory and child after outer absence check | symlink call must reject; child must remain |
| atomicMove | filesystem owns source and destination; existing directory rejected | source symlink-parent traversal clock creates target directory and child | move must reject; source and target child must remain |

## 06:00 UTC — public neighboring-entry proof
OBSERVED: New evidence-only NeighborEntryReviewTest ran against the JAR downloaded from the exact-head accepted gate (SHA-256 21e60aadff3bf4906847103742c28b6a197349ecf58f132bd4eebf07e4c9e7a4), exact consumer core 3.4.0.2, Java 21 and a 256 MiB test JVM. Native XML contains 9 rows, 9 assertion failures, zero errors/skips, no earlier attempts: three methods repeated three times each. The process returned 1 as expected. An earlier Java checked-exception compilation adjustment produced no XML and is not counted as a verdict. Local slot was acquired/released; no existing test was run again or edited.
OBSERVED: Each callback first creates /target as a directory, writes/closes /target/child, and verifies that child exists. Each outer mutator returns normally, replaces /target with a File or Symlink, and removes access to the committed child. The move also removes its source. This is deterministic public re-entry, with no sleeps, private access, outside service, timing-based trigger, or additional threads.
The mechanism is stale entry lookup/check across a user Clock callback in openReadWrite, createSymlink and atomicMove, and the evidence that demonstrates it is 9/9 native assertion failures in evidence/revokio1/neighbor-native plus the source check/write sequence at lines 325–367, 492–502 and 427–452.
OBSERVED: These are neighboring admission defects already present in the prior implementation, explicitly requested by review scope item (1); they are not claimed as newly introduced changes to those method bodies. The directory correction itself passes its recorded gate. No fix is made during this independent review.
