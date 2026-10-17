# Public entry review proofs

Reviewed product commit: https://github.com/CodexCoder21Organization/okio/commit/b942f8201b3f35605a5a8c3849026c5dc978607e .
Original accepted gate: https://github.com/CodexCoder21Organization/okio/actions/runs/39004533954 .

NeighborEntryReviewTest.java contains three independent public callback orderings. Each repeats three times, makes a fresh in-memory filesystem, commits a child during the callback, and verifies the outer operation rejects the new entry and preserves the child. Recorded native results are nine intended assertion failures, no errors/skips, and no earlier attempts. These expose retained admission gaps in openReadWrite, createSymlink and atomicMove.

DirectoryWrapperReviewTest.java checks the brief's file-rejection expectation for the inherited plural createDirectories wrapper. Recorded native results are three intended assertion failures, no errors/skips, and no earlier attempts. The implementation belongs to the core dependency; upstream has the same behavior. This is a qualification of the upstream-compatibility premise, separate from the three entry findings.

The original gate tests were not rerun. Their native rows were downloaded and audited; xml-audit.txt records the positive, baseline-negative and product-only mutation counts. original-gate-build.log contains the actual public-signature check and tested hashes. artifact-provenance.txt records the independent served-artifact and all-class signature comparison. exact-core-directory-wrapper-bytecode.txt records the owning core method's exists-only scan.

To run the public probes again, obtain the tested artifact from the original gate or the served Maven coordinate. The JAR's required SHA-256 is:

```
21e60aadff3bf4906847103742c28b6a197349ecf58f132bd4eebf07e4c9e7a4
```

With an allocated local build slot, a fresh temporary directory, Java 21 and Coursier, run from this repository root (replace the two paths below):

```bash
export LC_ALL=C.UTF-8
reviewJar=/absolute/path/okio-fakefilesystem-jvm-3.4.0-r576-registry-lock-2.jar
reviewWork=/absolute/path/fresh-temporary-directory
mkdir -p "$reviewWork/classes"
coursier fetch --classpath -r https://kotlin.directory \
  com.squareup.okio:okio-jvm:3.4.0.2 \
  org.jetbrains.kotlinx:kotlinx-datetime-jvm:0.4.0 \
  org.junit.platform:junit-platform-console-standalone:1.10.0 > "$reviewWork/classpath"
reviewClasspath="$reviewJar:$(cat "$reviewWork/classpath")"
javac -J-Xmx256m -cp "$reviewClasspath" -d "$reviewWork/classes" \
  evidence/revokio1/NeighborEntryReviewTest.java \
  evidence/revokio1/DirectoryWrapperReviewTest.java
timeout 120 java -Xmx256m -cp "$reviewWork/classes:$reviewClasspath" \
  org.junit.platform.console.ConsoleLauncher execute \
  --select-class NeighborEntryReviewTest --select-class DirectoryWrapperReviewTest \
  --reports-dir "$reviewWork/native" --disable-banner
```

Expect exit 1 and twelve assertion-failure rows. Count native XML rows and earlier attempts, rather than accepting the process exit alone. The lane ran the two classes separately, once each; its recorded files retain that separation. The bounded original command wrapper is preserved as run-neighbor-proof.bash, including its acquired/released local slot and temporary-directory cleanup.

No product source, existing test, PR description, published artifact, deployed service, or handoff was modified during this review.
