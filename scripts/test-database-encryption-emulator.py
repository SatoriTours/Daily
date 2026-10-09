#!/usr/bin/env python3
"""Disposable two-AVD native/Keystore/upgrade/SAF restore acceptance. Always shuts AVDs down."""
import argparse
import os
import signal
import subprocess
import time
from pathlib import Path

PACKAGE = "com.dailysatori"
RUNNER = PACKAGE + ".test/com.dailysatori.encryption.DatabaseTestRunner"


class Lab:
    def __init__(self, args):
        self.args = args
        self.root = args.results.resolve()
        self.root.mkdir(parents=True, exist_ok=True, mode=0o700)
        self.owned = {}

    def adb(self, serial, *args, timeout=120, stdin=None):
        result = subprocess.run(["adb", "-s", serial, *map(str, args)], stdin=stdin, capture_output=True, timeout=timeout)
        if result.returncode:
            detail = (result.stdout + result.stderr).decode(errors="replace")[:700] if args[:2] == ("shell", "pm") else ""
            raise RuntimeError("adb failed: " + " ".join(map(str, args[:3])) + "; " + detail)
        return result.stdout

    def start(self, name, port):
        serial = "emulator-" + str(port)
        if serial.encode() + b"\t" in subprocess.check_output(["adb", "devices"]):
            raise RuntimeError("Refusing to reuse an occupied emulator port")
        env = os.environ.copy()
        env["ANDROID_AVD_HOME"] = str(self.args.avd_home.resolve())
        log = open(self.root / (name + ".log"), "wb")
        emulator = Path(os.environ["ANDROID_HOME"]) / "emulator/emulator"
        process = subprocess.Popen([str(emulator), "-avd", name, "-port", str(port), "-no-window", "-no-audio", "-no-boot-anim",
                                    "-no-snapshot", "-wipe-data", "-gpu", "swiftshader", "-accel", "on", "-cores", "2", "-memory", "2048"],
                                   env=env, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
        self.owned[serial] = (name, process, log)
        self.adb(serial, "wait-for-device", timeout=90)
        deadline = time.monotonic() + 100
        while time.monotonic() < deadline:
            if self.adb(serial, "shell", "getprop", "sys.boot_completed").strip() == b"1":
                actual = self.adb(serial, "emu", "avd", "name").decode().splitlines()[0]
                if actual != name:
                    raise RuntimeError("Refusing to touch an unrelated emulator")
                self.adb(serial, "shell", "am", "wait-for-broadcast-idle", timeout=90)
                return serial
            time.sleep(3)
        raise RuntimeError("Emulator boot timeout")

    def stop(self, serial):
        name, process, log = self.owned.pop(serial)
        try:
            actual = self.adb(serial, "emu", "avd", "name", timeout=10).decode().splitlines()[0]
            if actual == name:
                self.adb(serial, "emu", "kill", timeout=15)
        except Exception:
            pass
        try:
            os.killpg(process.pid, signal.SIGTERM)
        except ProcessLookupError:
            pass
        try:
            process.wait(timeout=20)
        except subprocess.TimeoutExpired:
            os.killpg(process.pid, signal.SIGKILL)
        log.close()

    def install(self, serial, apk):
        self.adb(serial, "install", "-r", "-t", apk)

    def instrument(self, serial, cls, label):
        # Component state must be changed from the owning debug UID (API 36 rejects shell changes).
        self.adb(serial, "shell", "run-as", PACKAGE, "/system/bin/pm", "disable", PACKAGE + "/.core.reminder.ReminderReceiver")
        self.adb(serial, "shell", "am", "wait-for-broadcast-idle", timeout=90)
        self.adb(serial, "shell", "am", "force-stop", PACKAGE)
        result = self.adb(serial, "shell", "am", "instrument", "-w", "-r", "-e", "disposable", "yes", "-e", "class",
                          "com.dailysatori.encryption." + cls, RUNNER, timeout=300)
        (self.root / (label + ".log")).write_bytes(result)
        if b"OK (" not in result or b"FAILURES!!!" in result or b"INSTRUMENTATION_FAILED" in result:
            (self.root / (label + "-logcat.log")).write_bytes(self.adb(serial, "logcat", "-d"))
            print(result.decode(errors="replace")[-9000:])
            raise RuntimeError("Device tests failed: " + label)
        print(label + ": " + next(line for line in result.decode().splitlines() if "OK (" in line))

    def launch(self, serial, label):
        self.adb(serial, "shell", "run-as", PACKAGE, "/system/bin/pm", "default-state", PACKAGE + "/.core.reminder.ReminderReceiver")
        self.adb(serial, "logcat", "-c")
        start = self.adb(serial, "shell", "am", "start", "-W", "-n", PACKAGE + "/.MainActivity", timeout=90)
        time.sleep(5)
        crash = self.adb(serial, "logcat", "-d", "-b", "crash")
        (self.root / (label + "-launch.log")).write_bytes(start + crash)
        if b"Status: ok" not in start or b"FATAL EXCEPTION" in crash:
            print((start + crash).decode(errors="replace")[-7000:])
            raise RuntimeError("App launch failed: " + label)
        self.adb(serial, "shell", "uiautomator", "dump", "/data/local/tmp/encryption-ui.xml")
        activities = self.adb(serial, "shell", "dumpsys", "activity", "activities")
        (self.root / (label + "-activities.log")).write_bytes(activities)
        if not any(b"MainActivity" in line for line in activities.splitlines() if b"ResumedActivity" in line):
            raise RuntimeError("MainActivity was replaced by recovery UI: " + label)
        xml = self.adb(serial, "shell", "cat", "/data/local/tmp/encryption-ui.xml")
        (self.root / (label + "-ui.xml")).write_bytes(xml)
        (self.root / (label + ".png")).write_bytes(self.adb(serial, "exec-out", "screencap", "-p"))
        print(label + ": real MainActivity started without a crash")

    def pull_private(self, serial, source, target):
        self.root.joinpath(target).write_bytes(self.adb(serial, "exec-out", "run-as", PACKAGE, "cat", source))

    def push_private(self, serial, source, target):
        self.adb(serial, "shell", "run-as", PACKAGE, "mkdir", "-p", str(Path(target).parent))
        # Only synthetic encrypted test backups / sealed envelopes / hash evidence cross this temporary path.
        temporary = "/data/local/tmp/daily-encryption-fixture"
        try:
            self.adb(serial, "push", self.root / source, temporary)
            self.adb(serial, "shell", "chmod", "644", temporary)
            self.adb(serial, "shell", "run-as", PACKAGE, "cp", temporary, target)
        finally:
            self.adb(serial, "shell", "rm", "-f", temporary)
        if self.adb(serial, "exec-out", "run-as", PACKAGE, "cat", target) != (self.root / source).read_bytes():
            raise RuntimeError("Private fixture transfer failed")

    def run(self):
        try:
            source = self.start("DailyEncryptionSource", 5554)
            self.install(source, self.args.apk)
            self.install(source, self.args.test_apk)
            self.adb(source, "shell", "pm", "clear", PACKAGE)
            self.instrument(source, self.args.core_class, "native-core")
            if self.args.core_only:
                return
            self.adb(source, "shell", "am", "force-stop", PACKAGE)
            self.adb(source, "shell", "pm", "clear", PACKAGE)
            self.install(source, self.args.old_apk)
            self.instrument(source, "LegacySeedDeviceTest", "legacy-seed")
            self.launch(source, "old-app")
            self.adb(source, "shell", "am", "force-stop", PACKAGE)
            self.pull_private(source, "databases/daily_satori.db", "before-upgrade.db")
            self.install(source, self.args.apk)
            self.launch(source, "upgraded-app")
            self.instrument(source, "SourceUpgradeDeviceTest", "upgrade-and-portable-export")
            self.adb(source, "shell", "am", "force-stop", PACKAGE)
            for remote, local in [("files/source-evidence.json", "source-evidence.json"), ("cache/device-transfer.enc", "transferred.enc"),
                                  ("no_backup/database_key.sec", "source-device-key.sec"), ("databases/daily_satori.db", "source-database.db")]:
                self.pull_private(source, remote, local)
            self.stop(source)
            receiver = self.start("DailyEncryptionReceiver", 5556)
            self.install(receiver, self.args.apk)
            self.install(receiver, self.args.test_apk)
            self.adb(receiver, "shell", "pm", "clear", PACKAGE)
            self.adb(receiver, "shell", "run-as", PACKAGE, "mkdir", "-p", "files", "databases", "no_backup")
            for local, remote in [("source-evidence.json", "files/source-evidence.json"), ("transferred.enc", "files/transferred.enc"),
                                  ("source-device-key.sec", "files/source-device-key.sec"), ("source-database.db", "files/source-database.db")]:
                self.push_private(receiver, local, remote)
            self.instrument(receiver, "ReceiverRestoreDeviceTest", "different-device-restore")
            self.launch(receiver, "restored-app")
            self.instrument(receiver, "ReceiverColdStartDeviceTest", "receiver-cold-start-data")
            print("PASS: old APK overwrite upgrade, native crypto, real Keystore, SAF and independent-device password recovery")
        finally:
            for serial in list(self.owned):
                self.stop(serial)


def main():
    os.umask(0o077)
    def interrupted(signum, _frame):
        raise SystemExit(128 + signum)
    signal.signal(signal.SIGTERM, interrupted)
    signal.signal(signal.SIGINT, interrupted)
    parser = argparse.ArgumentParser()
    parser.add_argument("--avd-home", type=Path, required=True)
    parser.add_argument("--results", type=Path, required=True)
    parser.add_argument("--apk", type=Path, default=Path("app/build/outputs/apk/debug/app-debug.apk"))
    parser.add_argument("--test-apk", type=Path, default=Path("app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"))
    parser.add_argument("--old-apk", type=Path)
    parser.add_argument("--core-only", action="store_true")
    parser.add_argument("--core-class", default="DatabaseDeviceTest")
    args = parser.parse_args()
    if not args.avd_home.resolve().is_relative_to(Path(".local").resolve()):
        parser.error("AVDs must be disposable copies under the project's ignored .local directory")
    if not args.core_only and not args.old_apk:
        parser.error("Old APK required for a genuine overwrite upgrade")
    if ".local" not in args.avd_home.resolve().parts:
        parser.error("Only explicitly private disposable AVD directories are supported")
    Lab(args).run()


if __name__ == "__main__":
    main()
