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

1. **获取版本号** - 从 `pubspec.yaml` 读取当前版本
2. **收集变更** - 获取上一版本 tag 到当前 HEAD 的提交记录
3. **生成日志** - 创建 `docs/versions/changelog_${version}.md`
4. **提交代码** - 执行 `git add .` 和 `git commit`
5. **打版本标签** - 执行 `git tag v${version}`
6. **同步 GitHub** - 使用 `gh api` 将 GitHub `main` 快进到发布提交，并创建远程 tag 引用

> 发布只需要提交代码、打 tag、同步 GitHub main 和 tag。不在本地编译 release 版本，不上传 APK 或其他 release 资产。

## 使用场景

当需要发布新版本时使用，例如：
- "帮我发布最新版本"

## 执行步骤

### 1. 获取版本号

```bash
current_version=$(grep "^version:" pubspec.yaml | sed 's/version: //' | tr -d ' ')
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

### 5. 使用 gh 同步 GitHub

发布时不要使用普通 `git push` 推送分支；使用 GitHub API 快进远程 `main`，并用 GitHub API 创建远程 tag ref。

```bash
release_sha=$(git rev-parse HEAD)

# 快进 GitHub main 到发布提交，force=false 防止覆盖远端新提交
gh api repos/SatoriTours/Daily/git/refs/heads/main \
  -X PATCH \
  -f sha="$release_sha" \
  -F force=false \
  --jq .object.sha

# 创建远程 tag 引用，相当于只把 tag 推到 GitHub
gh api repos/SatoriTours/Daily/git/refs \
  -f ref="refs/tags/v${current_version}" \
  -f sha="$release_sha" \
  --jq .ref

# 验证远程 main 和 tag 都指向发布提交
gh api repos/SatoriTours/Daily/git/ref/heads/main --jq .object.sha
git ls-remote --tags origin "v${current_version}"
```

如果远程 tag 已存在，先验证它是否指向当前发布提交：

```bash
git ls-remote --tags origin "v${current_version}"
```

只有在用户明确要求修正错误 tag 时，才删除并重建远程 tag。不要 force push main/master。

## 注意事项

- 更新日志使用中文，聚焦功能变化
- 按新增/优化/修复分类，不包含测试改进
- 打标签前确认版本号正确
- GitHub 同步使用 `gh api`，不要用普通 `git push` 推送分支
- 只同步 main 和 tag，不编译 release，不上传 APK，不创建/上传 release 资产
- 远程 main 使用 `force=false` 快进，失败时停止并报告远端已有新提交
