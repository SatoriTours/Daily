# Android Release Action Design

## Goal

Build and publish an installable Android APK when a `v*.*.*` tag points to a commit that belongs to the `android` branch.

## Trigger

The release workflow runs on pushed tags matching `v*.*.*`. It also supports manual `workflow_dispatch` runs for recovery.

Because GitHub tag push events do not directly filter by source branch, the workflow checks whether `GITHUB_SHA` is contained in `origin/android` before building. If the tag does not point to an `android` branch commit, the workflow stops before release creation.

## Build

The workflow uses the Android Gradle project directly, not Flutter. It sets up JDK 21, checks out full history, decodes the signing keystore from GitHub Secrets, and runs:

```bash
./gradlew :app:assembleRelease
```

The expected APK artifact is:

```text
app/build/outputs/apk/release/app-release.apk
```

## Signing

The release APK is signed with existing GitHub Secrets:

- `KEY_JKS`: base64-encoded keystore
- `KEY_ALIAS`: keystore alias
- `KEY_PASSWORD`: key password
- `STORE_PASSWORD`: keystore password

`app/build.gradle.kts` reads signing values from environment variables. The workflow writes `KEY_JKS` to a temporary keystore file and passes its path to Gradle through an environment variable.

## Release Upload

The workflow grants `contents: write` permission and uses GitHub CLI to create or update the release for the tag. It uploads the signed APK with overwrite behavior so reruns can replace a failed or stale artifact.

The release body comes from `docs/versions/changelog_<version>.md` when present. If the changelog file is missing, the workflow uses a short fallback release note instead of failing after a successful build.

## Failure Handling

The workflow fails early when required signing secrets are missing. It also fails before building if the tag commit is not contained in `origin/android`, preventing accidental releases from other branches.

## Verification

Implementation verification should include:

- YAML syntax and workflow structure review
- Gradle release signing configuration compile check
- Local `./gradlew :app:compileDebugKotlin`
- If feasible, `./gradlew :app:assembleRelease` with signing environment variables available
