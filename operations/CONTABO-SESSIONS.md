# Contabo isolated worker sessions

Do not touch Matbakhy containers, secrets or checkout.

```bash
ssh aiagent@YOUR_CONTABO_HOST
mkdir -p ~/workspaces
cd ~/workspaces
gh repo clone afawzy70/Lawdesk-v1 lawdesk-claude
gh repo clone afawzy70/Lawdesk-v1 lawdesk-codex
cd lawdesk-claude && git fetch origin && git checkout -b develop origin/develop
```

If `develop` has not been created, create it once only after the baseline PR is approved. Use separate SSH shells:

```bash
tmux new -s lawdesk-claude
cd ~/workspaces/lawdesk-claude
claude
```

```bash
tmux new -s lawdesk-codex
cd ~/workspaces/lawdesk-codex
codex
```

Detach: Ctrl+B then D. Reattach: `tmux attach -t lawdesk-claude` or `-t lawdesk-codex`.

Never share one writable worktree between Claude and Codex. Codex must not modify Claude's files while reviewing; QA test changes use separate PRs. Docker reverse proxy and HTTPS host configuration must be inspected before deployment. Treat scripts as setup guidance, not proof that deployment is ready.
