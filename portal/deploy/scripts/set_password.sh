#!/usr/bin/env bash
# Sets (or rotates) the portal's basic-auth password WITHOUT it ever appearing on a command line, in shell history or
# in a log: reads it silently from the terminal (or generates one with --generate and writes it to a 0600 file), hashes
# it with bcrypt and writes the hash file that attach.sh reads. Then re-run attach.sh to apply.
#   set_password.sh <hash file> [--generate <password file>]
# Needs python3 with the bcrypt module, or a running Caddy proxy container for `caddy hash-password`.
set -euo pipefail
umask 077
hashfile=${1:?hash file to write (mode 0600)}
mode=${2:-}
if [[ $mode == --generate ]]; then
    pwfile=${3:?password file to write}
    python3 -c 'import secrets; print(secrets.token_urlsafe(24))' > "$pwfile"
    chmod 600 "$pwfile"
    echo "password written to $pwfile (mode 600); read it yourself, it is not printed"
    password=$(cat "$pwfile")
else
    read -r -s -p "New portal password (min 12 chars): " password
    echo
    read -r -s -p "Repeat: " again
    echo
    [[ $password == "$again" ]] || { echo "passwords differ" >&2; exit 1; }
fi
[[ ${#password} -ge 12 ]] || { echo "password must have at least 12 characters" >&2; exit 1; }
if python3 -c 'import bcrypt' 2> /dev/null; then
    PW=$password python3 -c 'import bcrypt,os; print(bcrypt.hashpw(os.environ["PW"].encode(), bcrypt.gensalt(12)).decode())' > "$hashfile"
else
    proxy=$(docker ps --filter label=com.docker.compose.service=proxy --format '{{.ID}}' | head -1)
    printf '%s\n' "$password" | docker exec -i "$proxy" caddy hash-password > "$hashfile"
fi
chmod 600 "$hashfile"
unset password again
echo "hash written to $hashfile (mode 600)"
