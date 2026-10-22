# Handoff: Restore allowed actions for the Okio native checks

Written 2026-10-22 at 03:08 UTC.

**RE-VERIFY: This is a write-time snapshot. Inspect https://github.com/CodexCoder21Organization/okio/pull/7 and https://github.com/CodexCoder21Organization/okio/actions/runs/39712980273 before acting. Check the live run conclusion, jobs, workflow and repository Actions rules.**

## Design decisions in force

The C2 owner stated on 2026-10-22: "You are not allowed to merge, enqueue, mark ready, close PRs, publish artifacts, deploy, restart services, rerun CI, or delete/rerequest buildtest runs." The C2 lane changes directory-creation source/tests; it does not change unrelated workflow configuration. The owner requires green required Actions, so this external configuration issue must be resolved under separate authorization.

## Mission and evidence

The C2 upstream directory correction is reviewed and locally verified at 9ff82726b0d4c0e0b2b0a2100187f2faa26445fc. Native full suite: 5,017 passed, 0 failed/errors, 104 existing platform skips. Three new deterministic public-API cases each passed 5/5, and all 3 fail on the old artifact. PR https://github.com/CodexCoder21Organization/okio/pull/7 is regular/open.

The initial native build run https://github.com/CodexCoder21Organization/okio/actions/runs/39712980273 is completed/startup_failure before any jobs. The run page states that reactivecircus/android-emulator-runner@a421e43855164a8197daf9d8d40fe71c6996bb0d and jamesives/github-pages-deploy-action@fa24774553152dd7873cd16ebd8d959b010c5445 are not allowed because actions must be enterprise-owned, GitHub-created or Marketplace-verified. These are existing workflow actions. Current repository settings permit GitHub-created and verified actions, with no selected patterns. All pinned repository commits resolve; invalid references were ruled out. The native run cannot turn green by further waiting because it is already terminal.

A read-only build-watchman reported missing check runs rather than this native startup result; direct run API and rendered run page established the cause. No workflow/rule edit, receiver warmup, CI rerun, rerequest or status bypass was done. The local watcher was stopped. This records an ordinary CI configuration blocker, not a test failure.

## Relevant PRs and refs

| Repo | Branch | Remote head | PR | State |
|---|---|---|---|---|
| Okio | https://github.com/CodexCoder21Organization/okio/tree/tests/r1-C2-okio-directory |9ff82726b0d4c0e0b2b0a2100187f2faa26445fc|https://github.com/CodexCoder21Organization/okio/pull/7|Local suite passes; native Actions startup blocked|
| Okio | https://github.com/CodexCoder21Organization/okio/tree/wip/r1-C2-okio-fixture |9ff82726b0d4c0e0b2b0a2100187f2faa26445fc|No PR|Same recoverable source/test checkpoint|
| Okio evidence | https://github.com/CodexCoder21Organization/okio/tree/wip/r1-C2-evidence-2026-10-22 |2358085cbf0c8c8cc67dde4b82e9e4c33cc8ec34|No PR|40 text/XML proof records; no binaries|

No production deployment, publication, service restart or PR merge was performed by C2. The proposed compatible Okio 3.4.0.3 artifact is local only and separately blocks the downstream SFS dependency.

## Next steps

1. Re-verify the run and PR state first; a fresh result takes precedence.
2. Obtain owner direction for the existing workflow/action rule mismatch. Restore an allowed workflow/rule combination under that authorization and arrange fresh native required checks. C2's original brief does not authorize those mutations or reruns.
3. Inspect actual required check results after that change. Do not skip tests or bypass required results; do not merge without explicit user instruction.
4. Carry the final check state back to the C2 workstream record. Its other consumer gates proceed independently while this configuration is resolved.

## Recoverable proof

https://github.com/CodexCoder21Organization/okio/tree/wip/r1-C2-evidence-2026-10-22/handoffs/artifacts/c2-2026-10-22 contains the findings/reviews, negative controls, all final targeted reports and full summaries. `r1-C2.md` records the exact run diagnosis, settings, constraints and local watcher cleanup. No build output/cache/credential value is committed.
