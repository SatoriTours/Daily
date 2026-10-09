#!/usr/bin/env python3
"""Check actual APK SQLCipher ABIs, ELF 16KB alignment and ZIP page alignment."""
import argparse
import os
import shutil
import struct
import subprocess
import zipfile
from pathlib import Path


def load_alignments(data):
    if data[:4] != b"\x7fELF" or data[4] not in (1, 2) or data[5] not in (1, 2):
        raise ValueError("Invalid ELF header")
    endian = "<" if data[5] == 1 else ">"
    elf64 = data[4] == 2
    offset = struct.unpack_from(endian + ("Q" if elf64 else "I"), data, 32 if elf64 else 28)[0]
    size, count = struct.unpack_from(endian + "HH", data, 54 if elf64 else 42)
    result = []
    for index in range(count):
        start = offset + size * index
        if struct.unpack_from(endian + "I", data, start)[0] != 1:
            continue
        alignment = struct.unpack_from(endian + ("Q" if elf64 else "I"), data, start + (48 if elf64 else 28))[0]
        result.append(alignment)
    if not result:
        raise ValueError("No ELF LOAD segments")
    return result


def zipalign_tool():
    configured = shutil.which("zipalign")
    if configured:
        return configured
    sdk = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT") or str(Path.home() / "Android/Sdk")
    candidates = sorted(Path(sdk).glob("build-tools/*/zipalign"), reverse=True)
    if not candidates:
        raise RuntimeError("Android SDK zipalign unavailable; verification NOT completed")
    return str(candidates[0])


def check(apk):
    with zipfile.ZipFile(apk) as archive:
        libraries = [item for item in archive.infolist() if item.filename.startswith("lib/") and item.filename.endswith("/libsqlcipher.so")]
        all_abis = {item.filename.split("/")[1] for item in archive.infolist() if item.filename.startswith("lib/") and item.filename.endswith(".so")}
        cipher_abis = {item.filename.split("/")[1] for item in libraries}
        if not libraries or not all_abis.issubset(cipher_abis):
            raise AssertionError("SQLCipher library missing for a packaged ABI")
        for item in libraries:
            if any(value < 16384 for value in load_alignments(archive.read(item))):
                raise AssertionError("SQLCipher ELF LOAD alignment below 16KB: " + item.filename)
        forbidden = {"ai-test.json", "database_key.sec", "database_key.json", "backup_password.sec"}
        if any(Path(item.filename).name in forbidden or ".local" in Path(item.filename).parts for item in archive.infolist()):
            raise AssertionError("Private configuration/key file found in APK")
    result = subprocess.run([zipalign_tool(), "-c", "-P", "16", "4", str(apk)], capture_output=True, text=True)
    if result.returncode:
        raise AssertionError("APK ZIP 16KB page alignment failed")
    print("PASS: SQLCipher " + ", ".join(sorted(cipher_abis)) + "; ELF/ZIP 16KB alignment; no private key/config files")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("apk", type=Path)
    check(parser.parse_args().apk)


if __name__ == "__main__":
    main()
