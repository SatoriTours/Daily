# Main Branch Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Prepare the Android/KMP project for `main` as the primary development branch, then merge `android` into `main`.

**Architecture:** Update documentation and release automation on the `android` branch first, commit those changes, verify them, and then merge the fully prepared branch into `main`. Keep release publishing tag-based, but require release tags to point to commits contained in `origin/main`.

**Tech Stack:** Git, GitHub Actions, Gradle, Android Gradle Plugin, Kotlin Multiplatform, Compose Multiplatform.

---

## File Structure

- Modify `README.md`: replace outdated Flutter project documentation with current Android/KMP documentation.
- Modify `.github/workflows/flutter-release.yml`: change release branch guard from `origin/android` to `origin/main`.
- Create this plan at `docs/superpowers/plans/2026-05-03-main-branch-migration.md`.

### Task 1: Update README

**Files:**
- Modify: `README.md`

- [ ] **Step 1: Replace Flutter-specific badges and setup with Android/KMP content**

Update `README.md` so it describes Kotlin Multiplatform, Android, Compose, SQLDelight, Koin, Gradle commands, and current project structure.

- [ ] **Step 2: Verify README no longer references Flutter setup commands**

Run: search `README.md` for `flutter pub get`, `flutter run`, `Dart SDK`, `Riverpod`, and `ObjectBox`.

Expected: no matches in project setup or tech stack content.

### Task 2: Restrict Release Workflow To Main Tags

**Files:**
- Modify: `.github/workflows/flutter-release.yml`

- [ ] **Step 1: Change branch guard**

Change the guard step to fetch `origin main` and verify `$GITHUB_SHA` is contained in `origin/main`.

- [ ] **Step 2: Verify workflow whitespace**

Run: `git diff --check .github/workflows/flutter-release.yml README.md docs/superpowers/plans/2026-05-03-main-branch-migration.md`

Expected: no output and exit code 0.

### Task 3: Verify, Commit, And Merge

**Files:**
- Verify: `README.md`
- Verify: `.github/workflows/flutter-release.yml`
- Verify: `docs/superpowers/plans/2026-05-03-main-branch-migration.md`

- [ ] **Step 1: Run compile verification**

Run: `./gradlew :app:compileDebugKotlin`

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 2: Commit on android**

Run: `git add README.md .github/workflows/flutter-release.yml docs/superpowers/plans/2026-05-03-main-branch-migration.md && git commit -m "docs: update project docs for main branch"`

Expected: commit succeeds.

- [ ] **Step 3: Merge android into main**

Run: `git checkout main && git merge android`

Expected: merge succeeds without unrelated changes being reverted.

- [ ] **Step 4: Verify merged main**

Run: `./gradlew :app:compileDebugKotlin`

Expected: `BUILD SUCCESSFUL` on `main`.
