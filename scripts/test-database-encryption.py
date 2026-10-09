#!/usr/bin/env python3
"""Real SQLCipher file probes; never substitutes ordinary SQLite for encryption."""
import argparse
import os
import sqlite3
import subprocess
import tempfile
from pathlib import Path


def run(binary, database, sql, succeeds=True):
    result = subprocess.run([binary, "-batch", str(database)], input=".bail on\n" + sql,
                            text=True, capture_output=True, timeout=30)
    if (result.returncode == 0) != succeeds:
        raise AssertionError("Unexpected SQLCipher result (key/SQL details intentionally omitted)")
    return result.stdout.strip().splitlines()


def quote(value):
    return "'" + str(value).replace("'", "''") + "'"


def probe(binary):
    version = run(binary, ":memory:", "PRAGMA cipher_version;\n")
    assert len(version) == 1 and version[0].startswith("4."), "Requires SQLCipher 4, not ordinary SQLite"
    with tempfile.TemporaryDirectory(prefix="daily-cipher-probe-") as directory:
        root = Path(directory)
        source, encrypted, backup = (root / name for name in ("old.db", "encrypted.db", "backup.db"))
        key = os.urandom(32).hex()
        replacement = os.urandom(32).hex()
        unlock = f'''PRAGMA key = "x'{key}'";\n'''
        marker = "daily-encryption-probe-private-928873"
        with sqlite3.connect(source) as old:
            old.execute("PRAGMA journal_mode=WAL")
            old.execute("CREATE TABLE data(value TEXT)")
            old.execute("CREATE INDEX value_idx ON data(value)")
            old.execute("INSERT INTO data VALUES(?)", (marker,))
            old.execute("PRAGMA user_version=7")
            old.commit()
            assert Path(str(source) + "-wal").is_file()
            run(binary, source, f'''ATTACH DATABASE {quote(encrypted)} AS encrypted KEY "x'{key}'";
BEGIN IMMEDIATE;
SELECT sqlcipher_export('encrypted');
PRAGMA encrypted.user_version=7;
COMMIT;
DETACH DATABASE encrypted;
''')
            assert old.execute("SELECT value FROM data").fetchone()[0] == marker
        assert marker.encode() not in encrypted.read_bytes()
        assert not encrypted.read_bytes().startswith(b"SQLite format 3\0")
        try:
            with sqlite3.connect(encrypted) as plain:
                plain.execute("SELECT * FROM data").fetchall()
        except sqlite3.DatabaseError:
            pass
        else:
            raise AssertionError("Ordinary SQLite unexpectedly read encrypted data")
        run(binary, encrypted, "SELECT * FROM data;\n", succeeds=False)
        run(binary, encrypted, f'''PRAGMA key = "x'{replacement}'"; SELECT * FROM data;\n''', succeeds=False)
        assert run(binary, encrypted, unlock + "PRAGMA integrity_check; PRAGMA cipher_integrity_check; SELECT value FROM data; PRAGMA user_version;\n")[-3:] == ["ok", marker, "7"]
        run(binary, encrypted, unlock + f'''ATTACH DATABASE {quote(backup)} AS encrypted KEY "x'{key}'";
BEGIN IMMEDIATE;
SELECT sqlcipher_export('encrypted');
PRAGMA encrypted.user_version=7;
COMMIT;
DETACH DATABASE encrypted;
''')
        assert run(binary, backup, unlock + "SELECT value FROM data;\n")[-1] == marker
        # Changing the DB key rewrites this test DB; the old backup remains independently restorable.
        run(binary, encrypted, unlock + f'''PRAGMA rekey = "x'{replacement}'";\n''')
        run(binary, encrypted, unlock + "SELECT * FROM data;\n", succeeds=False)
        assert run(binary, encrypted, f'''PRAGMA key = "x'{replacement}'"; SELECT value FROM data;\n''')[-1] == marker
        assert run(binary, backup, unlock + "SELECT value FROM data;\n")[-1] == marker
    print("PASS: SQLCipher actual files, committed WAL, keyed snapshots, wrong/no-key rejection and key rotation; " + version[0])


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--sqlcipher", default=os.environ.get("SQLCIPHER_BIN"))
    args = parser.parse_args()
    if not args.sqlcipher or not Path(args.sqlcipher).is_file():
        parser.error("Real SQLCipher executable required; verification NOT completed")
    probe(str(Path(args.sqlcipher).resolve()))


if __name__ == "__main__":
    main()
