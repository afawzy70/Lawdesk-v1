# Handoff for the UAT QA administrator account

Observed on deployed `3522775`: `qa.admin` login 401; manager's read-only user query says enabled, unlocked, `mustChangePassword=true`, but roles `LEGAL_STAFF` and a QA view role. The other three QA account files authenticate. No account was changed by QA.

The script `deploy/qa/qa_accounts.py` exists in this isolated QA worktree and its `provision`, `receive`, `verify` arguments and pipe safeguards were read. `origin/develop` commit `31e9bef` added the exact operator procedure in `docs/operations/QA-ACCOUNTS.md` after the tested UAT SHA. The command below uses the QA worktree copy, so it does not read or change Claude's workspace. It has **not been executed by QA** because it needs the UAT administrator secret and would rotate all four QA passwords, disrupting concurrent runs. The operator must coordinate a quiet QA window.

Run as host user `devops` from any working directory. It copies the script into an owner-only temporary directory readable by `devops`, prompts for the UAT administrator password without echo, creates or repairs all four QA roles/accounts, rotates **all four** passwords, completes forced first change, and pipes the credential bundle to `receive` as `aiagent` in the existing 0700 directory. It does not modify the administrator's account or ordinary users. No password is an argument or printed.

```bash
set -euo pipefail
sudo -v
umask 077
qa_tmp=$(mktemp -d "$HOME/qa-run.XXXXXX")
trap 'rm -f "$qa_tmp/qa_accounts.py"; rmdir "$qa_tmp"' EXIT
sudo -u aiagent cat /tmp/lawdesk-qa-comprehensive-20261110/deploy/qa/qa_accounts.py > "$qa_tmp/qa_accounts.py"
python3 "$qa_tmp/qa_accounts.py" provision --base-url https://lawdesk.167-86-90-174.sslip.io \
  | sudo -u aiagent python3 /tmp/lawdesk-qa-comprehensive-20261110/deploy/qa/qa_accounts.py receive \
      --dir /srv/ai-workspaces/secrets/lawdesk-qa
sudo -u aiagent python3 /tmp/lawdesk-qa-comprehensive-20261110/deploy/qa/qa_accounts.py verify \
  --dir /srv/ai-workspaces/secrets/lawdesk-qa --base-url https://lawdesk.167-86-90-174.sslip.io
```

The source path and script subcommands were verified in the QA checkout; host `sudo -u aiagent` permission and the real UAT transaction were **not** verified by QA. Stop and escalate to the account operator if sudo is denied; do not fall back to logging credentials. After a successful handoff, rerun S15, admin UI, all seven roles and direct API scope/secret/grant/revocation checks against one release SHA. If the QA worktree has been removed, use the `docs/operations/QA-ACCOUNTS.md` path from the current repository checkout and verify that exact script before running.
