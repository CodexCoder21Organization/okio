
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
