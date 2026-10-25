#!/usr/bin/env bash
# Moves the staging deployment secrets from repository level to the `staging` GitHub Environment, restricted to the
# `develop` branch (docs/operations/CI-RUNNER.md, STAGING-RUNBOOK §8). RUN BY THE OWNER, on a workstation that holds the
# CI deployment key; no agent runs it, reads the key or sees a secret.
#
#   migrate-staging-secrets.sh --ssh-key-file KEY --known-hosts-file HOSTS             show the plan, change nothing
#   migrate-staging-secrets.sh --ssh-key-file KEY --known-hosts-file HOSTS --apply     restrict the environment, store
#   ... --verify                                                                         redeploy the develop head
#   ... --remove-repo-secrets                                                            delete the repository copies
#
# Order matters and is enforced: the environment is restricted to develop BEFORE a secret is stored in it, and the
# repository-level copies are deleted only after a deployment that ran after the environment secrets were stored
# succeeded, so the key stays usable at every step. Secrets travel from the files to GitHub on stdin only: never as an
# argument, never printed. Rollback: `gh secret set NAME --repo REPO < FILE` restores a repository-level copy.
# Needs: gh (authenticated as a repository admin), jq, ssh-keygen.
set -euo pipefail

repo=afawzy70/Lawdesk-v1
environment=staging
key_file=
hosts_file=
action=plan

while [ $# -gt 0 ]; do
    case $1 in
        --ssh-key-file) key_file=${2:?--ssh-key-file needs a path}; shift 2 ;;
        --known-hosts-file) hosts_file=${2:?--known-hosts-file needs a path}; shift 2 ;;
        --repo) repo=${2:?--repo needs owner/name}; shift 2 ;;
        --plan | --apply | --verify | --remove-repo-secrets) action=${1#--}; shift ;;
        *) echo "unknown argument: $1 (see the header of this script)" >&2; exit 2 ;;
    esac
done

die() { echo "$*" >&2; exit 2; }

private_file() {
    local path=$1 label=$2 mode
    [ -f "$path" ] || die "$label: $path is not a file"
    [ "$(stat -c %u "$path")" = "$(id -u)" ] || die "$label: $path is not owned by you"
    mode=$(stat -c %a "$path")
    if [ $((8#$mode & 8#077)) -ne 0 ]; then
        die "$label: $path has mode $mode; it must not be readable by others (chmod 600 $path)"
    fi
}

[ -n "$key_file" ] && [ -n "$hosts_file" ] || die "both --ssh-key-file and --known-hosts-file are required"
gh auth status > /dev/null 2>&1 || die "gh is not authenticated; run: gh auth login"
private_file "$key_file" "key file"
private_file "$hosts_file" "known-hosts file"
# An unencrypted private key that ssh can read; nothing is printed.
ssh-keygen -y -P "" -f "$key_file" > /dev/null 2>&1 || die "$key_file is not a usable unencrypted private key"
target=$(gh variable get LAWDESK_STAGING_SSH_TARGET --repo "$repo")
host=${target#*@}
ssh-keygen -F "$host" -f "$hosts_file" > /dev/null 2>&1 || die "$hosts_file has no entry for the deployment host $host"

policies="repos/$repo/environments/$environment/deployment-branch-policies"

latest_deploy_run() {
    gh run list --repo "$repo" --workflow deploy-staging.yml --branch develop --limit 5 --json databaseId,createdAt,conclusion
}

case $action in
    plan)
        echo "would restrict the $environment environment to develop (custom branch policy: develop only)"
        echo "would store LAWDESK_STAGING_SSH_KEY in the $environment environment from $key_file"
        echo "would store LAWDESK_STAGING_KNOWN_HOSTS in the $environment environment from $hosts_file"
        echo "next: --apply, then --verify, then --remove-repo-secrets, then --verify again"
        ;;
    apply)
        gh api -X PUT "repos/$repo/environments/$environment" \
            -F 'deployment_branch_policy[protected_branches]=false' \
            -F 'deployment_branch_policy[custom_branch_policies]=true' > /dev/null
        allowed=$(gh api "$policies" --jq '.branch_policies[].name' || true)
        others=$(grep -vx develop <<< "$allowed" | tr '\n' ' ' || true)
        [ -z "${others// /}" ] || die "the $environment environment also allows: $others; remove those policies first"
        if ! grep -qx develop <<< "$allowed"; then
            gh api -X POST "$policies" -f name=develop -f type=branch > /dev/null
        fi
        echo "ok   the $environment environment is restricted to develop"
        gh secret set LAWDESK_STAGING_SSH_KEY --env "$environment" --repo "$repo" < "$key_file"
        gh secret set LAWDESK_STAGING_KNOWN_HOSTS --env "$environment" --repo "$repo" < "$hosts_file"
        echo "ok   both secrets are stored in the $environment environment (values not shown)"
        echo "next: --verify (a normal redeploy of the current develop head)"
        ;;
    verify)
        sha=$(gh api "repos/$repo/commits/develop" --jq .sha)
        before=$(latest_deploy_run | jq '[.[].databaseId] | max // 0')
        gh workflow run deploy-staging.yml --repo "$repo" --ref develop -f sha="$sha"
        echo "dispatched a redeploy of develop $sha; waiting for its run"
        run=0
        for _ in $(seq 1 30); do
            run=$(latest_deploy_run | jq '[.[].databaseId] | max // 0')
            [ "$run" -gt "$before" ] && break
            sleep 5
        done
        [ "$run" -gt "$before" ] || die "the dispatched run did not appear; check the Actions tab"
        gh run watch "$run" --repo "$repo" --exit-status || die "the deployment run $run failed; keep the repository secrets"
        echo "ok   run $run succeeded with the secrets of the $environment environment"
        ;;
    remove-repo-secrets)
        updated=$(gh api "repos/$repo/environments/$environment/secrets/LAWDESK_STAGING_SSH_KEY" --jq .updated_at)
        good=$(latest_deploy_run | jq --arg since "$updated" '[.[] | select(.conclusion == "success" and .createdAt > $since)] | length')
        [ "$good" -gt 0 ] || die "no successful deployment ran after the environment secrets were stored ($updated); run --verify first"
        gh secret delete LAWDESK_STAGING_SSH_KEY --repo "$repo"
        gh secret delete LAWDESK_STAGING_KNOWN_HOSTS --repo "$repo"
        echo "ok   the repository-level copies are deleted; run --verify once more to prove the environment copies suffice"
        ;;
esac
