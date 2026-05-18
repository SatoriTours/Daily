---
name: release-version
description: 发布 Daily Satori 新版本，生成更新日志并执行 git commit 和 git tag 命令
license: MIT
compatibility: opencode
metadata:
  audience: maintainers
  workflow: github
---

## 功能说明

为 Daily Satori 项目发布新版本，完整执行以下步骤：

1. **获取版本号** - 从 `app/build.gradle.kts` 的 `versionName` 读取当前版本
2. **收集变更** - 获取上一版本 tag 到当前 HEAD 的提交记录
3. **生成日志** - 创建 `docs/versions/changelog_${version}.md`
4. **提交代码** - 执行 `git add .` 和 `git commit`
5. **打版本标签** - 执行 `git tag v${version}`
6. **同步 GitHub** - 推送 `main` 和版本 tag，触发 GitHub Actions 构建并发布 APK

> 发布只需要提交代码、打 tag、同步 GitHub main 和 tag。不在本地编译 release 版本；APK 由 GitHub Actions 构建并上传到 Release。

## 使用场景

当需要发布新版本时使用，例如：
- "帮我发布最新版本"

## 执行步骤

### 1. 获取版本号

```bash
current_version=$(grep "versionName" app/build.gradle.kts | sed -E 's/.*versionName = "([^"]+)".*/\1/' | tr -d ' ')
```

### 2. 获取上一版本 tag

```bash
previous_tag=$(git tag --sort=-v:refname | head -2 | tail -1)
```

### 3. 生成更新日志

根据提交记录整理，按以下格式分类：

```markdown

- 新增 xx 功能
- 优化 xx 功能
- 修复 xx 问题
```

**分类规则**：
- **新增** - 新功能、新特性
- **优化** - 性能改进、代码重构、用户体验提升
- **修复** - Bug 修复、问题解决

保存到 `docs/versions/changelog_${version}.md`

### 4. 执行 Git 命令

```bash

# 提交所有变更
git add .
git commit -m "Release v${current_version}"

# 打标签
git tag v${current_version}

```

### 5. 同步 GitHub

推送 `main` 和版本 tag。远程 tag 会触发 `.github/workflows/android-release.yml`，由 GitHub Actions 构建签名 APK 并上传到 GitHub Release。

```bash
git push origin main "v${current_version}"

# 使用 gh 验证 GitHub release/tag 已存在；如 Actions 仍在运行，可稍后重试
gh release view "v${current_version}" --repo SatoriTours/Daily --json tagName,targetCommitish,url
```

如果远程 release/tag 已存在，先验证它：

```bash
gh release view "v${current_version}" --repo SatoriTours/Daily --json tagName,targetCommitish,url
```

只有在用户明确要求修正错误 tag 时，才删除并重建远程 tag。不要 force push main/master。

## 注意事项

- 更新日志使用中文，聚焦功能变化
- 按新增/优化/修复分类，不包含测试改进
- 打标签前确认版本号正确
- tag 必须匹配 `app/build.gradle.kts` 的 `versionName`
- tag 指向的 commit 必须属于 `main` 分支历史
- 不使用 `gh api` 手工操作 Git refs
- 使用 `git push origin main "v${current_version}"` 触发远程发布流程
- 不在本地编译 release，不在本地上传 APK
