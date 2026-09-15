import tempfile
import unittest
from unittest.mock import patch
from pathlib import Path

from ci_release import (
    COMMIT_BUILD_ASSET, COMMIT_BUILD_TAG, build_version_code, build_info, make_manifest,
    should_update_pointer, publish_build, should_mark_latest,
)


class ReleaseMetadataTest(unittest.TestCase):
    def test_older_stable_release_cannot_take_over_latest(self):
        self.assertTrue(should_mark_latest(None, "5.1.63"))
        self.assertTrue(should_mark_latest("v5.1.63", "5.1.65"))
        self.assertFalse(should_mark_latest("v5.1.65", "5.1.63"))
        self.assertFalse(should_mark_latest("v5.1.65", "5.1.65"))

    def test_published_build_keeps_its_original_apk_checksum_on_rerun(self):
        candidate = dict(build_info("5.1.63", 300, "commit", "a" * 40, 27), sha256="new")
        published = dict(candidate, sha256="original")
        with patch("ci_release.release_by_tag", return_value={"draft": False}), \
             patch("ci_release.download_manifest", return_value=published), patch("ci_release.run") as command:
            self.assertEqual(published, publish_build("SatoriTours/Daily", Path("dist"), candidate))
            command.assert_not_called()

    def test_rolling_channel_replaces_single_commit_build_release(self):
        candidate = build_info("5.1.63", 301, "commit", "b" * 40, 27)
        previous = dict(candidate, versionCode=300, commitSha="a" * 40, versionName="5.1.63-commit.173933")
        with patch("ci_release.release_by_tag", return_value={"draft": False}), \
             patch("ci_release.download_manifest", return_value=previous), patch("ci_release.run") as command:
            published = publish_build("SatoriTours/Daily", Path("dist"), candidate)
            self.assertEqual(candidate, published)
            calls = [call.args for call in command.call_args_list]
            self.assertIn("PATCH", calls[0])
            self.assertTrue(any(f"refs/tags/{COMMIT_BUILD_TAG}" in part for part in calls[0]))
            self.assertEqual("upload", calls[1][2])
            self.assertIn(str(Path("dist") / COMMIT_BUILD_ASSET), calls[1])
            self.assertIn("--clobber", calls[1])
            self.assertTrue(any("--title" in call and candidate["versionName"] in call for call in calls))

    def test_rolling_channel_keeps_constant_tag_and_asset_name(self):
        commit = build_info("5.1.63", 300, "commit", "a" * 40, 27)
        self.assertEqual(COMMIT_BUILD_TAG, commit["tag"])
        self.assertEqual(COMMIT_BUILD_ASSET, commit["apkName"])
        manifest = make_manifest(commit, Path("assets/app.apk") and self.apk_file(), "SatoriTours/Daily")
        self.assertIn(f"/releases/download/{COMMIT_BUILD_TAG}/{COMMIT_BUILD_ASSET}", manifest["apkUrl"])

    def apk_file(self):
        folder = Path(tempfile.mkdtemp())
        apk = folder / "app.apk"
        apk.write_bytes(b"abc")
        return apk

    def test_new_release_stays_draft_until_both_apk_and_metadata_are_uploaded(self):
        candidate = build_info("5.1.63", 300, "commit", "a" * 40, 27)
        with patch("ci_release.release_by_tag", return_value=None), patch("ci_release.run") as command:
            publish_build("SatoriTours/Daily", Path("dist"), candidate)
            calls = [call.args for call in command.call_args_list]
            self.assertIn("--draft", calls[0])
            self.assertEqual("upload", calls[1][2])
            self.assertIn(str(Path("dist") / "update.json"), calls[1])
            self.assertIn(str(Path("dist") / candidate["apkName"]), calls[1])
            self.assertIn("--draft=false", calls[2])
            self.assertIn("--latest=false", calls[2])

    def test_late_or_repeated_build_cannot_replace_newer_pointer(self):
        self.assertTrue(should_update_pointer(None, {"versionCode": 100}))
        self.assertTrue(should_update_pointer({"versionCode": 100}, {"versionCode": 101}))
        self.assertFalse(should_update_pointer({"versionCode": 101}, {"versionCode": 100}))
        self.assertFalse(should_update_pointer({"versionCode": 100}, {"versionCode": 100}))

    def test_codes_increase_without_digit_four(self):
        codes = [build_version_code(n) for n in range(1, 1000)]
        self.assertGreater(codes[0], 50163)
        self.assertEqual(codes, sorted(set(codes)))
        self.assertTrue(all("4" not in str(code) and code <= 2100000000 for code in codes))

    def test_same_commit_has_same_code_in_both_channels(self):
        stable = build_info("5.1.63", 300, "stable", "a" * 40, 27)
        commit = build_info("5.1.63", 300, "commit", "a" * 40, 27)
        self.assertEqual(stable["versionCode"], commit["versionCode"])
        self.assertEqual("5.1.63", stable["versionName"])
        self.assertTrue(commit["versionName"].startswith("5.1.63-commit."))
        self.assertNotIn("4", commit["versionName"])

    def test_invalid_versions_are_rejected(self):
        for version in ("5.1.64", "5.4.1", "oops"):
            with self.assertRaises(ValueError):
                build_info(version, 300, "stable", "a" * 40, 27)

    def test_manifest_hashes_exact_apk_and_uses_build_specific_url(self):
        with tempfile.TemporaryDirectory() as folder:
            apk = Path(folder) / "app.apk"
            apk.write_bytes(b"abc")
            info = build_info("5.1.63", 300, "commit", "a" * 40, 27)
            manifest = make_manifest(info, apk, "SatoriTours/Daily")
            self.assertEqual("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", manifest["sha256"])
            self.assertEqual(3, manifest["size"])
            self.assertIn("/releases/download/commit-", manifest["apkUrl"])
            self.assertEqual(27, manifest["schemaVersion"])


if __name__ == "__main__":
    unittest.main()
