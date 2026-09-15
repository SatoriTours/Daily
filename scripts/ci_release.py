"""GitHub APK builds: shared version allocation, manifests and publication."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess
import os

# 滚动更新渠道：所有 commit 构建都替换同一个 release 上的同一组资产。
COMMIT_BUILD_TAG = "commit-build"
COMMIT_BUILD_ASSET = "daily-satori-commit-latest.apk"


def build_version_code(commit_count):
    if commit_count < 1:
        raise ValueError("A complete nonempty main history is required")
    value = 100000 + commit_count
    digits = "012356789"
    result = ""
    while value:
        result = digits[value % 9] + result
        value //= 9
    code = int(result)
    if code > 2100000000:
        raise ValueError("Android versionCode limit exceeded")
    return code


def build_info(base_version, commit_count, channel, commit_sha, schema_version):
    if not re.fullmatch(r"[0-9]+\.[0-9]+\.[0-9]+", base_version) or "4" in base_version:
        raise ValueError("versionName must have three numeric segments without digit 4")
    if channel not in ("stable", "commit") or not re.fullmatch(r"[0-9a-f]{40}", commit_sha):
        raise ValueError("Invalid channel or commit")
    code = build_version_code(commit_count)
    return {
        "versionName": base_version if channel == "stable" else f"{base_version}-commit.{code}",
        "versionCode": code,
        "channel": channel,
        "commitSha": commit_sha,
        "schemaVersion": schema_version,
        "tag": f"v{base_version}" if channel == "stable" else COMMIT_BUILD_TAG,
        "apkName": f"daily-satori-{channel}-{code}.apk" if channel == "stable" else COMMIT_BUILD_ASSET,
    }


def make_manifest(info, apk, repository):
    if not re.fullmatch(r"[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+", repository):
        raise ValueError("Invalid repository")
    return dict(info, sha256=hashlib.sha256(apk.read_bytes()).hexdigest(), size=apk.stat().st_size,
                apkUrl=f"https://github.com/{repository}/releases/download/{info['tag']}/{info['apkName']}")


def run(*args):
    return subprocess.check_output(args, text=True).strip()


def prepare(channel, output):
    if run("git", "rev-parse", "--is-shallow-repository") != "false":
        raise ValueError("Checkout must use fetch-depth: 0")
    if run("git", "rev-parse", "HEAD") not in run("git", "rev-list", "--first-parent", "origin/main").splitlines():
        raise ValueError("Build commit must belong to main's first-parent history")
    gradle = Path("app/build.gradle.kts").read_text()
    base = re.search(r'^\s*versionName = "([^"]+)"', gradle, re.M).group(1)
    config = Path("shared/src/commonMain/kotlin/com/dailysatori/config/Config.kt").read_text()
    schema = int(re.search(r"currentSchemaVersion = (\d+)L", config).group(1))
    info = build_info(base, int(run("git", "rev-list", "--first-parent", "--count", "HEAD")),
                      channel, run("git", "rev-parse", "HEAD"), schema)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(info, indent=2) + "\n")
    if "GITHUB_OUTPUT" in os.environ:
        with open(os.environ["GITHUB_OUTPUT"], "a") as stream:
            for key, value in info.items():
                stream.write(f"{key}={value}\n")


def release_by_tag(repository, tag):
    return github_release(repository, f"tags/{tag}")


def github_release(repository, route):
    result = subprocess.run(["gh", "api", f"repos/{repository}/releases/{route}"], capture_output=True, text=True)
    if result.returncode == 0:
        return json.loads(result.stdout)
    if "HTTP 404" in result.stderr:
        return None
    raise RuntimeError(result.stderr)


def download_manifest(tag, folder):
    target = folder / "published-update.json"
    run("gh", "release", "download", tag, "--pattern", "update.json", "--output", str(target), "--clobber")
    return json.loads(target.read_text())


def publish_build(repository, folder, manifest):
    if manifest["channel"] == "commit":
        return publish_commit_build(repository, folder, manifest)
    tag = manifest["tag"]
    existing = release_by_tag(repository, tag)
    if existing and not existing["draft"]:
        previous = download_manifest(tag, folder)
        if previous["commitSha"] != manifest["commitSha"] or previous["channel"] != manifest["channel"]:
            raise ValueError("A published release cannot be reassigned to another build")
        return previous
    if not existing:
        command = ["gh", "release", "create", tag, "--target", manifest["commitSha"], "--draft",
                   "--title", manifest["versionName"], "--notes-file", str(folder / "notes.md")]
        run(*command)
    run("gh", "release", "upload", tag, str(folder / manifest["apkName"]), str(folder / "update.json"), "--clobber")
    latest = github_release(repository, "latest") if manifest["channel"] == "stable" else None
    mark_latest = manifest["channel"] == "stable" and should_mark_latest(latest and latest["tag_name"], manifest["versionName"])
    run("gh", "release", "edit", tag, "--draft=false", "--latest=" + str(mark_latest).lower())
    return manifest


def publish_commit_build(repository, folder, manifest):
    """Rolling channel: every build replaces the assets of the single commit-build release."""
    tag = manifest["tag"]
    existing = release_by_tag(repository, tag)
    previous = download_manifest(tag, folder) if existing and not existing["draft"] else None
    if not should_update_pointer(previous, manifest):
        return previous or manifest
    if existing:
        # Keep the rolling tag on the latest build so the source links match the APK.
        run("gh", "api", "-X", "PATCH", f"repos/{repository}/git/refs/tags/{tag}",
            "-f", f"sha={manifest['commitSha']}", "-F", "force=true")
    else:
        run("gh", "release", "create", tag, "--target", manifest["commitSha"], "--draft", "--prerelease",
            "--title", manifest["versionName"], "--notes", "提交构建版更新渠道")
    notes = (f"[下载最新 APK]({manifest.get('apkUrl') or f'https://github.com/{repository}/releases/download/{tag}/{manifest['apkName']}'})\n\n"
             f"版本：{manifest['versionName']}\n\n提交：`{manifest['commitSha']}`\n\n"
             f"此页面持续替换为最新提交构建，不保留历史版本。\n")
    folder.mkdir(parents=True, exist_ok=True)
    (folder / "channel-notes.md").write_text(notes)
    run("gh", "release", "upload", tag, str(folder / manifest["apkName"]), str(folder / "update.json"), "--clobber")
    run("gh", "release", "edit", tag, "--draft=false", "--prerelease", "--latest=false",
        "--title", manifest["versionName"], "--notes-file", str(folder / "channel-notes.md"))
    return manifest


def should_mark_latest(latest_tag, candidate_version):
    if latest_tag is None:
        return True
    latest = latest_tag.removeprefix("v")
    if not re.fullmatch(r"\d+\.\d+\.\d+", latest):
        return False
    return tuple(map(int, candidate_version.split("."))) > tuple(map(int, latest.split(".")))


def should_update_pointer(previous, candidate):
    return previous is None or candidate["versionCode"] > previous["versionCode"]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("command", choices=["prepare", "package", "publish"])
    parser.add_argument("--channel", choices=["stable", "commit"])
    parser.add_argument("--folder", type=Path, default=Path("dist"))
    parser.add_argument("--apk", type=Path)
    args = parser.parse_args()
    info_path = args.folder / "build-info.json"
    if args.command == "prepare":
        prepare(args.channel, info_path)
        return
    repository = os.environ.get("GITHUB_REPOSITORY", "SatoriTours/Daily")
    if args.command == "package":
        info = json.loads(info_path.read_text())
        manifest = make_manifest(info, args.apk, repository)
        (args.folder / info["apkName"]).write_bytes(args.apk.read_bytes())
        (args.folder / "update.json").write_text(json.dumps(manifest, indent=2) + "\n")
        changelog = Path(f"docs/versions/changelog_{info['versionName']}.md")
        notes = changelog.read_text() if info["channel"] == "stable" and changelog.exists() else f"提交：{info['commitSha']}\n"
        (args.folder / "notes.md").write_text(notes)
        return
    manifest = json.loads((args.folder / "update.json").read_text())
    publish_build(repository, args.folder, manifest)


if __name__ == "__main__":
    main()
