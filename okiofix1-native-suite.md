# Native Okio JVM suite run

## Plan

- [x] Inspect the original 3.4.0 JVM test sources, resources, build configuration, and generated fixtures.
- [x] Prepare a disposable suite workspace and record the fixed jar handoff state.
- [x] Compile unchanged commonTest+jvmTest sources against the parent-confirmed fixed jar.
- [x] Run the complete existing JVM suite once with JUnit XML; report totals and every failure with its full stack trace. The run produced three CWD assertion failures; the authorized corrected-CWD method-only verification also completed, with all three passing.
- [x] Confirmed all compiler and test processes started by this lane exited; logs and reports remain preserved for parent review.

## Progress

- OBSERVED: The supplied working tree is `/home/u/lanes/util20261014/workspace/okiofix1`; an extracted upstream source snapshot exists at `build/nullable-use/source`.
- OBSERVED: The top-level README describes Okio but gives no build or test commands. No repository-root `AGENTS.md` appeared in the file listing.
- OBSERVED: The source snapshot contains `okio/src/commonTest`, `okio/src/jvmTest/kotlin`, `okio/src/jvmTest/java`, and `okio/src/jvmTest/resources`.
- INFER: The task's established cause is that Okio 3.4.0 `use` treats a valid `null` result as an error. This lane is only checking that the unchanged native JVM suite passes with the parent's fixed jar.
- OBSERVED (initial preparation): The fixed jar was not yet confirmed ready, and no suite compilation or test run had started. Parent later confirmed the fixed jar ready and provided its checksum.
- OBSERVED: Required guidance read: `/home/u/lanes/util20261014/br/common.md`, `README.md`, `/home/u/lanes/util20261014/out/okiofix1-testing.md`, and `/home/u/lanes/util20261014/out/okiofix1-philosophy.md` (relevant sections read: hermetic and hardware-independent tests, TDD, real dependencies, reproducing before fixing, and retaining failures).
- OBSERVED: The source snapshot's version catalog declares kotlinx-datetime 0.4.0; the parent confirmed to use that exact upstream version.
- OBSERVED: The Gradle config adds no generated test source/resource tasks. The only JVM resource inputs are three checked-in files under `okio/src/jvmTest/resources`, including two UTF-8 path entries; they must be copied intact to the test classpath.
- INFER: Published fakefilesystem/testing-support artifacts may transitively bring the stock Okio jar. The manual test classpath will exclude that coordinate so the fixed jar is the only Okio implementation.

- OBSERVED: The upstream 3.4.0 JVM test source layout contributes 25 common test Kotlin files, 31 JVM test Kotlin files, and 23 JVM Java files. The local `okio-testing-support` helper contributes 6 original Kotlin files; the total prepared Kotlin source list is 62. The initial direct enumeration returned no files through `src/jvmTest/hashFunctions` because that configured path is a symlink and `find` did not follow it.
- OBSERVED: `okio-testing-support:3.4.0` cannot be resolved from Maven Central (Coursier reports its POM is not found). The source snapshot includes the module, so the prepared compile step uses its unchanged commonMain and jvmMain sources. This was a dependency-resolution issue only; no test was compiled or executed.
- OBSERVED: `dependencies.all.cp` includes fakefilesystem 3.4.0, datetime 0.4.0, AssertJ 3.24.2, Jimfs 1.3.0, Kotlin test/JUnit dependencies, and their required transitive jars. `com.squareup.okio:okio` is excluded, so the fixed jar will be the only Okio implementation at compile and runtime.
- OBSERVED: `compile-tests.bash` and `run-suite.bash` are syntax-checked and stored only under the ignored suite directory. The compile script uses build slot 1, 2, or 4 and releases its slot on exit; its acquisition bound was reduced after the first timeout. The run script emits JUnit XML into the same disposable directory.
- OBSERVED (initial setup): Parent had not yet confirmed the fixed jar. Subsequent messages confirmed the fixed artifact and required inputs.

## Compile preparation stop

- OBSERVED: The first suite Kotlin compiler was stopped before it completed after the parent asked for a 768 MB compiler heap cap. The helper module compile had already succeeded, and its output remains in `suite/support-classes`; it will be reused. This was a preparation stop, not a test attempt or test failure.
- OBSERVED: Reviewing the classpath found `okio-jvm:3.4.0` still arrived transitively even though the multiplatform `okio` coordinate was excluded. I regenerated the classpath with both coordinates excluded. The only remaining `com.squareup.okio` jars are fakefilesystem metadata and fakefilesystem-jvm 3.4.0; the fixed jar is the only Okio implementation jar.
- OBSERVED: The compile script now caps the test compiler at 768 MB and uses javac `-source 8 -target 8`, matching the upstream Gradle source/target setup. The test runtime classpath now includes the already compiled testing-support classes and uses a 512 MB heap.

## Compile source-set correction

- OBSERVED: The first completed compiler attempt exited 1 before Java compilation or test execution. The unchanged JVM tests import `okio.internal.HashFunction`, `Md5`, `Sha1`, `Sha256`, `Sha512`, and `Hmac`; these sources are absent from the fixed JVM production jar.
- OBSERVED: Parent verified that upstream Gradle config points `jvmTest` at `src/jvmTest/hashFunctions`, which is a tracked symlink to `../hashFunctions/kotlin`. The extracted source copy preserved this valid symlink and its target; my initial manual source enumeration did not follow the symlink, so it omitted these six unchanged test-side sources.
- OBSERVED: Preserved the complete first compiler output as `build/nullable-use/suite/kotlin-compile-setup-1.log`. Updated only the disposable compile script to add the original `src/hashFunctions/kotlin/**/*.kt` files to JVM test compilation. No source or test files were changed.
- INFER: With this source-set linkage restored, the manual compile now matches the original Gradle JVM test compilation inputs for the missing hash functions.
- OBSERVED (20:35 UTC): The second suite compiler attempt is still in the required build-slot acquisition loop; eligible slots 1, 2, and 4 remain occupied. The loop has a hard 10-minute limit and no compile or suite execution is running yet.
- OBSERVED (20:39 UTC): The first 10-minute build-slot acquisition ended with exit 3 because all eligible slots remained occupied. No compiler or test process started. Per parent direction, a second acquisition is now bounded to five minutes; helper classes and source inputs remain preserved.
- OBSERVED (20:44 UTC): The second slot-acquisition attempt also exited 3 without starting a compiler or test process; all eligible slots remained occupied through the five-minute bound. The unchanged tests have not yet compiled or run. The 20:59 hard stop leaves about fifteen minutes for any next authorized slot window, compile, and one suite run.

- OBSERVED (20:44 UTC): Parent authorized one final slot-acquisition window ending no later than 20:50 UTC. The existing acquisition loop was bounded to about 4.5 minutes; it acquired slot 4 at approximately 20:45 UTC, then compilation started immediately. Parent later extended the lane hard process stop to 21:05 UTC.
- OBSERVED (20:48 UTC): Confirmed the fixed jar checksum (`c95af313fc9017f8dd356de73428f664009895c65c748abb07d2ce12c4c34e64`), confirmed no stock Okio 3.4.0 implementation jar in the runtime dependencies, and confirmed slot 4 released after compilation.
- OBSERVED (20:48 UTC): Started the one authorized full JVM suite run with the original checked-in resources, extracted zip on PATH, fixed jar first on runtime classpath, a 512 MB JVM heap, JUnit XML reporting, and a 900-second hard timeout. The run was still progressing successfully at 20:49 UTC; no verdict is available yet.
- OBSERVED (20:48 UTC): Native JVM test compilation succeeded against the fixed jar using 62 test Kotlin sources (25 commonTest, 31 jvmTest, 6 hashFunctions through the configured symlink) and 23 Java sources. The compiler emitted only warnings; Java emitted the expected JDK source/target 8 warnings. All three original JVM test resources were copied. The helper module compilation was reused from the prior successful preparation; compile output is retained in `build/nullable-use/suite/kotlin-compile.log`, `java-compile.log`, and `support-compile.log`.
- OBSERVED: The compile process exited successfully and its build-slot trap released the acquired slot; the full suite was started immediately afterward.
- OBSERVED (20:50 UTC): The running suite has so far reported three failures, all variants of `AbstractFileSystemTest.canonicalizeDotReturnsCurrentWorkingDirectory`. The shared original test asserts that the process current directory ends with `/okio/okio` on JVM; this direct JUnit process was launched from the repository root, so it fails with `/home/u/lanes/util20261014/workspace/okiofix1`. The upstream Gradle JVM test task defaults to the `okio` module project directory. This identifies a test-harness working-directory mismatch in this already-running sole suite invocation. I am preserving the run and will report the completed counts and full stack traces; no rerun is being started without parent direction.


## Full suite result (one run)

- OBSERVED: The complete existing JVM test set ran once with exit code 1 after 227.905 seconds. ConsoleLauncher reported 4,844 tests found, 26 skipped, 4,818 started, 21 aborted, 4,794 successful, and 3 failed. The Vintage XML reports 47 skipped because its skipped count includes the 21 assumption-aborted tests; there were zero errors and no additional failure cases.
- OBSERVED: XML reports are preserved under `build/nullable-use/suite/reports/`; the verbose full run is preserved as `build/nullable-use/suite/suite-run.log`.
- INFER: All three failures are caused by the process working directory used by this manual JUnit invocation, not by the nullable-use fix. The unchanged shared test expects a JVM CWD ending in `/okio/okio`; this invocation used the repository root. Parent authorized one method-only run of the three affected cases from a lane-local real directory ending `/okio/okio`, while keeping the full-suite report unchanged; all three passed in that corrected-CWD run.

### Failure 1: `okio.NioDefaultFileSystemWrappingFileSystemTest#canonicalizeDotReturnsCurrentWorkingDirectory`

```text
java.lang.AssertionError: /home/u/lanes/util20261014/workspace/okiofix1
    at kotlin.test.junit.JUnitAsserter.fail(JUnitSupport.kt:56)
    at kotlin.test.Asserter$DefaultImpls.assertTrue(Assertions.kt:652)
    at kotlin.test.junit.JUnitAsserter.assertTrue(JUnitSupport.kt:30)
    at kotlin.test.Asserter$DefaultImpls.assertTrue(Assertions.kt:662)
    at kotlin.test.junit.JUnitAsserter.assertTrue(JUnitSupport.kt:30)
    at kotlin.test.AssertionsKt__AssertionsKt.assertTrue(Assertions.kt:44)
    at kotlin.test.AssertionsKt.assertTrue(Unknown Source)
    at okio.AbstractFileSystemTest.canonicalizeDotReturnsCurrentWorkingDirectory(AbstractFileSystemTest.kt:75)
    at java.base/java.lang.reflect.Method.invoke(Method.java:580)
```

### Failure 2: `okio.JvmSystemFileSystemTest#canonicalizeDotReturnsCurrentWorkingDirectory`

```text
java.lang.AssertionError: /home/u/lanes/util20261014/workspace/okiofix1
    at kotlin.test.junit.JUnitAsserter.fail(JUnitSupport.kt:56)
    at kotlin.test.Asserter$DefaultImpls.assertTrue(Assertions.kt:652)
    at kotlin.test.junit.JUnitAsserter.assertTrue(JUnitSupport.kt:30)
    at kotlin.test.Asserter$DefaultImpls.assertTrue(Assertions.kt:662)
    at kotlin.test.junit.JUnitAsserter.assertTrue(JUnitSupport.kt:30)
    at kotlin.test.AssertionsKt__AssertionsKt.assertTrue(Assertions.kt:44)
    at kotlin.test.AssertionsKt.assertTrue(Unknown Source)
    at okio.AbstractFileSystemTest.canonicalizeDotReturnsCurrentWorkingDirectory(AbstractFileSystemTest.kt:75)
    at java.base/java.lang.reflect.Method.invoke(Method.java:580)
```

### Failure 3: `okio.NioSystemFileSystemTest#canonicalizeDotReturnsCurrentWorkingDirectory`

```text
java.lang.AssertionError: /home/u/lanes/util20261014/workspace/okiofix1
    at kotlin.test.junit.JUnitAsserter.fail(JUnitSupport.kt:56)
    at kotlin.test.Asserter$DefaultImpls.assertTrue(Assertions.kt:652)
    at kotlin.test.junit.JUnitAsserter.assertTrue(JUnitSupport.kt:30)
    at kotlin.test.Asserter$DefaultImpls.assertTrue(Assertions.kt:662)
    at kotlin.test.junit.JUnitAsserter.assertTrue(JUnitSupport.kt:30)
    at kotlin.test.AssertionsKt__AssertionsKt.assertTrue(Assertions.kt:44)
    at kotlin.test.AssertionsKt.assertTrue(Unknown Source)
    at okio.AbstractFileSystemTest.canonicalizeDotReturnsCurrentWorkingDirectory(AbstractFileSystemTest.kt:75)
    at java.base/java.lang.reflect.Method.invoke(Method.java:580)
```


## Corrected-CWD targeted verification

- OBSERVED: Created the real working directory `/home/u/lanes/util20261014/workspace/okiofix1/build/nullable-use/native-cwd/okio/okio`; its physical path ends with the exact `/okio/okio` suffix required by the unchanged test.
- OBSERVED: Per parent direction, ran only the three named methods from the original full-suite failures once, using the same fixed jar, compiled test classes/resources, dependency classpath, zip PATH, 512 MB heap, and 900-second process timeout. JUnit XML is in `build/nullable-use/suite/reports-corrected-cwd/`; verbose output is `build/nullable-use/suite/targeted-cwd-run.log`.
- OBSERVED: Corrected-CWD targeted result: 3 tests found, 3 started, 3 successful, 0 skipped, 0 aborted, 0 failed, and 0 errors in the Vintage XML; all 6 containers succeeded. The three canonicalize tests now pass without changing any test source or assertion. The three canonicalize tests now pass without changing any test source or assertion.
- OBSERVED: No compiler, suite, or targeted-test process remains running. Full-suite and targeted logs/reports are preserved for parent review.

## Brief item states

- Inspect source and build setup: DONE. Original sources, resources, dependencies, symlinked hashFunctions tests, and expected working-directory contract were inspected.
- Compile unchanged native JVM tests against fixed jar: DONE. Compilation succeeded with the fixed jar first on the test classpath; all original Kotlin and Java test files and resources were included.
- Run full existing JVM suite once and report counts/failures: DONE. One run completed with the counts and three complete stacks recorded above; the failures were conclusively traced to process CWD setup.
- Verify those three failures with original assertions under correct CWD: DONE. All three passed in the authorized targeted invocation.
- Stop processes and provide evidence: DONE. All launched processes exited; logs and XML reports remain in the ignored disposable build directory and the findings file outside tracked source.

- OBSERVED: Final `ps` check found no `K2JVMCompiler`, JUnit ConsoleLauncher, suite runner, or targeted runner process from this lane. No tracked source/test files were changed by this lane. Per parent direction, all ignored `build/nullable-use` evidence is retained.
