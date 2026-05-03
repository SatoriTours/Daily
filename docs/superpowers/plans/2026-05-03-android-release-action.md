# Android Release Action Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a signed installable Android release APK from `v*.*.*` tags that point to commits on the `android` branch, then upload it to the matching GitHub Release.

**Architecture:** Replace the obsolete Flutter release workflow with a Gradle-based Android workflow. Add release signing configuration to `app/build.gradle.kts` that reads CI-provided environment variables without hardcoding secrets.

**Tech Stack:** GitHub Actions, GitHub CLI, Gradle Kotlin DSL, Android Gradle Plugin, Kotlin Multiplatform Android app.

---

## File Structure

- Modify `.github/workflows/flutter-release.yml`: keep the existing filename to minimize repo churn, but replace its content with an Android Gradle release workflow.
- Modify `app/build.gradle.kts`: add a `release` signing config backed by environment variables and attach it to the release build type.
- No new scripts are required; all workflow logic remains in YAML steps.

### Task 1: Add Gradle Release Signing Configuration

**Files:**
- Modify: `app/build.gradle.kts`

- [ ] **Step 1: Add environment-backed signing variables**

Add these values inside the existing `android { ... }` block, before `buildTypes { ... }`:

```kotlin
    val releaseStoreFile = System.getenv("KEYSTORE_FILE")
    val releaseStorePassword = System.getenv("STORE_PASSWORD")
    val releaseKeyAlias = System.getenv("KEY_ALIAS")
    val releaseKeyPassword = System.getenv("KEY_PASSWORD")
```

- [ ] **Step 2: Add the release signing config**

Still inside `android { ... }`, add this block before `buildTypes { ... }`:

```kotlin
    signingConfigs {
        create("release") {
            storeFile = releaseStoreFile?.let { file(it) }
            storePassword = releaseStorePassword
            keyAlias = releaseKeyAlias
            keyPassword = releaseKeyPassword
        }
    }
```

- [ ] **Step 3: Attach signing to release builds only when configured**

Update the existing release build type to include:

```kotlin
            if (!releaseStoreFile.isNullOrBlank()) {
                signingConfig = signingConfigs.getByName("release")
            }
```

The full release build type should be:

```kotlin
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            if (!releaseStoreFile.isNullOrBlank()) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
```

- [ ] **Step 4: Verify debug compilation still works**

Run: `./gradlew :app:compileDebugKotlin`

Expected: `BUILD SUCCESSFUL`.

### Task 2: Replace Flutter Workflow With Android Release Workflow

**Files:**
- Modify: `.github/workflows/flutter-release.yml`

- [ ] **Step 1: Replace the workflow header and permissions**

Use this top-level workflow configuration:

```yaml
name: Build And Release Android APK

on:
  push:
    tags:
      - 'v*.*.*'
  workflow_dispatch:

permissions:
  contents: write

jobs:
  build:
    runs-on: ubuntu-latest
```

- [ ] **Step 2: Add checkout, branch guard, JDK setup, and Gradle setup**

Add these steps under `jobs.build.steps`:

```yaml
      - name: Checkout code
        uses: actions/checkout@v4.2.2
        with:
          fetch-depth: 0

      - name: Ensure tag belongs to android branch
        run: |
          git fetch origin android --depth=1
          if ! git merge-base --is-ancestor "$GITHUB_SHA" "origin/android"; then
            echo "Tag commit $GITHUB_SHA is not contained in origin/android"
            exit 1
          fi

      - name: Set up JDK 21
        uses: actions/setup-java@v4.5.0
        with:
          distribution: 'temurin'
          java-version: '21'

      - name: Set up Gradle
        uses: gradle/actions/setup-gradle@v4
```

- [ ] **Step 3: Add signing secret validation and APK build**

Add these steps after Gradle setup:

```yaml
      - name: Validate signing secrets
        env:
          KEY_JKS: ${{ secrets.KEY_JKS }}
          KEY_ALIAS: ${{ secrets.KEY_ALIAS }}
          KEY_PASSWORD: ${{ secrets.KEY_PASSWORD }}
          STORE_PASSWORD: ${{ secrets.STORE_PASSWORD }}
        run: |
          test -n "$KEY_JKS" || { echo "KEY_JKS secret is missing"; exit 1; }
          test -n "$KEY_ALIAS" || { echo "KEY_ALIAS secret is missing"; exit 1; }
          test -n "$KEY_PASSWORD" || { echo "KEY_PASSWORD secret is missing"; exit 1; }
          test -n "$STORE_PASSWORD" || { echo "STORE_PASSWORD secret is missing"; exit 1; }

      - name: Build signed release APK
        env:
          KEYSTORE_FILE: ${{ runner.temp }}/release-key.jks
          KEY_JKS: ${{ secrets.KEY_JKS }}
          KEY_ALIAS: ${{ secrets.KEY_ALIAS }}
          KEY_PASSWORD: ${{ secrets.KEY_PASSWORD }}
          STORE_PASSWORD: ${{ secrets.STORE_PASSWORD }}
        run: |
          printf '%s' "$KEY_JKS" | base64 --decode > "$KEYSTORE_FILE"
          ./gradlew :app:assembleRelease
```

- [ ] **Step 4: Add version, changelog, and release upload steps**

Add these final steps:

```yaml
      - name: Get current version
        id: version
        run: |
          VERSION=${GITHUB_REF_NAME#v}
          echo "version=$VERSION" >> "$GITHUB_OUTPUT"
          echo "tag=$GITHUB_REF_NAME" >> "$GITHUB_OUTPUT"

      - name: Read changelog
        id: changelog
        run: |
          VERSION=${{ steps.version.outputs.version }}
          CHANGELOG_PATH="docs/versions/changelog_${VERSION}.md"
          if [ -f "$CHANGELOG_PATH" ]; then
            {
              echo "body<<EOF"
              cat "$CHANGELOG_PATH"
              echo "EOF"
            } >> "$GITHUB_OUTPUT"
          else
            echo "body=Release ${{ steps.version.outputs.tag }}" >> "$GITHUB_OUTPUT"
          fi

      - name: Create or update GitHub Release
        env:
          GH_TOKEN: ${{ github.token }}
          RELEASE_TAG: ${{ steps.version.outputs.tag }}
          RELEASE_NAME: Release ${{ steps.version.outputs.tag }}
          RELEASE_BODY: ${{ steps.changelog.outputs.body }}
          APK_PATH: app/build/outputs/apk/release/app-release.apk
        run: |
          if gh release view "$RELEASE_TAG" >/dev/null 2>&1; then
            gh release edit "$RELEASE_TAG" --title "$RELEASE_NAME" --notes "$RELEASE_BODY" --latest
          else
            gh release create "$RELEASE_TAG" --title "$RELEASE_NAME" --notes "$RELEASE_BODY" --latest
          fi
          gh release upload "$RELEASE_TAG" "$APK_PATH#daily-satori-${RELEASE_TAG}.apk" --clobber
```

- [ ] **Step 5: Verify workflow file can be parsed structurally**

Run: `git diff --check .github/workflows/flutter-release.yml`

Expected: no output and exit code 0.

### Task 3: Final Verification

**Files:**
- Verify: `.github/workflows/flutter-release.yml`
- Verify: `app/build.gradle.kts`

- [ ] **Step 1: Run required compile check**

Run: `./gradlew :app:compileDebugKotlin`

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 2: Check release task availability**

Run: `./gradlew :app:tasks --all`

Expected: output includes `assembleRelease`.

- [ ] **Step 3: Review final diff**

Run: `git diff -- .github/workflows/flutter-release.yml app/build.gradle.kts docs/superpowers/plans/2026-05-03-android-release-action.md`

Expected: diff only includes the Android release workflow, signing config, and this plan.
