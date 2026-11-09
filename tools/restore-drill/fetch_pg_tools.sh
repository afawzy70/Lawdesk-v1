#!/usr/bin/env bash
# Fetches the PostgreSQL 18.6 binaries the restore drill needs, WITHOUT root and without touching the host's PostgreSQL:
#   server  (initdb, pg_ctl, postgres)       from the same artefact the backend tests use (io.zonky.test.postgres 18.6.0)
#   client  (psql, pg_dump, pg_restore)      from the PGDG package postgresql-client-18 (+ libpq5), unpacked with dpkg -x
# Every download is checked against a pinned SHA-256; a mismatch stops the script. Usage: fetch_pg_tools.sh <target-dir>
# Prints the two directories and the library paths to pass to restore_drill.py.
set -euo pipefail

target=${1:?usage: fetch_pg_tools.sh <target-dir>}
mkdir -p "$target"
cd "$target"

zonky_jar=embedded-postgres-binaries-linux-amd64-18.6.0.jar
zonky_sha=008ee189eaa7ca2b58bd01dd950291e638b998392cf982297acd4779ec898ac1
zonky_url=https://repo1.maven.org/maven2/io/zonky/test/postgres/embedded-postgres-binaries-linux-amd64/18.6.0/$zonky_jar
pgdg=https://apt.postgresql.org/pub/repos/apt/pool/main/p/postgresql-18
client_deb='postgresql-client-18_18.6-3.pgdg24.04+1_amd64.deb'
client_sha=635c8cbad024be8d1208433ec902cf721ae77028e7d6b7e0231a2768ba4dacfa
libpq_deb='libpq5_18.6-3.pgdg24.04+1_amd64.deb'
libpq_sha=29452c26315aeefeb900895b20b3e8adbd33c32cb9b602a83c30ac5cea1c2055

fetch() { # file url sha
    if [[ ! -f $1 ]]; then curl -fsSL -o "$1" "$2"; fi
    echo "$3  $1" | sha256sum --check --status || { echo "fetch_pg_tools: SHA-256 mismatch for $1" >&2; rm -f "$1"; exit 1; }
}

fetch "$zonky_jar" "$zonky_url" "$zonky_sha"
fetch "$client_deb" "$pgdg/${client_deb//+/%2B}" "$client_sha"
fetch "$libpq_deb" "$pgdg/${libpq_deb//+/%2B}" "$libpq_sha"

mkdir -p server client
unzip -o -q "$zonky_jar" postgres-linux-x86_64.txz -d server-archive
tar -xJf server-archive/postgres-linux-x86_64.txz -C server
dpkg -x "$client_deb" client
dpkg -x "$libpq_deb" client

echo "server-bin : $PWD/server/bin"
echo "server-lib : $PWD/server/lib"
echo "client-bin : $PWD/client/usr/lib/postgresql/18/bin"
echo "client-lib : $PWD/client/usr/lib/x86_64-linux-gnu"
