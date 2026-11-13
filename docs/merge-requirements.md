# Required checks before merging

The workflow defines `Merge gate`, an always-running aggregate check. It fails
unless both the platform validation matrix and `Performance standards` succeed;
failed, cancelled, or skipped prerequisites cannot turn it green. There are no
path exclusions. Existing Windows/Linux build coverage is retained, but the
measured performance policy covers Android, iOS, and macOS only.

**Repository settings still need administrator activation.** A workflow alone
does not prevent merging. During setup the connected account had read access
only, and the repository's ruleset list was empty. The branch-protection endpoint
was inaccessible; that does not establish whether legacy protection exists.

## Solo-developer workflow

Open a PR, run the required checks, inspect the benchmark evidence, and merge
your own PR once `Merge gate` succeeds. No second developer, approving review,
code-owner approval, or last-push approval is required. CODEOWNERS documents
ownership only. PRs, up-to-date branches, resolved review conversations, and
passing checks remain the default. Repository administrators have an explicit
PR-only emergency bypass; direct pushes and force pushes remain restricted.
Agent reviews can help assess changes, but do not replace measurements or act
as a required human approval.

## Emergency override

As repository administrator, you can deliberately bypass this ruleset to merge
a PR even when required checks are failing or missing. The bypass uses
`RepositoryRole` admin (`actor_id: 5`) with `bypass_mode: pull_request`. It
applies to this entire ruleset, not only the performance check. It does not
turn failed CI results green or grant a direct-push/force-push bypass. Anyone
you later grant repository admin access will also have this capability.

Use the bypass option in GitHub’s PR merge controls when needed. Record why
you are overriding, which checks remain unresolved, and any follow-up in the PR
so the exception remains understandable. This explanation is a working practice,
not a machine-enforced requirement. No second reviewer is required.

Other rulesets or legacy branch protection can still block the merge; this
bypass does not override those rules. See GitHub’s
[ruleset bypass API documentation](https://docs.github.com/en/rest/repos/rules)
and the official provider’s
[repository role mapping](https://github.com/integrations/terraform-provider-github/blob/main/docs/resources/repository_ruleset.md).

## What runs

- Existing JVM application/build-plugin tests, desktop builds and packaging,
  Android build/tests, and iOS simulator build, plus shared iOS simulator tests.
- Python performance-tool tests, including malformed/incomplete evidence and
  regression rejection tests.
- A deterministic JVM workload test for 3,650 events across all 12 month grids,
  bounded previews and omission accounting. Loading/refresh in this test models
  the fixture delivery policy; it does not test the production loading state or
  measure execution time.
- On PRs and merge groups, source-bound, reviewed hardware performance evidence.
  Post-merge pushes run the test suite but do not revalidate historical evidence
  against a different base commit. Direct pushes must be prohibited by rules.

## Measured regression policy

Check in `performance-evidence/merge.json`. Missing evidence fails. It must contain
at least five complete before/after runs for each of alternating month navigation,
forward navigation, and loading/refresh on each of Android, iOS, and macOS.
Use the 2026 fixture with 3,650 events, identical device/configuration per pair,
release binaries, controlled thermal state, and alternating before/after order.
Retain all predeclared runs and raw artifacts; do not select only favorable runs.

The checker evaluates each platform/scenario independently:

| Metric | Maximum permitted regression |
|---|---:|
| Median of per-run p95 frame times | 5% |
| Median of per-run p99 frame times | 5% |
| Median of per-run maximum frame times | 5% |
| Median startup time | 5% |
| Median memory usage | 2% |
| Median late-frame rate | +0.5 percentage points |
| Frames taking at least 40 ms | None newly introduced when every baseline run had none |

These are initial **regression limits**, not a 120 FPS certification. A slow
baseline can pass a relative comparison. The separate release-readiness goal
still requires actual sustained 120 Hz deadline measurements and a suitable
absolute jank budget. Callback intervals, simulator timing, agent ratings, and
old committed reports are not substitutes for hardware frame evidence.

The current macOS callback diagnostics and iOS test timings do not qualify.
Until actual frame evidence exists on all three platforms, this strict gate will
remain red. Hosted CI validates evidence and runs deterministic tests; it does
not run the physical-device suite. Dedicated benchmark runners and automated
trace-to-report ingestion are future work.

## Binding evidence to source

Benchmark clean, committed base and candidate checkouts. Obtain their digests:

```sh
python3 scripts/performance/source_digest.py BASE_COMMIT
python3 scripts/performance/source_digest.py CANDIDATE_COMMIT
```

The digest includes every tracked path, file mode and blob identity except
`performance-evidence/merge.json`. This permits a subsequent evidence-only commit
without the impossible requirement that a file contain its own commit SHA.
Code, tests, workflows, benchmark scripts, dependencies and configuration all
invalidate the digest. Uncommitted changes are not included: never measure a
dirty checkout for merge evidence. Store the clean commit IDs, fixture/binary
hashes, environment, run order and raw traces in the linked artifact bundles.

The version-1 report contains `base_source_sha256`, `head_source_sha256`,
`metric_kind: "presented_frames"`, `fixture: {"year": 2026, "events": 3650}`,
and `platforms` keyed by `android`, `ios`, `macos`. Each platform has `device`,
`config`, `artifacts` (`before_url`, `after_url`), and `scenarios` keyed by
`alternating`, `forward`, `loading_refresh`. Each scenario contains `before`
and `after` run arrays. The checker and its test fixture define the exact run
schema and reject unknown fields. Every run requires a unique `run_id`,
`valid_complete: true`, `p95_ms`, `p99_ms`, `late_frame_pct`, `max_ms`,
`startup_ms`, `memory_bytes`, and `stalls_ge_40ms`. Before and after sample counts
must match. Metric definitions and measurement windows must be identical between
the two sides; in particular, startup is measured separately from the swipe trace
and memory must include the same categories on both sides. Use anonymous device labels, not private
serials or credentials.

```sh
python3 scripts/performance/check_merge_evidence.py \
  --report performance-evidence/merge.json \
  --base-source-sha256 BASE_DIGEST --head-source-sha256 CANDIDATE_DIGEST
```

CI compares these against the PR's current base and head and verifies that the
tested merge tree has the same source digest as the measured head. Update and
remeasure when the base changes. Multi-PR merge queues usually invalidate a
report's source scope; keep the queue to one PR and a current base, or provide
fresh evidence for the combined tree. No skip exemption is provided.

**Evidence is self-reviewed, not attested.** HTTPS links and JSON fields alone
cannot prove that measurements came from the stated binaries or that the device
conditions matched. As the solo maintainer, inspect artifact contents, all runs,
metric extraction, configuration and binary/source provenance before merging.
This is a manual responsibility, not an independently enforced review. The
validator does not fetch or cryptographically authenticate artifact contents.

## Activate the restrictions

1. Publish the workflow and tooling, then let the checks register on GitHub.
2. With repository administrator access, inspect existing protection and import
   `scripts/ci/merge-ruleset.json` as a new ruleset. It targets `main`, requires
   `Merge gate` from the GitHub Actions app, requires an up-to-date branch and
   zero approving reviews. It blocks force pushes and deletion, with a PR-only
   administrator bypass for emergency merges. Preserve existing test/security
   protections, but remove any
   overlapping rule requiring another reviewer: rulesets combine, so such a
   rule would still block your own PRs.

   ```sh
   gh api --method POST repos/Debanshu777/XCalendar/rulesets \
     --input scripts/ci/merge-ruleset.json
   ```

3. Confirm a deliberately failing test, missing report, stale digest, and
   out-of-budget report each block normal merging on a disposable PR. Verify
   that the administrator can explicitly bypass on that PR, direct pushes
   remain blocked, and your own passing PR can merge without another reviewer.
   Do not claim enforcement until
   this GitHub-side check is complete.

GitHub permits skipped jobs to satisfy ordinary required checks, which is why
the aggregate explicitly requires success from every prerequisite. See the
[required-check documentation](https://docs.github.com/en/pull-requests/how-tos/merge-and-close-pull-requests/troubleshooting-required-status-checks).
Repository administrators can still edit/delete rules; this protects the normal
merge path, not against a malicious administrator.
