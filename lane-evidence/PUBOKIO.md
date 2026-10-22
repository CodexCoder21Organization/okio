
# PUBOKIO publication verification

Plan:
- IN PROGRESS: Read required instructions, locate repository, confirm target POM is absent, and clone main with the required merge.
- TODO: Rebase, build the backport, run all backport scenarios, and checkpoint evidence remotely.
- TODO: Publish only com.squareup.okio:okio-jvm:3.4.0.3 after rechecking absence.
- TODO: Download published JAR/POM, compare SHA-256, inspect bytecode/dependencies, push final evidence and report.

OBSERVED: User explicitly authorized exactly com.squareup.okio:okio-jvm:3.4.0.3; no overwrite, merging, deployment, or other artifact publication is authorized.

## 2026-10-22T06:50:18.493847+00:00 — initial verification

OBSERVED: The exact target POM GET returned HTTP 404. Response saved as PUBOKIO-initial-pom-response.
OBSERVED: Repository search located https://github.com/CodexCoder21Organization/okio. Fresh clone main is aa6f2aae1f6fbc28e8ac7d5b816d47970e3884e7, the required merge from https://github.com/CodexCoder21Organization/okio/pull/7. Required merge ancestry check passed; git fetch origin and git rebase origin/main succeeded. No repository AGENTS.md exists.
OBSERVED: Read root README, both backport build/test scripts, and the three selected test scenario files. Read github-repos and publish-maven-artifact skills (the latter at /home/leslie/.claude/skills/publish-maven-artifact/SKILL.md). Read Engineering Philosophy and the relevant Extreme-Condition Testing, Never Calibrate a Reproducer to a Machine, and test-layout guidance in Testing Architecture via authenticated GitHub content. Public unauthenticated documentation URLs returned 404; authenticated access succeeded.
OBSERVED: memory.max=10737418240; initial memory.current=10247757824 bytes. memory.stat later showed anon=7125213184 and file=2008997888 bytes. Build and regression run will share the machine-wide complete-suite lock to avoid overlap with a full suite. No JVM flags or test limits will be changed.
OBSERVED: Native scenarios cover the forced two-caller NIO creation ordering (32 rounds for both mustCreate values), file targets, competing file creation, directory replacement, and non-existence-related creation errors. The supplied compatible runner uses the historical error spelling and has no retry mechanism.

Plan update: initial verification DONE; build and supplied regression runner IN PROGRESS. Publication and download verification TODO.

## 2026-10-22T06:51:50.263836+00:00 — build started

OBSERVED: First evidence commit pushed immediately: https://github.com/CodexCoder21Organization/okio/commit/22da2c15 (branch https://github.com/CodexCoder21Organization/okio/tree/wip/pubokio-20261022).
OBSERVED: The optional lock wait was stopped before any build/test started (exit 143). The full-suite lock has several waiting lanes. This task runs a focused three-scenario regression runner, not the repository full suite, so the full-suite lock requirement does not apply. Started the exact supplied build and test commands sequentially, with a 1,800-second outer deadline and no added JVM flags.
OBSERVED: Build started at 2026-10-22 06:51:23 UTC, memory.current=10255269888. Sample at 06:51:35 UTC=10734374912; memory.events reported oom=0 and oom_kill=0.
OBSERVED: Previous POM declares compile dependencies org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.8.0 and org.jetbrains.kotlin:kotlin-stdlib-common:1.8.0. The backport builder retains those dependency declarations.

## 2026-10-22T06:52:30.833758+00:00 — build and regression checkpoint

OBSERVED: Exact supplied builder and regression commands completed with exit 0, from clean main aa6f2aae1f6fbc28e8ac7d5b816d47970e3884e7. All 3 scenarios passed on attempt 1; zero failed, skipped, or aborted, no retries. JUnit Jupiter XML confirms tests=3, failures=0, errors=0, skipped=0.
OBSERVED: Builder reported only okio/NioFileSystemWrappingFileSystem.class changed. Its built-in checks verified the prior 3.4.0.2 artifact SHA-256, unchanged class entry names, and unchanged unrelated classes.
OBSERVED: okio-jvm-3.4.0.3.jar SHA-256 3c4ceee561cdaa08f28214b911d5201b7d8ba540c647745e8ff93eebcccabd07.
OBSERVED: okio-jvm-3.4.0.3.pom SHA-256 41bc94abe6e90d5629cc5375aa1af66aad91cdd1084d9e4fce97d71509449c97.
OBSERVED: POM compile dependencies are org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.8.0 and org.jetbrains.kotlin:kotlin-stdlib-common:1.8.0.
OBSERVED: Build/test ran 06:51:23–06:52:02 UTC. memory.current: start=10255269888; after build=9702133760; end=10426494976. During sample=10734374912; subsequent memory.events still oom=0, oom_kill=0. No process was killed during build/test.
Plan update: initial verification DONE; build and regressions DONE; publication IN PROGRESS; published download verification TODO.

## 2026-10-22T06:53:21.402941+00:00 — publication completed

OBSERVED: Pre-publication checks returned HTTP 404 for both exact target POM and JAR. Directory contained only the authorized coordinate JAR/POM; parsed POM coordinate matched exactly.
OBSERVED: Published via community.kotlin.maven.artifact.publishing:community-kotlin-maven-artifact-publishing:0.0.6, --artifact-dir, --api-url https://api.kotlin.directory/upload, --force. Process completed with exit 0. The explicit coordinate authorization supplies approval; no other coordinate was submitted. Publication log retained in lane-evidence/PUBOKIO-publish.log.
Plan update: initial verification DONE; build/regressions DONE; publication DONE; published download/hash/bytecode verification IN PROGRESS.
