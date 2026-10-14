# okiofix1 findings

## Plan and scope
- [in progress] Verify the published 3.4.0 artifact provenance and source corrections.
- [pending] Add public use tests, show baseline failure, checkpoint the tests.
- [pending] Build the compatible nullable correction, pass the new and neighbour gates 3/3, and run the existing JVM suite once.
- [pending] Publish exactly one absent coordinate, verify downloaded POM/JAR and bytecode, then open a non-draft PR. No merges or deployments.

## 2026-10-14 20:13 UTC
OBSERVED: Read common.md with every addendum, repository README, Testing Architecture (required sections), Engineering Philosophy, reviewer F3, and the Maven publisher README. The publisher README URL in the brief returns 404; the actual repository is https://github.com/CodexCoder21Organization/community.kotlin.maven.artifact.publishing.cli . The fork README has no build/publish procedure; the short-read branch has a focused compiler/validation procedure in tests/fileHandlePreservesAppendBetweenShortReads/README.md.
OBSERVED: Default main is c06d4735. Existing short-read branch head is 10ded1cc52b0a171ebfcaa7b4d313c19dcb1dc6b; it contains c116621307f0b1494fc0802aec155953d9166f76 (partial-read destination fix) and fe6ac99a29ec207c9c5f4421f3fed62fd5bc9d45 (nullable-preserving use). Both ancestry checks succeed. fe6ac99a is a 2024 upstream correction; it predates the fork short-read correction. Publishing branch head wholesale would also introduce unrelated APIs.
OBSERVED: Downloaded short-read-1 SHA-256 matches the reviewer exactly: b0f2a8296acb1818a03f370ecea763e273224a04d6c0046c190ebb0a82a77061. Its file entries equal Central 3.4.0; only manifest, AsyncTimeout$Watchdog.class and JvmFileHandle.class differ. Watchdog javap output is identical, so that binary difference alone does not establish changed behavior. No published sources jar exists (404). Source provenance is 3.4.0 release a161b07fb1b459371458ae6d9508ec31df280428 plus short-read correction from c1166213, rather than a single source commit yet identified.
OBSERVED: Target short-read-2 POM returned 404 before publication. Read-only BuildTestEmbedded PR https://github.com/CodexCoder21Organization/BuildTestEmbedded/pull/926 records publication of short-read-1 and its digest, but not its exact build commit.
INFER: The compatible release should retain the exact prior binary entries and replace the common Okio facade implementation from 3.4.0 with fe6ac99a's nullable correction. This avoids changing any other behavior and retains the existing short-read class byte-for-byte.
The mechanism is that use stores a valid nullable block result then applies result!! after closing, and the evidence that demonstrates it is reviewer F3 plus the matching published artifact and 3.4.0 source at Okio.kt:72. A new public-API test will make this independently visible at the owning layer before any product edit.

## Resource invariants
| Resource / result | Creator / owner | Close / terminal state | Public behavior / test |
| --- | --- | --- | --- |
| Buffered sink supplied to use | Test creates and caller owns until use | use closes exactly once | Null result returns null; counted forwarding sink delegates to real Buffer |
| Same sink when block throws | Test creates; use owns cleanup | use closes exactly once before propagating | Same exception identity and full message; close count 1 |
| Result value | Block creates; caller receives | Nullable values are valid | Preserve null without an additional result check |
No concurrent access or restart state changes are involved.

## Challenges for the orchestrator
- Brief publisher repository URL is stale; actual README found via GitHub repo search above.
- Fork README lacks the requested historical release procedure and no short-read release tag exists. Binary/source comparisons and historical consumer PR identify its source family; exact publication command still being traced.

## 2026-10-14 20:14 UTC — failing-test milestone
OBSERVED: New native-suite UseNullableResultTest compiled against the exact published short-read-1 jar. JUnit XML: 4 tests, 3 pass, 1 failure; nullResultClosesExactlyOnce asserts closeCount=1 first, then fails with "use rejected a nullable block result: java.lang.NullPointerException". throwingBlockClosesExactlyOnce, nullResultStillPropagatesCloseFailure, and blockFailureKeepsCloseFailureSuppressed pass. Reports: out/okiofix1-red/TEST-junit-vintage.xml; full assertion stack in out/okiofix1-red.log. This is a deterministic baseline failure, not a compile failure.
OBSERVED: FileSystem.read and FileSystem.write contain inlined use code inside the artifact. Rebuilding only Okio.use would leave those inline bodies wrong. The artifact build must rebuild the complete 3.4.0 JVM source set with the nullable correction, retaining the existing short-read correction.
Plan: first push contains only the failing public test. Then build complete 3.4.0 commonMain+jvmMain with the nullable correction and retain the exact prior Watchdog class. Compile the fork's original JVM tests against that fixed jar and run them once, in parallel with the parent preparing the repeatable build script. Finally run focused 3/3 gates, publish, verify downloaded binary, and open PR.

## 2026-10-14 20:18 UTC — build and coverage progress
OBSERVED: First failing-test checkpoint pushed as https://github.com/CodexCoder21Organization/okio/commit/db9d225b . Added source-level integration coverage for FileSystem.read/write with real in-memory filesystem delegates and counted forwarding source/sink closure, plus a null-receiver/null-result case. These extend the same nullable contract; no existing test is edited.
OBSERVED: Native-suite worker is preparing the unchanged 3.4.0 commonTest+jvmTest test sources and local testing-support helper, excluding stock okio jars from its runtime. Parent retains diagnosis, source/artifact choices, publication, test review and code review.
OBSERVED: Full 3.4.0 baseline JVM source compile succeeded; the matching compile with only fe6ac99a Okio.kt is now running. The repeatable build will overlay only classes that differ between those two compiles onto the hash-verified previous artifact. This retains both historical special classes and all unrelated binary entries byte-for-byte.
Additional resource invariants before integration tests: read opens a real in-memory source; use owns exactly-one close before returning null. Write opens a real in-memory sink; use flushes/closes exactly once and callers see the written record plus null result. A null receiver creates no resource and must still preserve a null result. Tests drive those public paths directly.

## 2026-10-14 20:21 UTC — artifact build milestone
OBSERVED: Both complete 3.4.0 source compiles succeeded using Kotlin 1.8.22, language/API 1.8, JVM target 1.8. Only fe6ac99a Okio.kt differed between compiles. Four class bodies changed: okio/FileSystem.class, okio/Okio__OkioKt.class, okio/ZipFileSystem.class, okio/internal/-FileSystem.class. These are overlaid on the exact previous artifact; all other entries are preserved, including JvmFileHandle and Watchdog. Manifest Bundle-Version advances to short-read-2.
OBSERVED: Candidate JAR SHA-256 c95af313fc9017f8dd356de73428f664009895c65c748abb07d2ce12c4c34e64; POM SHA-256 ba63e01956c2128e1eed2c0f576a461c4fda7bd29fb8d5650d7559dea391a438. Both files are staged under out/okiofix1-release; nothing has been published yet. Build log: out/okiofix1-build.log. Temporary compiler source directory removed automatically.
OBSERVED: The build script is now part of the pending PR work, and README records its public behavior and rebuild requirement. Native-suite worker has the fixed jar and is waiting for a permitted slot. Focused baseline/fixed/mutation runs are started with a hard outer deadline and XML as the only verdict source.

## 2026-10-14 20:23 UTC — focused gate complete
OBSERVED: Expanded baseline XML has 7 tests: 4 expected nullable-result assertion failures, 3 passes, 0 errors/skips. Fixed candidate XML has 7/7 passes on each of three independent invocations (21 first-attempt passes total, no retry mechanism). Product-only binary rollback plus rebuilding the unchanged inline callers returns the same 4 nullable assertion failures, with 3 passes. The short-read public FileHandle harness from the existing upstream branch passes all three schedules on each of three independent invocations, reported as 1/1 JUnit test per invocation. No gate was repeated beyond these prescribed runs.
OBSERVED: Public signatures of all four affected classes equal the prior jar under javap -public. Fixed Okio.use contains zero Intrinsics.checkNotNull result calls. JvmFileHandle SHA-256 remains 0d0b267a4f569a349ac5d4fc61dcdf64288af006fe1b594b6a6533691873b03c; Watchdog remains a5b056b06fae51ceb2b350deba29c0c3294772b0d55d1065991ba62e24b5657b.
OBSERVED: Dedicated test coverage and source/packaging review passes are recorded in out/okiofix1-review.md. No review gaps found; no existing test changes. The single existing JVM suite run is next and is still required before publication.
Plan state: provenance/source corrections verified (historical release has no build-commit marker; source base/tag and short-read source commit are identified), failing-first tests done, focused gates and mutation done, existing full JVM suite in progress, publication and PR pending.
