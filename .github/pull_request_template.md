## Summary

<!-- What changed and why. -->

## Traceability

- Stories: <!-- e.g. LD-002 -->
- QA cases: <!-- e.g. QA-0009..QA-0016 -->
- Refined acceptance criteria: `docs/delivery/PHASE-1-ACCEPTANCE.md` <!-- or the relevant phase file -->

## TDD evidence

| Step | Evidence file | Result |
|------|---------------|--------|
| RED | `docs/evidence/<story>/01-red.log` | |
| GREEN | `docs/evidence/<story>/..-green.log` | |
| REFACTOR / regression | `docs/evidence/<story>/..-refactor.log` | |

## Test results at head SHA

<!-- Exact commands run and passed / failed / skipped counts. Skipped is never PASS. -->

```
$ command
```

## Risk, rollback and known defects

<!-- Migrations, deploy impact, how to roll back, open defects. -->

## Checklist

- [ ] Branch and commit messages pass `tools/repo_policy.py`
- [ ] Tests were written before the implementation and the RED output is recorded
- [ ] No secrets, `.env` files or credentials committed
- [ ] No change to Matbakhy services, data or the shared reverse proxy without recorded owner approval
- [ ] Independent QA review requested; the author will not self-approve or merge
