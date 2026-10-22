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
