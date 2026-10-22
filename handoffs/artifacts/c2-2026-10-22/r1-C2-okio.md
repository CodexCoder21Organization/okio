# R1-C2 Okio concurrent directory creation

Plan:
1. [in progress] Read repository guidance, source, native test conventions, and existing 3.4.0 backport tooling; preserve the assigned no-test-until-slot-release rule.
2. Design one deterministic public-API regression test that forces both callers to observe the same missing directory before either creates it; include existing-directory and file-target contract checks.
3. Commit and push the test alone, then coordinate a test-slot release and prove the old implementation fails on the first attempt.
4. Fix native source and the compatible 3.4.0 backport only after the failing result; build the backport and run native/backport regression tests as scoped.
5. Rebase, perform required verification and review, open a non-draft PR only after parent approval for the upstream workflow; no publish/deploy/merge.

Invariant table:

| Resource/state | Owner and allowed operation | Expected outcome |
| --- | --- | --- |
| Jimfs backing filesystem | Test owns and closes it; callers share only the public Okio adapter | Isolated in-memory state; no external service or filesystem |
| `/shared` absent, two concurrent `createDirectory(..., mustCreate=false)` calls | Both callers request ordinary idempotent directory creation | Both return successfully; exactly one underlying create may win |
| `/shared` already a directory, `mustCreate=false` | Any caller may request ordinary creation | Call returns successfully without changing the directory |
| `/shared` already a directory, `mustCreate=true` | Caller explicitly requests a new directory | Public API rejects it with the existing must-create error |
| `/shared` is a regular file, either flag | Caller requests a directory at a file path | Public API rejects it; the file remains a file |

OBSERVED: Checkout is clean at fork `main` (`origin/main`), with upstream remote configured. Root `AGENTS.md` is absent. README documents local compatible `okio-jvm:3.4.0.2` backport build and existing backport-script workflow. Read the repository README in full, `/tmp/c2-TESTING.md` hardware-independent verdict, extreme-condition, public-API, machine-independent reproducer and per-test isolation guidance, and `/tmp/c2-PHILOSOPHY.md` “A Refuted Hypothesis Is a Successful Outcome.”

OBSERVED from parent-provided consumer evidence: SFS's eight concurrent callers fail while making a shared staging directory through Okio 3.4.0's `asOkioFileSystem()` adapter. Parent traced the mechanism to the NIO adapter's cached pre-create existence check; the direct native test must force that ordering deterministically.

INFER: A wrapper `java.nio.file.FileSystem` can gate only the second `getPath("/shared")` call per thread: first resolution is `metadataOrNull`, second is `Files.createDirectory`. Releasing both second resolutions after both callers reach them guarantees both pre-create metadata observations completed, without sleeps or hardware-calibrated thread counts.
