# revokio1 review
Started 2026-10-17 05:57 UTC. Budget 25 minutes. Target b942f8201b3f35605a5a8c3849026c5dc978607e, https://github.com/CodexCoder21Organization/okio/pull/5 .

## Plan
- [done] Read guidance and prior evidence; confirm exact target.
- [done] Compare backport with parent-3.4.0 and prior registry implementation; trace callback and mutation paths.
- [done] Audit native red/green results, test isolation, signature/checksum build evidence.
- [done] Push review evidence, report counted findings, confirm background commands ended.

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

## 06:04 UTC — complete provenance, semantics and test review
OBSERVED: Re-applied the recorded registry product patch to upstream parent-3.4.0 (a161b07fb1b459371458ae6d9508ec31df280428). The result is byte-identical to the saved -1 source: both SHA-256 df3c97290809cf967bd50257af3461aded79859273e5ac21a339995da58880e9. The -1-to-head diff changes only createDirectory, the root admission/recheck, optional clock-value propagation in lookupPath and its recursive call, and the lock comment. FileMetadataCommon.kt is unchanged upstream source. No unrelated product-source change found. The historical registry test matches the saved original patch byte-for-byte.
OBSERVED: Downloaded the served -1 and -2 JARs independently. -1 checksum is 1f6dbb73ed4c0f32adcb914e77eaf40b9f4217990742554ebb1603681097e72c, matching the build script's mandatory check. Served -2 checksum is 21e60aadff3bf4906847103742c28b6a197349ecf58f132bd4eebf07e4c9e7a4 and its bytes equal the exact-head gate JAR. Independently compared all 11 compiled class names and javap public declarations: identical. The POM differs exactly by project version; dependencies remain unchanged.
OBSERVED: The actual GitHub gate log contains the exact candidate checkout, the build-script command, the success message after its mandatory checksum/signature checks, and the tested JAR/POM hashes. The checks actually ran; this is not just a source inspection of an unused script. Gate: https://github.com/CodexCoder21Organization/okio/actions/runs/39004533954 . Downloaded native XML has 27/27 directory repetitions and 4/4 registry neighbors passing, zero errors/skips/earlier attempts. Console lists precisely trials 1 through 20, all PASS, for the registry workload.
OBSERVED: Baseline native XML has 27 assertion failures of type org.opentest4j.AssertionFailedError, zero errors/skips/earlier attempts. Its classpath names the -1 artifact and exact core 3.4.0.2. Product-only mutation native XML has those same 27 assertion failures and four passing registry neighbors. No compile failure is counted. Tests create fresh in-memory filesystems, use only public methods and supplied Clock callbacks, close their handles, and force the directory ordering with explicit callbacks or barriers rather than scheduler luck. The simultaneous mustCreate scenario has three repetitions; callback/root/symlink/existing-file cases each have three. The registry workload is unchanged.
OBSERVED: createDirectory preserves an existing directory, rejects existing directories when mustCreate is true, and rejects a file at the requested path; the exact-head recorded tests cover these behaviors. The inherited core createDirectories wrapper leaves an existing directory unchanged and rejects an existing path with mustCreate=true.

## Scope-item (2) qualification — inherited wrapper behavior
OBSERVED: A separate public proof, DirectoryWrapperReviewTest.createDirectoriesRejectsExistingFile, is assertion-red 3/3 against the exact-head tested JAR and core 3.4.0.2. Its complete assertion is `Directory creation wrapper must report a file at the requested directory path. ==> Expected java.io.IOException to be thrown, but nothing was thrown.` Native XML has zero errors/skips/earlier attempts. No files or handles remain open.
OBSERVED: The owning code is core FileSystem.commonCreateDirectories, which stops its scan on any existing entry and never checks that entry's directory type. The same behavior is present in upstream parent-3.4.0 and the fork's current core source; exact-core javap bytecode confirms the exists-only scan. FileSystem.createDirectories is inherited and final, so this PR's FakeFileSystem override is not reached for an already-present file.
REFUTED: If scope item (2) requires the plural createDirectories(file, false) wrapper to throw, that expectation does not describe upstream 3.4.0 behavior. The singular createDirectory(file, false) behavior is corrected as requested. This dependency limitation is recorded separately and is not counted as a regression introduced by this PR. No downstream workaround is proposed.

## Review findings — P1 3, P2 0
All three findings below are inherited neighboring gaps explicitly requested in scope item (1), rather than newly changed bodies. They are counted separately because each public mutator has its own stale check/write sequence and its own deterministic public proof. They silently replace a directory containing a child and return success. No additional missing-test finding is counted for the same behavior.

### P1 — Revalidate mustCreate open admission after the clock callback
Location: https://github.com/CodexCoder21Organization/okio/blob/b942f8201b3f35605a5a8c3849026c5dc978607e/backports/fakefilesystem-3.4.0/src/FakeFileSystem.kt#L325 (check at 335, replacement at 367).
OBSERVED: openReadWrite captures lookupResult before clock.now, then tests mustCreate and directory type using that old result. A callback creates /target and closes /target/child before returning. The outer openReadWrite(/target, true, false) succeeds and replaces /target with a File under the lock. The lock protects the assignment but not the validity of its earlier lookup. New proof mustCreateOpenPreservesDirectoryCreatedByClock fails 3/3; the expected IOException is absent and the directory becomes a file.
Required correction: read any user callback before the entry-admission transaction, then obtain/check the current entry and register the handle atomically with directory creation. Preserve the rule that no user callback runs under the lock. Add public mustCreate/existing-entry coverage for file, directory and callback/concurrent creation.

### P1 — Recheck symlink destination after its clock callback
Location: https://github.com/CodexCoder21Organization/okio/blob/b942f8201b3f35605a5a8c3849026c5dc978607e/backports/fakefilesystem-3.4.0/src/FakeFileSystem.kt#L492 (replacement at 502).
OBSERVED: createSymlink checks source absence, then evaluates clock.now while preparing the new Symlink. A callback creates /target and closes /target/child. The outer call replaces that nonempty directory with a Symlink and returns normally. New proof symlinkCreationPreservesDirectoryCreatedByClock fails 3/3, including explicit missing-exception, changed-type and missing-child assertions.
Required correction: obtain time before admission; perform current-entry lookup, existing-entry rejection and symlink insertion under the same directory-admission lock. Cover an entry created by the clock and genuine concurrent admission.

### P1 — Revalidate move destination after source traversal
Location: https://github.com/CodexCoder21Organization/okio/blob/b942f8201b3f35605a5a8c3849026c5dc978607e/backports/fakefilesystem-3.4.0/src/FakeFileSystem.kt#L427 (replacement at 452).
OBSERVED: atomicMove looks up /target before looking up /alias/source. The source's symlink parent triggers clock.now; its callback creates /target and closes /target/child. The old targetLookupResult still says absent, so the directory rejection at 431 is bypassed. The move removes /real/source and replaces the callback's nonempty target directory with that file. New proof movePreservesDirectoryCreatedBySourceLookupClock fails 3/3; the expected IOException is absent, target type changes, child access fails and the source disappears.
Required correction: keep user callbacks outside the transaction and revalidate both current source and destination inside one directory-admission transaction before removing/inserting entries. Cover source traversal callbacks and concurrent target creation.

## Challenges for the orchestrator — local tooling
OBSERVED: The ordinary patch executable is absent. Used git apply into a private .git/source-provenance reconstruction instead; exact byte comparison succeeded. No product file was changed by this workaround. No challenge-service write was performed.

## FLAKES ENCOUNTERED — final
No unexpected first-attempt failure. The 9 neighboring-entry failures and 3 wrapper failures are deliberate public contract probes with deterministic ordering and native assertion verdicts. Existing positive rows were audited from their original run, not rerun to obtain a later pass.

## Reading references
- Testing Architecture: https://github.com/CodexCoder21Organization/DocumentationRepository/blob/main/architecture/TESTING.md — the four named sections.
- Engineering Philosophy: https://github.com/CodexCoder21Organization/DocumentationRepository/blob/main/PHILOSOPHY.md — the three named sections.
- Fork README: https://github.com/CodexCoder21Organization/okio/blob/b942f8201b3f35605a5a8c3849026c5dc978607e/README.md .
- Prior registry note and patch: https://github.com/CodexCoder21Organization/BuildTestEmbedded/blob/main/docs/r576-fakefilesystem-concurrency.md and https://github.com/CodexCoder21Organization/BuildTestEmbedded/blob/main/docs/r576-okio-fakefilesystem-registry-lock.patch .
- Supplied okiofakefs1 and teardownfix1 lane findings, plus the retained consumer reproduction brief from https://github.com/CodexCoder21Organization/okio/tree/wip/okiofakefs1-evidence/evidence/okiofakefs1 .

## Brief audit
- DONE: Exact requested head confirmed; no PR edit or merge.
- DONE: Clock/callback paths under openFilesLock traced; no remaining callback under that lock found.
- FINDINGS: Other entry writers can still replace populated directories; three public methods each assertion-red 3/3, counted as P1 3.
- DONE: Singular directory semantics and plural existing-directory semantics checked. REFUTED generalized plural file-rejection premise against owning core code and public native proof; upstream shares that behavior.
- DONE: Source is parent-3.4.0 plus the saved registry patch plus the stated directory/root correction; no unrelated change found.
- DONE: Recorded tests are public, isolated and deterministic; nine directory scenarios repeat three times, with baseline and product-only controls assertion-red for their named reasons. Registry trial count is 20/20.
- DONE: Actual original build log and independent signature/checksum comparison verified; served and tested candidate bytes agree.
- DONE: All source, tests and native review evidence are on the separate wip/revokio1-evidence branch. No product changes, existing-test edits, full-suite run, remote submission, production access, extra agent, handoff action or challenge-service call.
