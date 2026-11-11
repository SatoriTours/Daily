#!/usr/bin/env bash
# Installs only the host packages the pinned SQLCipher build actually needs.
# Evidence: the pinned 4.19.0 build ships autosetup/jimsh0.c and generates its own jimsh with the
# C compiler, so code generation does not need a system tcl-dev; the only host capabilities are a
# C compiler, make, pkg-config and OpenSSL headers/library (-lcrypto).
set -euo pipefail

apt_get="${APT_GET:-apt-get}"
# Budget: 2 attempts x (update 120s + install 120s) + 5s pause = 485s, inside the 10 min step.
apt_timeout="${APT_TIMEOUT_SECONDS:-120}"
apt_attempts="${APT_ATTEMPTS:-2}"
retry_delay="${APT_RETRY_DELAY_SECONDS:-5}"
cc_bin="${CC:-cc}"

have_command() { command -v "$1" >/dev/null 2>&1; }

as_root() {
  if [ "$(id -u)" -eq 0 ]; then
    "$@"
  else
    sudo "$@"
  fi
}

# Real compile+run probe: a present pkg-config or header alone must not count as usable.
probe_openssl() {
  local dir cflags="" libs="-lcrypto"
  dir="$(mktemp -d)"
  printf "%s\n" \
    "#include <openssl/evp.h>" \
    "int main(void) { EVP_MD_CTX *c = EVP_MD_CTX_new(); EVP_MD_CTX_free(c); return 0; }" \
    > "$dir/probe.c"
  if have_command pkg-config && pkg-config --exists openssl >/dev/null 2>&1; then
    # shellcheck disable=SC2046
    cflags="$(pkg-config --cflags openssl)"
    libs="$(pkg-config --libs openssl)"
  fi
  # shellcheck disable=SC2086
  if ! "$cc_bin" $cflags "$dir/probe.c" -o "$dir/probe" $libs >/dev/null 2>&1; then
    rm -rf "$dir"
    return 1
  fi
  if ! "$dir/probe" >/dev/null 2>&1; then
    rm -rf "$dir"
    return 1
  fi
  rm -rf "$dir"
  return 0
}

missing_packages() {
  local packages=()
  if ! have_command "$cc_bin" || ! have_command make; then
    packages+=("build-essential")
  fi
  if ! have_command pkg-config; then
    packages+=("pkg-config")
  fi
  if ! probe_openssl; then
    packages+=("libssl-dev")
  fi
  if [ "${#packages[@]}" -eq 0 ]; then
    printf ""
  else
    printf "%s " "${packages[@]}"
  fi
}

missing="$(missing_packages)"
if [ -z "$missing" ]; then
  echo "SQLCipher build dependencies already available (compiler, make, pkg-config, OpenSSL)"
  exit 0
fi

read -r -a packages <<< "$missing"
attempt=1
while [ "$attempt" -le "$apt_attempts" ]; do
  echo "Installing host SQLCipher build dependencies (attempt $attempt/$apt_attempts): ${packages[*]}"
  if as_root timeout "$apt_timeout" "$apt_get" update \
      && as_root timeout "$apt_timeout" "$apt_get" install -y --no-install-recommends "${packages[@]}"; then
    break
  fi
  if [ "$attempt" -eq "$apt_attempts" ]; then
    echo "apt failed to install ${packages[*]} after $apt_attempts attempt(s); package sources may be unreachable" >&2
    exit 1
  fi
  attempt=$((attempt + 1))
  sleep "$retry_delay"
done

remaining="$(missing_packages)"
if [ -n "$remaining" ]; then
  echo "SQLCipher build dependencies still missing after install: $remaining" >&2
  echo "Provide these packages in the runner image or reachable apt sources" >&2
  exit 1
fi
echo "Host SQLCipher build dependencies are ready"
