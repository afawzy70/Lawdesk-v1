# QA accounts on UAT — exact procedure

Tool: `deploy/qa/qa_accounts.py` (Python 3 standard library only).

## What each sub-command does

| Command | Who runs it | Effect |
|---|---|---|
| `provision --base-url URL [--admin-user NAME] [--admin-password-file FILE]` | the operator who holds the UAT administrator password (host user `devops`) | Signs in as the UAT administrator (prompts for the password without echo, or reads `FILE`). For **all four** QA accounts `qa.admin` (ADMIN), `qa.manager` (LEGAL_MANAGER), `qa.staff` (LEGAL_STAFF), `qa.viewer` (read-only QA role, created with the role if absent): **creates the user if missing, otherwise re-applies its role, enables it and rotates its password**; completes the forced first-password change; checks each role; writes the credential bundle **only to stdout, which must be a pipe**. It refuses a terminal or a file (exit 2). The administrator's own account and every real user are never touched. |
| `receive --dir DIR` | the QA execution identity (host user `aiagent`) | Reads the bundle from stdin and stores one owner-only (0600) JSON file per account in `DIR` (must be owned by the caller, mode 0700), replacing atomically. Prints file paths only. |
| `verify --dir DIR --base-url URL` | `aiagent` | Signs in with the stored files and prints outcomes only. |

So the operation **creates missing accounts and rotates the passwords of all four**. Rotation is not partial.

## Why the previous one-line command was wrong

It contained a placeholder instead of a command. The real constraints (checked on the host on 2026-11-10):

* `devops` (uid 1001, groups sudo, users, docker) cannot read `/home/aiagent` (mode 0750) or `/srv/ai-workspaces` (0750), so it cannot run the script from the repository checkout.
* The credential directory `/srv/ai-workspaces/secrets/lawdesk-qa` is `aiagent`-owned, mode 0700, so `receive` must run as `aiagent`.
* The script itself checks that stdout is a pipe and `receive` checks the directory owner and mode.

## Coordination (do this first)

Rotation invalidates the passwords in the files that Codex's QA runs read. Run it when Codex has no QA run in progress, or tell Codex before; new runs read the files afresh after `receive` replaces them. Current state on 2026-11-10 (verified with `verify`): `qa.manager`, `qa.staff`, `qa.viewer` sign in; `qa.admin` is refused.

## Command (run by `devops` on the Contabo host)

Working directory: any (the commands below use absolute paths). Needs: `devops` sudo rights for `sudo -u aiagent`, and the UAT administrator password typed at the prompt (never put it in an argument).

```bash
sudo -v                                   # cache the sudo password first, so the two prompts do not interleave
umask 077
mkdir -p "$HOME/qa-run"
sudo -u aiagent cat /home/aiagent/workspaces/lawdesk-wp1/deploy/qa/qa_accounts.py > "$HOME/qa-run/qa_accounts.py"

python3 "$HOME/qa-run/qa_accounts.py" provision --base-url https://lawdesk.167-86-90-174.sslip.io \
  | sudo -u aiagent python3 /home/aiagent/workspaces/lawdesk-wp1/deploy/qa/qa_accounts.py receive \
      --dir /srv/ai-workspaces/secrets/lawdesk-qa

rm -rf "$HOME/qa-run"
```

Expected: stderr lines such as `qa.admin: existing account reset to role ADMIN and rotated` (or `created with role`), then four `LAWDESK_QA_*_CREDENTIAL_FILE=<path>` lines from `receive`. No password appears on the terminal. If the pipe fails in the middle, `receive` stores nothing (it validates the whole bundle first).

Then, as `aiagent`:

```bash
python3 /home/aiagent/workspaces/lawdesk-wp1/deploy/qa/qa_accounts.py verify \
  --dir /srv/ai-workspaces/secrets/lawdesk-qa --base-url https://lawdesk.167-86-90-174.sslip.io
```

and re-run scenario S15 in full (roles screen, direct API denial, scopes, confidentiality, grants, revocation) using `qa.admin`.

## What was and was not tested

* Tested by the developer without touching UAT: `provision` refuses a terminal/file stdout (exit 2); with an unreachable base URL it exits 3 without producing output and `receive` then refuses the empty input and stores nothing (`python3 … provision --base-url http://127.0.0.1:1 … | … receive` composition, exit codes `3 2`).
* **Not** tested: the real run against UAT (needs the administrator password), and the `sudo -u aiagent` forms (the developer's account has no sudo rights). If `sudo -u aiagent` is not permitted for `devops`, run the same `receive` as `aiagent` through a named pipe: `aiagent` creates `mkfifo -m 0620` a FIFO in a directory both can reach, runs `receive` reading it, and `devops` runs `provision … > FIFO` (stdout is then a FIFO, which the script accepts).
