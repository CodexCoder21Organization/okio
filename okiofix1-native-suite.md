# Native Okio JVM suite run

## Plan

- [x] Inspect the original 3.4.0 JVM test sources, resources, build configuration, and generated fixtures.
- [x] Prepare a disposable suite workspace and record the fixed jar handoff state.
- [ ] After the parent confirms `build/nullable-use/fixed.jar` is ready, compile the unchanged commonTest+jvmTest sources against it.
- [ ] Run the complete existing JVM suite once with JUnit XML; report totals and every failure with its full stack trace.
- [ ] Stop processes started for this run and remove only disposable suite files.

## Progress

- OBSERVED: The supplied working tree is `/home/u/lanes/util20261014/workspace/okiofix1`; an extracted upstream source snapshot exists at `build/nullable-use/source`.
- OBSERVED: The top-level README describes Okio but gives no build or test commands. No repository-root `AGENTS.md` appeared in the file listing.
- OBSERVED: The source snapshot contains `okio/src/commonTest`, `okio/src/jvmTest/kotlin`, `okio/src/jvmTest/java`, and `okio/src/jvmTest/resources`.
- INFER: The task's established cause is that Okio 3.4.0 `use` treats a valid `null` result as an error. This lane is only checking that the unchanged native JVM suite passes with the parent's fixed jar.
- OBSERVED: The fixed jar is not yet confirmed ready. No suite compilation or test run has started.
- OBSERVED: Required guidance read: `/home/u/lanes/util20261014/br/common.md`, `README.md`, `/home/u/lanes/util20261014/out/okiofix1-testing.md`, and `/home/u/lanes/util20261014/out/okiofix1-philosophy.md` (relevant sections are being checked before execution).
- OBSERVED: The source snapshot's version catalog declares kotlinx-datetime 0.4.0; the parent confirmed to use that exact upstream version.
- OBSERVED: The Gradle config adds no generated test source/resource tasks. The only JVM resource inputs are three checked-in files under `okio/src/jvmTest/resources`, including two UTF-8 path entries; they must be copied intact to the test classpath.
- INFER: Published fakefilesystem/testing-support artifacts may transitively bring the stock Okio jar. The manual test classpath will exclude that coordinate so the fixed jar is the only Okio implementation.

- OBSERVED: The upstream 3.4.0 JVM test source layout contributes 25 common test Kotlin files, 31 JVM test Kotlin files, and 23 JVM Java files. The local `okio-testing-support` helper contributes 6 original Kotlin files; the total prepared Kotlin source list is 62. The configured `src/jvmTest/hashFunctions` directory exists but contains no files.
- OBSERVED: `okio-testing-support:3.4.0` cannot be resolved from Maven Central (Coursier reports its POM is not found). The source snapshot includes the module, so the prepared compile step uses its unchanged commonMain and jvmMain sources. This was a dependency-resolution issue only; no test was compiled or executed.
- OBSERVED: `dependencies.all.cp` includes fakefilesystem 3.4.0, datetime 0.4.0, AssertJ 3.24.2, Jimfs 1.3.0, Kotlin test/JUnit dependencies, and their required transitive jars. `com.squareup.okio:okio` is excluded, so the fixed jar will be the only Okio implementation at compile and runtime.
- OBSERVED: `compile-tests.bash` and `run-suite.bash` are syntax-checked and stored only under the ignored suite directory. The compile script waits up to 10 minutes for build slot 1, 2, or 4 and releases its slot on exit. The run script emits JUnit XML into the same disposable directory.
- OBSERVED: Parent confirmed the fixed jar is still building. Suite compilation and execution remain pending that signal.
