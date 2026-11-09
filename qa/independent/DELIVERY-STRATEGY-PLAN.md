# QA application of owner-approved delivery strategy

Authority: `docs/governance/LAWDESK-DELIVERY-STRATEGY.md` at develop
`19ad5386fa586e2469ecad36a677632273712f44` (PR46), read 2026-10-24. Phase7 remains deferred.
No new orchestration infrastructure, deployment, feature-worktree mutation or shared-host stress is introduced.

## Current execution rules

- Track IMPLEMENTED, INTEGRATED, DEPLOYED, QA ACCEPTED and PRODUCTION READY separately.
- Reuse independently executed same-SHA evidence when code, data assumptions and environment remain applicable;
  never call it newly executed, add its count twice, or transfer full acceptance to a changed SHA.
- Identify new heads before review. Test changed code plus affected integrations; preserve superseded evidence.
- QAD-032's API fix and QAD-033's separate database-writer gap have different dispositions. Do not ask Claude to
  repeat the verified API fix. The owner-required database invariant remains an affected-functionality blocker.
- Expert referrals do not exist in PR33 itself. Attribute their tests to the expert or cumulative implementation,
  and leave the PR33 expert integration case BLOCKED rather than assigning another head's PASS to it.
- Preserve QAD-001…006 baseline findings and every unresolved test/acceptance ambiguity.
- Publish exact SHA, independent counts, reproduction, dependencies and scoped recommendation to QA PR6.

## Security work

Continue focused source review and independently executed authorization/CSRF/session/upload/export/integrity
probes on isolated data. The expert lookup change uses bound UUIDs, a 10000 input cap and a four-field projection;
future report controllers still require authorization testing. A successful internal-port test does not prove the
future HTTP endpoint's security. Run secret scanning before evidence publication.

Record frontend dependency advisories separately from backend dependency coverage. The current lightweight pass
includes npm audit and manual changed-code review. Automated full SAST and backend SCA are NOT_RUN in that pass;
absence of installed tools or a green frontend audit is not a clean-security verdict. Full DAST belongs to the later
approved assessment, with scoped safe DAST continuing now against synthetic isolated targets only.

## Performance work

`phase56_priority.py --mode performance` establishes a small diagnostic baseline on the exact local PR33 jar:
20 synthetic cases/proceedings/hearings, one client, 2 warmups plus30 measured reads per endpoint, at least100ms
spacing. Capture nearest-rank p50/p95/p99, deliberately paced throughput, HTTP error rate, JVM CPU/RSS samples,
DB connection snapshots and `EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON)` for the hearing list query shape.

This is not saturation, load/stress, container benchmarking or performance acceptance. Thirty samples give unstable
tails, the dataset is small, host contention is uncontrolled, and no latency SLO has been approved. DB CPU,
container/cgroup accounting and representative multi-user workloads remain uncovered. Preserve these limitations
alongside every result. Resource-bounded isolated load tests follow completion of Phases5/6; stress/spike/soak and
final UI performance follow the later approved phases. Never apply them to shared public Contabo UAT.

## Follow-up scope

Next integration checks remain the Phase5-to-Phase6 hearing/expert/workload reports, real deployment-container PDF,
newly deployed functionality at its authenticated `/api/v1/release` SHA, and regressions for actual remediation.
A publication CI job that never starts because of a GitHub account restriction is operational BLOCKED/NOT_RUN,
not a failed product assertion and not an excuse to bypass a required gate. Production-only checks do not
independently block ordinary synthetic Dev/UAT iteration.
