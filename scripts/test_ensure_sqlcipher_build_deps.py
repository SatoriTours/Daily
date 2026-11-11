#!/usr/bin/env python3
"""Behaviour tests for scripts/ensure-sqlcipher-build-deps.sh (stdlib only).

The script may only touch apt when a capability is actually missing, must fail loudly when apt
fails, and must not report success on a capability it could not really probe.
"""
import re
import stat
import subprocess
import tempfile
import textwrap
import unittest
from pathlib import Path

SCRIPT = Path(__file__).resolve().with_name("ensure-sqlcipher-build-deps.sh")


class EnsureDepsTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.root = Path(self.tmp.name)
        self.bin = self.root / "bin"
        self.bin.mkdir()
        self.state = self.root / "state"
        self.state.mkdir()
        self.apt_log = self.root / "apt.log"
        self.timeout_log = self.root / "timeout.log"
        self.fake("timeout", '#!/usr/bin/env bash\necho "$1" >> "$TIMEOUT_LOG"\nshift\nexec "$@"\n')
        self.fake("sleep", "#!/usr/bin/env bash\nexit 0\n")
        self.fake("sudo", '#!/usr/bin/env bash\nexec "$@"\n')
        self.fake(
            "apt-get",
            textwrap.dedent(
                """\\
                #!/usr/bin/env bash
                echo "apt-get $*" >> "$APT_LOG"
                if [ "$1" = "update" ]; then exit "${APT_UPDATE_EXIT:-0}"; fi
                if [ "$1" = "install" ]; then
                  if [ "${APT_INSTALL_FIXES:-1}" = "1" ]; then touch "$STATE/openssl-ok"; fi
                  exit "${APT_INSTALL_EXIT:-0}"
                fi
                exit 0
                """
            ),
        )

    def tearDown(self):
        self.tmp.cleanup()

    def fake(self, name, body):
        path = self.bin / name
        path.write_text(body)
        path.chmod(path.stat().st_mode | stat.S_IEXEC)

    def write_toolchain(self, openssl_ok):
        state = self.state
        self.fake(
            "cc",
            textwrap.dedent(
                """\\
                #!/usr/bin/env bash
                if [ -e "%s/openssl-ok" ]; then
                  out=""
                  while [ $# -gt 0 ]; do
                    if [ "$1" = "-o" ]; then out="$2"; shift; fi
                    shift
                  done
                  if [ -n "$out" ]; then printf '#!/bin/sh\nexit 0\n' > "$out"; chmod +x "$out"; fi
                  exit 0
                fi
                exit 1
                """
                % state
            ),
        )
        self.fake("make", "#!/usr/bin/env bash\nexit 0\n")
        self.fake(
            "pkg-config",
            textwrap.dedent(
                """\\
                #!/usr/bin/env bash
                case "$1" in
                  --exists) [ -e "%s/openssl-ok" ] && exit 0 || exit 1 ;;
                  --cflags) echo "" ;;
                  --libs) echo "-lcrypto" ;;
                  *) exit 1 ;;
                esac
                """
                % state
            ),
        )
        if openssl_ok:
            (self.state / "openssl-ok").touch()

    def run_script(self, env_extra=None):
        env = {
            "PATH": f"{self.bin}:/usr/bin:/bin",
            "APT_LOG": str(self.apt_log),
            "TIMEOUT_LOG": str(self.timeout_log),
            "STATE": str(self.state),
            "APT_ATTEMPTS": "1",
            "APT_RETRY_DELAY_SECONDS": "0",
            "APT_TIMEOUT_SECONDS": "30",
        }
        env.update(env_extra or {})
        return subprocess.run(
            ["bash", str(SCRIPT)], capture_output=True, text=True, env=env, timeout=120
        )

    def apt_calls(self):
        if not self.apt_log.exists():
            return []
        return [line for line in self.apt_log.read_text().splitlines() if line]

    def test_available_dependencies_never_touch_apt(self):
        self.write_toolchain(openssl_ok=True)
        result = self.run_script()
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn("already available", result.stdout)
        self.assertEqual([], self.apt_calls())

    def test_missing_openssl_installs_only_then_reverifies(self):
        self.write_toolchain(openssl_ok=False)
        result = self.run_script()
        self.assertEqual(0, result.returncode, result.stderr)
        calls = self.apt_calls()
        self.assertTrue(any(call.startswith("apt-get update") for call in calls), calls)
        install = [call for call in calls if call.startswith("apt-get install")]
        self.assertEqual(1, len(install), calls)
        self.assertIn("libssl-dev", install[0])
        self.assertNotIn("tcl-dev", install[0], "tcl bindings are not built by the pinned script")

    def test_apt_failure_is_fatal(self):
        self.write_toolchain(openssl_ok=False)
        result = self.run_script({"APT_UPDATE_EXIT": "100"})
        self.assertNotEqual(0, result.returncode)
        self.assertIn("apt", result.stderr.lower())
        self.assertNotIn("already available", result.stdout)

    def test_unprobeable_dependency_never_reports_success(self):
        self.write_toolchain(openssl_ok=False)
        result = self.run_script({"APT_INSTALL_FIXES": "0"})
        self.assertNotEqual(0, result.returncode)
        self.assertIn("libssl-dev", result.stderr)


    def test_default_budget_and_attempts_stay_inside_the_step_timeout(self):
        script = SCRIPT.read_text()
        attempts = int(re.search(r"APT_ATTEMPTS:-([0-9]+)", script).group(1))
        timeout_seconds = int(re.search(r"APT_TIMEOUT_SECONDS:-([0-9]+)", script).group(1))
        delay = int(re.search(r"APT_RETRY_DELAY_SECONDS:-([0-9]+)", script).group(1))
        self.assertEqual(2, attempts)
        self.assertEqual(120, timeout_seconds)
        self.assertEqual(5, delay)
        self.assertEqual(485, attempts * 2 * timeout_seconds + delay)
        self.assertLess(attempts * 2 * timeout_seconds + delay, 10 * 60)

        self.write_toolchain(openssl_ok=False)
        env = {
            "PATH": f"{self.bin}:/usr/bin:/bin",
            "APT_LOG": str(self.apt_log),
            "TIMEOUT_LOG": str(self.timeout_log),
            "STATE": str(self.state),
            "APT_UPDATE_EXIT": "100",
        }
        result = subprocess.run(["bash", str(SCRIPT)], capture_output=True, text=True, env=env, timeout=120)
        self.assertNotEqual(0, result.returncode)
        self.assertIn("apt", result.stderr.lower())
        updates = [call for call in self.apt_calls() if call.startswith("apt-get update")]
        self.assertEqual(2, len(updates), self.apt_calls())
        # A failing update short-circuits the install, so one bounded timeout per attempt is observed.
        self.assertEqual(["120", "120"], self.timeout_log.read_text().split())

    def test_ci_runs_every_scripts_test(self):
        workflow = (Path(__file__).resolve().parents[1] / ".github" / "workflows" / "code-test-gate.yml").read_text()
        self.assertIn("python3 -m unittest discover -s scripts -p 'test_*.py'", workflow)
        self.assertNotIn("Test release metadata", workflow)


if __name__ == "__main__":
    unittest.main()
