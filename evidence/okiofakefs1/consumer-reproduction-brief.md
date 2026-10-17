# FakeFileSystem directory creation can remove a committed child

## State

BuildTestEmbedded source is preserved at https://github.com/CodexCoder21Organization/BuildTestEmbedded/commit/9c04feeb9dfed02d729e4ca65a8ddcd6cccbd133 . Its required first-attempt gate is NOT qualified. No PR, merge, deployment or new Maven publication was performed.

## Consumer observation

In https://github.com/CodexCoder21Organization/BuildTestEmbedded/actions/runs/39002070995 , testUnownedDeletionConfirmationSurvivesStaleListingAndRestart failed on its FIRST attempt with `An ID without a durable owner must retain an observable unowned confirmation.` Its later internal attempt passed and is not accepted over that failure. All four provider deletions and local publication callbacks completed, but an expected per-ID file was absent. The full native trace and console are retained in final-97/new-2-native/native.xml and new2-first-attempt-console.txt.

The new confirmation writers call the public FileSystem.createDirectories on the same initially absent directory before writing separate per-ID files. The component never deletes those proof files. Four independent provider replies allow those directory operations to overlap.

## Owning layer and mechanism

The remaining defect is in `com.squareup.okio:okio-fakefilesystem-jvm:3.4.0-r576-registry-lock-1`, with consumer core `com.squareup.okio:okio-jvm:3.4.0.2`. It is separate from the already-fixed open-file registry problem. Exact resolved JAR hashes are in artifact-hashes.json.

The unchanged directory method is visible at https://github.com/lysine-dev/okio/blob/a161b07f/okio-fakefilesystem/src/commonMain/kotlin/okio/fakefilesystem/FakeFileSystem.kt#L378 . It looks up the directory and its parent, then calls the supplied clock and unconditionally assigns a new empty Directory to the parent's child entry. It does not preserve a directory created between that lookup and assignment.

The deterministic public-API ordering is:

1. Caller A starts createDirectories for an absent /proof directory.
2. Its createDirectory reaches the public Clock.now callback after obtaining the parent reference.
3. The callback re-enters the same filesystem, creates /proof, writes and closes /proof/1.absent, and verifies that the file exists.
4. Caller A resumes and replaces /proof with a new empty directory. Its call returns normally, but /proof/1.absent is now absent.

This is the same critical ordering two genuine concurrent directory creators can reach. The clock callback forces the ordering directly, without timing calibration, extra processor work, sleeps or a large dataset. The expected result is that the committed child remains present. Directory creation must not replace a directory that another completed call created.

## Native proof

DirectoryProof.java uses only public FakeFileSystem, Clock, FileSystem and Sink APIs. It creates a fresh in-memory filesystem for each of three native JUnit repetitions and checks that every handle closes. There are no mocks or private-member reads.

TEST-junit-jupiter.xml reports THREE tests, THREE assertion failures, ZERO errors and ZERO skipped tests, using the exact consumer core version. The assertion is `A directory creation must preserve a child committed by a re-entering caller.` The two auxiliary JUnit engine reports have zero rows and are not counted as verdicts. The initial stock-core probe also failed 3/3; the exact-core probe is the relevant proof.

Run with Java 21 and the public Maven artifacts:

```
coursier fetch --classpath -r https://kotlin.directory com.squareup.okio:okio-fakefilesystem-jvm:3.4.0-r576-registry-lock-1 com.squareup.okio:okio-jvm:3.4.0.2 org.junit.platform:junit-platform-console-standalone:1.10.0 > classpath.txt
javac -cp "$(cat classpath.txt)" DirectoryProof.java
java -Xmx256m -cp ".:$(cat classpath.txt)" org.junit.platform.console.ConsoleLauncher execute --select-class DirectoryProof --reports-dir native-results --disable-banner
```

The command is expected to return an unsuccessful test status and write native XML with the three assertion failures. Source, the lane's bounded command wrapper and native reports are preserved beside this brief. No compiled class or JAR is committed.

## Required next layer

The lane explicitly forbids fixing a dependency in the consumer, or publishing a new artifact without authorization. Therefore no directory precreation, consumer lock, delay or retry is added to hide this behavior. An authorized upstream change must make directory admission preserve existing children across re-entry and concurrent calls, publish a tested artifact, and then update the consumer test dependency.

The current service gate also has twelve selectors with no native rows because historical test filenames do not match their declared function names. missing-selector-map.json lists every file and declared name. Use the exact test-file selectors when requalifying, and audit rows by file as well as function name. Do not count absent rows as passes.

After the dependency change, the complete required gate remains eight new scenarios three times and all 73 named neighbors once, with only first attempts accepted. The original product-only controls remain assertion-red 24/24. No completed accepted gate has been followed by another positive batch.
