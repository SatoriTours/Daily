#!/usr/bin/env bash
# Host-side SQLCipher for code-level startup/backup tests; never an Android JNI substitute.
set -euo pipefail

root="${1:?Usage: bash scripts/build-test-sqlcipher.sh <dedicated-build-directory>}"
mkdir -p "$root"
root="$(cd "$root" && pwd)"
archive="$root/sqlcipher-v4.19.0.tar.gz"
curl --fail --location --retry 1 --connect-timeout 10 --max-time 90 \
  https://codeload.github.com/sqlcipher/sqlcipher/tar.gz/refs/tags/v4.19.0 -o "$archive"
printf '%s  %s\n' 7075f96cbabe45b4ecfc2e6b1745a625f856f695b0827a5506ce9ed85b906aa0 "$archive" | sha256sum --check -
tar --no-same-owner -xzf "$archive" -C "$root"
cd "$root/sqlcipher-4.19.0"
./configure --with-tempstore=yes \
  CFLAGS="-O2 -DSQLITE_HAS_CODEC -DSQLITE_EXTRA_INIT=sqlcipher_extra_init -DSQLITE_EXTRA_SHUTDOWN=sqlcipher_extra_shutdown" \
  LDFLAGS="-lcrypto"
make -j2 sqlite3
test -x sqlite3
printf 'SQLCipher test binary: %s/sqlite3\n' "$PWD"
