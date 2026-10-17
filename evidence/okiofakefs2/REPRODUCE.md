# Entry admission verification

Product/test branch: https://github.com/CodexCoder21Organization/okio/tree/wip/okiofakefs1
Candidate: https://github.com/CodexCoder21Organization/okio/commit/ba235959c1bdf0ab615429ba250061900d4607d8
Gate: https://github.com/CodexCoder21Organization/okio/actions/runs/39009069601

Build with `python3 scripts/buildFakeFilesystemDirectoryBackport.py --output-directory /tmp/okio-admission-artifact`, then run `scripts/testFakeFilesystemDirectoryBackport.bash /tmp/okio-admission-artifact /tmp/okio-admission-native`.

The original reviewer proofs are adopted without edits as NeighborEntryReviewTest. The public tests use exact core okio-jvm:3.4.0.2. No remote BuildTest service is used. Native JUnit produces no internal retries; audit every testcase and attempt-history element.

Red-first native XML: red (18 new assertion failures plus 31 passing prior rows), boundary-red (18 assertion failures), metadata-red (3 assertion failures). Product-only controls: mutation (36 assertion failures plus 31 passing prior rows), metadata-mutation (3 assertion failures). The rebuilt control JAR has the exact checksum of published -2.

The temporary GitHub gate workflow exists only under wip/gha-run/okiofakefs2, checks out the full candidate SHA and uploads native XML plus artifact JAR/POM. No investigation output or workflow is in the PR branch.
