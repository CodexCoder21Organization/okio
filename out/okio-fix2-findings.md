# okio-fix2 review corrections

Start: 2026-10-22 10:03 UTC. PR: https://github.com/CodexCoder21Organization/okio/pull/8
Initial head: 644560be9126049d43593dd478525df63bc0ce16

Plan:
- [complete] Read CONTRACT.md fully, root/module README, review findings, and TESTING.md sections Test-Driven Development and Tests Must Be Hermetically Sealed.
- [in progress] Add public API regressions for original-owner shrink/regrow and shrink/append, sequential-read segment work, and ordered concurrent read/resize. Run baseline on GitHub runner.
- [pending] Correct ownership, cursor reuse, and per-file locking in native and compatible implementations. Run passing tests, revert implementation and prove red again, restore.
- [pending] Review coverage/code, update README and PR body, fetch/rebase, targeted runner gate, one PR push. Remove throwaway runner branch and stop.

OBSERVED: Checkout is the supplied fresh clone, clean on usage-tracking at the requested head. PR is OPEN. No extra clone, local compilation, publication, merge or deployment is needed.
OBSERVED: FileHandle explicitly permits concurrent use. Buffer.copy leaves original segment owner=true; resizeBuffer truncates its limit without copying. A fresh read cursor walks the linked segments from an end. Read computes size before inspecting the same mutable buffer without a content lock.
INFER: These mechanisms account for all three findings. Retaining a cursor across reads and resetting it before every mutation bounds sequential traversal; copying the retained tail before shrink protects other views; one file-content lock covers size plus data access.

Invariant table (before implementation):
| Resource | Creator/owner/close | Caller observations | Re-entry/concurrency/restart |
|---|---|---|---|
| File bytes | File owns its Buffer; replacing a writable view creates another File | Prior/replacement handles stay independent, including original shrink then growth/append | Copy and mutation use original file lock; no persistence |
| Read cursor | File creates lazily; File closes/resets it before every mutation | Sequential reads traverse each segment a bounded number of times | Shared file lock serializes cursor access; replacement has its own cursor |
| File size and bytes | File owns both under one content lock | A read returns bytes or EOF from one consistent state | Read/resize/write/size share the file lock; callbacks run outside it |
| Handle lifecycle | FileSystem creates; caller closes; FileHandle retains streams | Closed handle fails; open streams keep their existing contract | Close cannot race buffer mutation outside content lock; callbacks cannot publish stale cursor state |

Test design: test-only Java instrumentation counts Segment.next/prev reads in UnsafeCursor.seek and supplies a scheduling pause before read seek. Tests still open/read/write/resize through public FileHandle only; production exposes no test member. No wall-clock performance verdict, reflection, or external service in tests. Existing tests will not be edited.

2026-10-22 10:13 UTC — baseline checkpoint
OBSERVED: First execution https://github.com/CodexCoder21Organization/okio/actions/runs/39761248950 built the unchanged compatible implementation and executed four tests: 0 passed / 4 assertion failures / 0 skipped. Both original-owner replacement tests changed the replacement array; 256 sequential reads visited exactly 16,384 segment links against a 1,024 bound; ordered read/resize reported NullPointerException. No compile failure. Exported API/checksum checks passed. Test reports are uploaded on that run.
OBSERVED: Local disk now has 35 GiB free. All compilation is still restricted to the GitHub runner. Test source checkpoint: aefa52df (pushed to wip/usage-okio-fix2-ckpt); disposable runner head 48e48ada.
INFER: All three source mechanisms are now reproduced with deterministic assertions. Native will use the same portable atomicfu lock primitive already used by the compatible registry (new native dependency); no production test hooks are needed.

2026-10-22 10:16 UTC
OBSERVED: Implemented file-content locks, cursor reuse/reset, and pre-shrink tail copy in both source versions. The portable native source adds implementation dependency kotlinx.atomicfu:atomicfu:0.29.0 for real per-file synchronization across platforms; compatible sources reuse their existing atomicfu primitive and keep dependencies unchanged. Common ownership tests also cover segment boundaries and cursor refresh after overwrite/resize/another handle close. Existing test files remain unchanged.
INFER: Clock callbacks remain outside the content lock; compatible registry acquisition therefore cannot form a content-lock-to-registry-lock cycle. A writable-open copy holds only the original file's lock; each replacement owns a separate lock and cursor.

Coverage review in progress: native common tests cover both owner directions and partial/full segment boundaries; deterministic JVM work/ordering tests currently run with the standalone review script. They will also be connected to standard jvmTest so future CI runs retain the regression checks. No production visibility changes or reflective calls are used. Test instrumentation observes JVM instructions and scheduling while all file operations go through FileHandle.

2026-10-22 10:19 UTC
OBSERVED: First fixed-run attempt https://github.com/CodexCoder21Organization/okio/actions/runs/39761694673 executed ZERO tests. Disposable workflow selected repository Java 26; Kotlin 1.8 compatible compiler failed with `java.lang.IllegalArgumentException: 26.0.2` at JavaVersion.parse. Full stack is in that runner log. This was a workflow setup mistake; compatible compilation is restored to Java 17, with repository Java selected only for Gradle.
OBSERVED: Review strengthened the ordered concurrency check to require the resizer's lock owner to be the paused reader (public ThreadMXBean state), rather than accepting any blocked/waiting state. The real file calls remain public API calls. Full captured read/resize failures are propagated as assertion causes. Standard jvmTest now compiles the standalone Java tests and runs their test-only Java agent; no production test hook was added.
Current source commit: 0aec8e5d; pending test integration changes are local and will be checkpointed with results.

2026-10-22 10:20 UTC — provisional implementation checkpoint
OBSERVED: Compatible Kotlin compilation now succeeds on Java 17, but the builder's public-javap comparison rejected the candidate before tests (https://github.com/CodexCoder21Organization/okio/actions/runs/39762062960). The original error did not show the difference. Added unified signature diagnostics; next runner will establish the exact difference. ZERO tests executed in this attempt. No API check has been disabled or broadened.
OBSERVED: Current source commit e9b0067d; no passing implementation result yet. WIP checkpoint is being pushed so the source and test integration are durable. Earlier progress timestamps are approximate narration times; GitHub runner timestamps are the authoritative execution records.

2026-10-22 10:21 UTC
OBSERVED: Diagnostic run https://github.com/CodexCoder21Organization/okio/actions/runs/39762296713 shows the sole API difference is declaration order of existing synthetic accessors `access$getOpenFilesLock$p` and `access$checkOffsetAndCount`. No signature was added, removed or changed. The builder now sorts member declarations within EACH class before comparing, retaining class identity and every signature. This corrects an order-sensitive API comparison; private-storage exclusion is unchanged. That attempt executed zero tests.

2026-10-22 10:24 UTC — first green step
OBSERVED: https://github.com/CodexCoder21Organization/okio/actions/runs/39762452972 ran the fixed review tests for the first time: 4 passed / 0 failed / 0 skipped. Exact segment work: 255 visits for 256 chunks; 1,023 for 1,024 chunks. Compatible checksum, complete class-name list and per-class public signatures all passed. The revert step then stopped before tests because checkout's default depth omitted aefa52df. Runner checkout will fetch history; this is another disposable-workflow setup mistake, not a test failure.
OBSERVED: Coverage review added the same ordered read/resize scenario through a distinct read-only handle of the original file (in addition to one shared read/write handle), and a common test for clock callback re-entry after mutation. README now describes independent replacement views, concurrent byte-array read behavior, linear sequential reads, and commands/examples.
