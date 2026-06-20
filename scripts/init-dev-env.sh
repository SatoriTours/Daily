#!/usr/bin/env bash

set -euo pipefail

if [[ "${BASH_SOURCE[0]}" != "$0" ]]; then
  printf '\033[0;31m[ERROR]\033[0m Run this script as an executable, do not source it.\n' >&2
  return 1
fi

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$HOME/Android/Sdk}}"
JAVA_HOME_DEFAULT="/usr/lib/jvm/java-21-openjdk"
CMDLINE_TOOLS_VERSION="14742923"
CMDLINE_TOOLS_ZIP="commandlinetools-linux-${CMDLINE_TOOLS_VERSION}_latest.zip"
CMDLINE_TOOLS_URL="${ANDROID_CMDLINE_TOOLS_URL:-https://dl.google.com/android/repository/${CMDLINE_TOOLS_ZIP}}"
BUILD_TOOLS_VERSION="${ANDROID_BUILD_TOOLS_VERSION:-36.0.0}"
PLATFORM_VERSION="${ANDROID_PLATFORM_VERSION:-android-36}"
case "$(basename "${SHELL:-zsh}")" in
  bash) DEFAULT_SHELL_PROFILE="$HOME/.bashrc" ;;
  zsh) DEFAULT_SHELL_PROFILE="$HOME/.zshrc" ;;
  *) DEFAULT_SHELL_PROFILE="$HOME/.profile" ;;
esac
SHELL_PROFILE="${SHELL_PROFILE:-$DEFAULT_SHELL_PROFILE}"

info() { printf '\033[0;34m[INFO]\033[0m %s\n' "$*"; }
ok() { printf '\033[0;32m[OK]\033[0m %s\n' "$*"; }
warn() { printf '\033[0;33m[WARN]\033[0m %s\n' "$*"; }
err() { printf '\033[0;31m[ERROR]\033[0m %s\n' "$*" >&2; }

usage() {
  cat <<EOF
Usage: scripts/init-dev-env.sh [options]

Options:
  --no-profile          Do not append JAVA_HOME/ANDROID_HOME/PATH to shell profile.
  --skip-gradle-check   Do not run ./gradlew :app:compileDebugKotlin after setup.
  --help                Show this help.

Environment overrides:
  ANDROID_SDK_ROOT              Default: $HOME/Android/Sdk
  ANDROID_HOME                  Used if ANDROID_SDK_ROOT is not set
  ANDROID_CMDLINE_TOOLS_URL     Default: official Linux command-line tools URL
  ANDROID_BUILD_TOOLS_VERSION   Default: 36.0.0
  ANDROID_PLATFORM_VERSION      Default: android-36
  SHELL_PROFILE                 Default: $DEFAULT_SHELL_PROFILE
EOF
}

UPDATE_PROFILE=1
RUN_GRADLE_CHECK=1

while [[ $# -gt 0 ]]; do
  case "$1" in
    --no-profile)
      UPDATE_PROFILE=0
      shift
      ;;
    --skip-gradle-check)
      RUN_GRADLE_CHECK=0
      shift
      ;;
    --help|-h)
      usage
      exit 0
      ;;
    *)
      err "Unknown option: $1"
      usage
      exit 2
      ;;
  esac
done

require_arch_like() {
  if [[ ! -r /etc/os-release ]]; then
    err "Cannot detect OS. This script currently targets Arch/EndeavourOS."
    exit 1
  fi

  # shellcheck disable=SC1091
  source /etc/os-release
  if [[ "${ID:-}" != "arch" && "${ID:-}" != "endeavouros" && "${ID_LIKE:-}" != *"arch"* ]]; then
    err "Detected ${PRETTY_NAME:-unknown OS}. This script currently targets Arch/EndeavourOS."
    exit 1
  fi

  if ! command -v pacman >/dev/null 2>&1; then
    err "pacman was not found. This script currently targets Arch/EndeavourOS."
    exit 1
  fi

}

normalize_android_sdk_root() {
  mkdir -p "$ANDROID_SDK_ROOT"
  ANDROID_SDK_ROOT="$(cd "$ANDROID_SDK_ROOT" && pwd)"
}

install_system_packages() {
  local packages=(jdk21-openjdk unzip)
  local missing_packages=()

  if command -v curl >/dev/null 2>&1; then
    :
  elif command -v wget >/dev/null 2>&1; then
    :
  else
    packages+=(curl)
  fi

  for package in "${packages[@]}"; do
    if ! pacman -Q "$package" >/dev/null 2>&1; then
      missing_packages+=("$package")
    fi
  done

  if [[ "${#missing_packages[@]}" -eq 0 ]]; then
    ok "System packages already installed: ${packages[*]}"
    return
  fi

  if ! command -v sudo >/dev/null 2>&1; then
    err "Missing system packages: ${missing_packages[*]}"
    err "sudo was not found. Install the missing packages manually or install sudo first."
    exit 1
  fi

  info "Installing missing system packages: ${missing_packages[*]}"
  sudo pacman -S --needed "${missing_packages[@]}"
}

ensure_java_home() {
  if [[ -x "$JAVA_HOME_DEFAULT/bin/java" ]]; then
    export JAVA_HOME="$JAVA_HOME_DEFAULT"
    export PATH="$JAVA_HOME/bin:$PATH"
  elif [[ -n "${JAVA_HOME:-}" && -x "$JAVA_HOME/bin/java" ]]; then
    :
  else
    err "JDK 21 was not found. Expected $JAVA_HOME_DEFAULT/bin/java."
    exit 1
  fi

  info "Java version:"
  java -version

  local java_major
  java_major="$(java -version 2>&1 | awk -F '"' '/version/ { split($2, parts, "."); print parts[1]; exit }')"
  if [[ "$java_major" != "21" ]]; then
    err "Expected Java 21, but java -version reported major version '$java_major'."
    err "Check JAVA_HOME and PATH, then rerun this script."
    exit 1
  fi
}

download_file() {
  local url="$1"
  local output="$2"

  if command -v curl >/dev/null 2>&1; then
    curl -fL --retry 3 --retry-delay 2 -o "$output" "$url"
  elif command -v wget >/dev/null 2>&1; then
    wget -O "$output" "$url"
  else
    err "Neither curl nor wget is available."
    exit 1
  fi
}

install_cmdline_tools() {
  local sdkmanager="$ANDROID_SDK_ROOT/cmdline-tools/latest/bin/sdkmanager"

  if [[ -x "$sdkmanager" ]]; then
    ok "Android command-line tools already installed."
    return
  fi

  info "Installing Android command-line tools into $ANDROID_SDK_ROOT"
  local tmp_dir
  tmp_dir="$(mktemp -d)"
  TMP_CMDLINE_TOOLS_DIR="$tmp_dir"
  cleanup_cmdline_tools_tmp() {
    if [[ -n "${TMP_CMDLINE_TOOLS_DIR:-}" ]]; then
      rm -rf "$TMP_CMDLINE_TOOLS_DIR"
    fi
  }
  trap cleanup_cmdline_tools_tmp EXIT

  download_file "$CMDLINE_TOOLS_URL" "$tmp_dir/$CMDLINE_TOOLS_ZIP"
  mkdir -p "$ANDROID_SDK_ROOT/cmdline-tools"
  unzip -q "$tmp_dir/$CMDLINE_TOOLS_ZIP" -d "$tmp_dir"
  if [[ ! -d "$tmp_dir/cmdline-tools" ]]; then
    err "Downloaded archive did not contain the expected cmdline-tools directory."
    exit 1
  fi
  if [[ -e "$ANDROID_SDK_ROOT/cmdline-tools/latest" ]]; then
    local backup_path="$ANDROID_SDK_ROOT/cmdline-tools/latest.backup.$(date +%Y%m%d%H%M%S)"
    warn "Existing non-functional cmdline-tools/latest found; moving it to $backup_path"
    mv "$ANDROID_SDK_ROOT/cmdline-tools/latest" "$backup_path"
  fi
  mv "$tmp_dir/cmdline-tools" "$ANDROID_SDK_ROOT/cmdline-tools/latest"
  rm -rf "$tmp_dir"
  TMP_CMDLINE_TOOLS_DIR=""
  trap - EXIT

  ok "Android command-line tools installed."
}

install_android_sdk_packages() {
  local sdkmanager="$ANDROID_SDK_ROOT/cmdline-tools/latest/bin/sdkmanager"

  mkdir -p "$HOME/.android"
  touch "$HOME/.android/repositories.cfg"

  export ANDROID_HOME="$ANDROID_SDK_ROOT"
  export ANDROID_SDK_ROOT="$ANDROID_SDK_ROOT"
  export PATH="$ANDROID_SDK_ROOT/platform-tools:$ANDROID_SDK_ROOT/cmdline-tools/latest/bin:$PATH"

  info "Accepting Android SDK licenses."
  set +o pipefail
  yes | "$sdkmanager" --licenses >/dev/null
  local sdkmanager_license_status="${PIPESTATUS[1]}"
  set -o pipefail
  if [[ "$sdkmanager_license_status" -ne 0 ]]; then
    err "sdkmanager --licenses failed with exit code $sdkmanager_license_status."
    exit "$sdkmanager_license_status"
  fi

  info "Installing Android SDK packages for this project."
  "$sdkmanager" \
    "platform-tools" \
    "platforms;$PLATFORM_VERSION" \
    "build-tools;$BUILD_TOOLS_VERSION"

  ok "Android SDK packages installed."
}

write_local_properties() {
  local local_properties="$PROJECT_ROOT/local.properties"
  local tmp_file="$PROJECT_ROOT/.local.properties.tmp.$$"

  if [[ -f "$local_properties" ]]; then
    if grep -q '^sdk\.dir=' "$local_properties"; then
      if awk -v sdk_dir="$ANDROID_SDK_ROOT" '
        /^sdk\.dir=/ && !done {
          print "sdk.dir=" sdk_dir
          done = 1
          next
        }
        { print }
      ' "$local_properties" > "$tmp_file"; then
        mv "$tmp_file" "$local_properties"
      else
        rm -f "$tmp_file"
        err "Failed to update $local_properties"
        exit 1
      fi
    else
      printf '\nsdk.dir=%s\n' "$ANDROID_SDK_ROOT" >> "$local_properties"
    fi
  else
    printf 'sdk.dir=%s\n' "$ANDROID_SDK_ROOT" > "$local_properties"
  fi
  rm -f "$tmp_file"
  ok "Wrote $local_properties"
}

update_shell_profile() {
  if [[ "$UPDATE_PROFILE" -eq 0 ]]; then
    warn "Skipped shell profile update."
    return
  fi

  mkdir -p "$(dirname "$SHELL_PROFILE")"
  touch "$SHELL_PROFILE"

  local marker_start="# Daily Satori Android dev env start"
  local marker_end="# Daily Satori Android dev env end"

  if grep -Fq "$marker_start" "$SHELL_PROFILE" && grep -Fq "$marker_end" "$SHELL_PROFILE"; then
    sed -i "/^$marker_start$/,/^$marker_end$/d" "$SHELL_PROFILE"
  elif grep -Fq "$marker_start" "$SHELL_PROFILE"; then
    warn "Found an incomplete Daily Satori environment block in $SHELL_PROFILE; appending a fresh block without deleting existing content."
  fi

  cat >> "$SHELL_PROFILE" <<EOF

$marker_start
export JAVA_HOME="$JAVA_HOME_DEFAULT"
export ANDROID_HOME="$ANDROID_SDK_ROOT"
export ANDROID_SDK_ROOT=\$ANDROID_HOME
export PATH=\$JAVA_HOME/bin:\$ANDROID_HOME/platform-tools:\$ANDROID_HOME/cmdline-tools/latest/bin:\$PATH
$marker_end
EOF

  ok "Appended environment variables to $SHELL_PROFILE"
}

run_verification() {
  info "Verifying installed tools."
  java -version
  javac -version
  adb version
  "$ANDROID_SDK_ROOT/cmdline-tools/latest/bin/sdkmanager" --version

  if [[ "$RUN_GRADLE_CHECK" -eq 0 ]]; then
    warn "Skipped Gradle compile check."
    return
  fi

  info "Running project compile check. This may download Gradle and Maven dependencies."
  if ! (cd "$PROJECT_ROOT" && ./gradlew :app:compileDebugKotlin --no-configuration-cache); then
    err "Gradle compile check failed."
    err "If the failure mentions dl.google.com, Maven, TLS, or handshake, the local network cannot reach the official Google Maven repository."
    err "Fix proxy/DNS/network access, then rerun this script or run:"
    err "  cd $PROJECT_ROOT && ./gradlew :app:compileDebugKotlin --no-configuration-cache"
    exit 1
  fi
  ok "Project compile check passed."
}

main() {
  require_arch_like
  normalize_android_sdk_root
  install_system_packages
  ensure_java_home
  install_cmdline_tools
  install_android_sdk_packages
  write_local_properties
  update_shell_profile
  run_verification

  cat <<EOF

Done.

For the current terminal, run:
  export JAVA_HOME="$JAVA_HOME_DEFAULT"
  export ANDROID_HOME="$ANDROID_SDK_ROOT"
  export ANDROID_SDK_ROOT=\$ANDROID_HOME
  export PATH=\$JAVA_HOME/bin:\$ANDROID_HOME/platform-tools:\$ANDROID_HOME/cmdline-tools/latest/bin:\$PATH

Or open a new terminal after the shell profile update.
EOF
}

main "$@"
