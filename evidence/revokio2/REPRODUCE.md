# Independent review of ba235959

Reviewed PR: https://github.com/CodexCoder21Organization/okio/pull/5
Reviewed commit: https://github.com/CodexCoder21Organization/okio/commit/ba235959c1bdf0ab615429ba250061900d4607d8
Evidence branch: https://github.com/CodexCoder21Organization/okio/tree/wip/revokio2-evidence/evidence/revokio2

No product, existing test, README, or PR file was changed by this review.

## Original fork verification

Build using `python3 scripts/buildFakeFilesystemDirectoryBackport.py --output-directory <empty-artifact-directory>`.
Run `scripts/testFakeFilesystemDirectoryBackport.bash <artifact-directory> <report-directory>`.
The review ran that script exactly once: 70/70 native rows, including 39 new rows, all 31 earlier rows, and all 20 registry trials. See `native/`, `native-audit.txt`, and `build-test-console.txt`.

## Additional review proofs

Use Java 21, Coursier, `LC_ALL=C.UTF-8`, and the ordinary lane build-slot rule. Compile the two Java files against:

- The artifact built above, `okio-fakefilesystem-jvm-3.4.0-r576-registry-lock-3.jar`.
- `coursier fetch --classpath -r https://kotlin.directory com.squareup.okio:okio-jvm:3.4.0.2 org.jetbrains.kotlinx:kotlinx-datetime-jvm:0.4.0 org.junit.platform:junit-platform-console-standalone:1.10.0`.

Run the JUnit console with a 256 MiB test JVM and a 180-second outer bound. Select `--select-method DanglingAdmissionReviewTest#moveAdmissionPreservesUnrelatedDirectory` and `--select-class ReadLookupReviewTest`, retaining native reports with `--reports-dir <directory>`. These are deliberate assertion-red proofs, not a positive gate.

`DanglingAdmissionReviewTest` uses a one-shot public clock callback to create `/target -> /parent/missing` before move admission. `/parent/target/child` already contains `confirmed`. Moving `/source` to `/target` instead replaces unrelated `/parent/target`, making its child inaccessible. Its move method is assertion-red 3/3 in `dangling-native/`.

The initial broad exploratory selection also ran the directory and symlink methods in that class. Those methods expected a normal return, but the product correctly rejected the existing symlink with `IOException: already exists: /target`. Their six native error rows are retained for transparency and are not product findings or assertion-red proof. No source or assertion was changed, no row was rerun, and no existing test was edited to obtain a later pass. Select the move method alone when reproducing the counted finding.

`ReadLookupReviewTest` starts with `/alias -> /old` and no `/alias/ghost`. At the lookup clock callback it replaces the alias with `/alias -> /new`, then creates `/old/ghost`. The callback checks the requested alias child is still absent. Suspended metadata, canonicalization and listing nevertheless report the retired parent's new child. `createDirectories` accepts that metadata and returns without creating the requested child. All four methods are assertion-red 3/3 in `read-native/`, zero errors/skips/earlier attempts.

## Independent receipt checks

The original successful gate is https://github.com/CodexCoder21Organization/okio/actions/runs/39009069601 . Downloaded its native XML independently and compared byte-for-byte with the earlier evidence; all files agree. Its tested JAR equals independently served -3 and the exact-head locally rebuilt artifact. All 11 public class declarations and class names match -1/-2/-3; POM dependencies are unchanged. See `api-audit.txt`, `native-audit.txt`, and `original-gate-state.json`.
