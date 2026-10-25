# CI duration comparison: GitHub-hosted vs the Contabo self-hosted runner

Source: GitHub Actions API (`actions/runs/{id}/jobs`, started_at to completed_at of each successful job). Hosted runs are
the last successful ones before the billing hold (2026-10-24 17:46-17:55Z); self-hosted runs are the two green runs of
PR #50 on the Contabo runner (2026-10-25). Same jobs, same commands (the self-hosted variant only adds clean-up steps).

| Job (minutes) | Hosted `0db2bef` PR | Hosted `42fb564` PR | Hosted `19ad538` push | Contabo `f8e8cb3` PR | Contabo `5d0cf8f` PR |
|---|---:|---:|---:|---:|---:|
| Repository policy | 1.1 | 1.0 | 0.9 | 2.3 | 2.5 |
| Backend (embedded PostgreSQL) | 3.2 | 2.6 | 2.5 | 5.0 | 5.4 |
| Backend against postgres:18.6 image | 3.6 | 3.5 | 3.0 | 5.9 | 6.1 |
| Frontend (Node 22) | 2.3 | 2.2 | 1.4 | 2.9 | 2.6 |
| Staging stack (images + compose smoke) | 2.9 | 2.8 | 2.7 | 6.3 | 3.3 |
| Secret scan | 0.2 | 0.2 | 0.2 | 0.3 | 0.3 |
| Develop guard / Deploy (push only) | n/a | n/a | 0.1 / 2.0 | skipped | skipped |
| **Sum of job minutes** | 13.2 | 12.3 | 12.8 | 22.7 | 20.2 |
| **Billed minutes** (each job rounded up) | 17 | 15 | 16 | n/a | n/a |
| **Wall-clock of the run** | **4.2** | **4.5** | **5.0** | **22.9** | **20.3** |

Reading: jobs run concurrently on hosted runners (the run takes about as long as its slowest job plus start-up), and one
after another on the single Contabo runner (the run takes about the sum). The same job is also 1.5 to 2 times slower on
the shared host (CPU quota 4 cores for the runner, other tenants, load average 3 to 8). The one fast Contabo stack run
(3.3 min) had warm image layers; the first (6.3 min) was cold.

First hosted job executed after the plan upgrade: attempt 2 of run 40120417395, job "Secret scan", runner
"GitHub Actions 1000007560", success (2026-10-25 08:08Z). Before, every hosted job of that run was refused.
