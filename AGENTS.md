# Daily Satori Codex 项目规则

本文件是本项目 Codex 的统一规则入口，包含工作流程与工程约束。还需遵守 `docs/` 中的工程规范；如执行方式冲突，以本文件的 token 效率规则为准，但不得降低最终正确性、安全性或必要验证。

## Token 效率优先

所有任务默认以尽可能少的 token、工具输出和往返次数完成。先利用已有上下文、摘要、测试报告和已读取内容，不重复获取相同信息。

### 沟通

- 回复简洁，优先结论、改动、验证结果和阻塞项。
- 中间进度最多 1-2 句，不复述计划、历史或工具完整输出。
- 不粘贴长日志；仅摘录错误根因和关键行。
- 不重复解释用户已经确认的需求。
- 简单任务直接执行，不先写长计划。
- 不为展示过程而输出思维链、逐步推理或大段分析。

### 上下文读取

- 先用 `rg` 精确定位，再读取最小必要行段。
- 不重复读取本会话已经读取且未变化的文件。
- 不一次性读取整个大型目录、长日志、完整 diff 或无关文档。
- 已有摘要、报告或任务简报时，以其为入口，只按具体疑点补读源码。
- 工具输出使用最小合理 `max_output_tokens`，搜索结果用 `head` 或精确模式限制。

### 子代理

- 默认不使用子代理；当前代理直接实现、测试和审查。
- 仅当用户明确要求子代理，或两个任务确实独立且并行能显著节省总成本时使用。
- 子代理统一使用用户指定模型；未指定时使用 `medium` reasoning，不使用 `high`、`xhigh`、`max` 或 `ultra`。
- 同一任务最多一名实现代理和一名审查代理。
- 禁止“实现 -> 审查 -> 修复代理 -> 再审查代理”的循环。审查发现的问题由当前代理集中修复，随后本地验证一次。
- 不让多个代理重复读取同一大 diff、重复运行同一全量测试或重复调查同一问题。

### 计划与审查

- 除非用户要求，或任务跨越多个独立子系统，不创建额外设计文档、计划文档、报告文件或进度台账。
- 已有已批准规格/计划时直接执行，不重新 brainstorm 或重写计划。
- 代码审查每个功能阶段最多一次；优先审查行为风险和回归，不追逐理论上无限细分的竞态。
- 审查发现项一次性汇总并集中修复；只对 Critical/Important 做复核。
- 不因 Minor 建议阻塞阶段完成；记录到最终风险即可。
- 不对修改前已存在且与本任务无关的问题扩大范围。

### 测试与命令

- TDD 时先运行最小聚焦测试确认 RED，再运行同一聚焦测试确认 GREEN。
- 微小修复不运行全量测试；一个功能阶段结束时才运行相关模块全量测试和编译。
- 最终交付前只运行一次必要的完整验证，不重复 `--rerun-tasks`，除非怀疑缓存导致错误结果。
- 不同时运行覆盖范围重复的 Gradle 命令；优先组合为一次调用。
- 不轮询长任务；使用长等待并在完成后读取一次结果。
- 日常验证只执行代码级测试（如单元测试）及必要的编译检查，不启动 Android 模拟器，不安装或启动 App，不执行 UI 测试。
- 仅在发布代码前，或用户明确要求真机测试／UI 测试时，启动 Android 模拟器、安装并运行 App，执行 UI 测试；用户说“真机测试”时默认使用该模拟器，明确指定物理设备时遵从用户指定。
- UI 测试结束后必须关闭 Android 模拟器，测试失败或中断时也要清理，不得保持后台运行。
- 不重复查询已确认的文档。Context7 查询每个问题最多执行 `library` 和 `docs` 各一次。

### 实现范围

- 优先最小可用实现，避免提前支持未要求的扩展点和抽象。
- 复用项目现有模式，不做无关重构。
- 一个文件能清晰完成时不拆成多个文件；只有职责或测试边界明确时才拆分。
- 遇到复杂并发问题，先确定单一所有权和状态机，再实现；禁止靠连续补丁和无限审查循环逼近正确性。
- 连续两次修复仍暴露同类架构问题时，暂停补丁，重新评估边界并一次性重构。

## 文档查询（Context7）

当用户询问库、框架、SDK、API、CLI 或云服务，或实现依赖其当前 API 时，使用 Context7 CLI 获取最新文档：

1. `npx ctx7@latest library <name> "<question>"`
2. 选择官方且最相关的 `/org/project` ID。
3. `npx ctx7@latest docs <libraryId> "<question>"`

每个问题最多 3 个 Context7 命令。不得在查询中包含密钥或敏感信息。网络错误时在沙箱外重试；配额错误时明确告知用户。

以下情况不使用 Context7：业务逻辑调试、普通重构、从零编写脚本、代码审查和一般编程概念。

## 工作区与 Git

- 直接在当前工作区开发，不创建 git worktree。
- 保留用户现有修改和未跟踪文件，不清理、不回退、不覆盖。
- 使用 `apply_patch` 手工编辑文件。
- 不执行破坏性 Git 命令。
- 只提交当前任务相关文件；提交前检查 staged diff。

## 最终交付

最终回复只包含：

- 完成了什么。
- 关键文件或提交。
- 实际运行的验证及结果。
- 尚未验证的真实风险。

不要重复过程，不罗列无关细节，不以可选式追问结尾。

## 文档索引

| 文档 | 用途 |
|------|------|
| [01-coding-standards](./docs/01-coding-standards.md) | 架构约束、代码质量规范 |
| [02-testing](./docs/02-testing.md) | 测试指南 |
| [03-app-features](./docs/03-app-features.md) | 功能模块说明 |
| [04-style-guide](./docs/04-style-guide.md) | 样式系统参考 |
| [05-i18n-guide](./docs/05-i18n-guide.md) | 国际化指南 |
| [06-koin-viewmodel-guide](./docs/06-koin-viewmodel-guide.md) | Koin + ViewModel 最佳实践 |
| [08-remote-news-api](./docs/08-remote-news-api.md) | 远程新闻接口标准 |

## 核心约束

1. **KMP 架构**：Kotlin Multiplatform，共享模块 `shared/` + Android 模块 `app/`
2. **代码质量**：函数 ≤50 行，缩进 ≤3 层，无重复代码
3. **样式系统**：`import com.dailysatori.ui.theme.*`，禁止硬编码颜色/间距/字体
4. **质量检查**：修改后执行 `./gradlew :app:compileDebugKotlin`，确保无编译错误

## 项目结构

```
shared/                     # KMP 共享模块
├── commonMain/kotlin/
│   ├── config/             # 配置常量
│   ├── data/repository/    # 数据仓库
│   └── service/            # 共享服务
└── commonMain/sqldelight/  # 数据库 Schema

app/                        # Android 应用
└── src/main/kotlin/
    └── com/dailysatori/
        ├── core/di/        # 依赖注入 (Koin)
        ├── core/navigation/# 导航
        └── ui/
            ├── feature/    # 功能页面模块
            ├── component/  # 可复用组件
            └── theme/      # 样式系统 (Color, Spacing, Typography)
```

## 禁止事项

- 禁止硬编码颜色/间距/字体
- 禁止日志输出敏感信息
- 禁止修改数据库 Schema 不编写迁移脚本
- 禁止使用 git worktree；后续开发直接在当前工作区进行

## 发版版本号规则

- 发布新版本时，版本号任意段都禁止包含数字 `4`。
- 如果按常规递增得到的版本号包含 `4`，必须继续递增并跳过，直到版本号所有段都不包含 `4`。
- 示例：当前版本 `5.0.3`，用户要求发版时，下一个版本必须是 `5.0.5`，不能使用 `5.0.4`。
- 打 tag、生成 changelog、提交 release 前必须确认 `app/build.gradle.kts` 的 `versionName` 和 tag 都符合该规则。

## 代码校验（每次修改后必须执行）

```bash
# 编译检查 - 检查语法错误和代码问题
./gradlew :app:compileDebugKotlin

# 完整构建
./gradlew :app:assembleDebug
```

**必须确保编译无错误后才能提交代码。**

## 数据库迁移规则

**每次修改 `DailySatori.sq`（新增/修改表或列）时必须同步编写迁移脚本：**

1. 在 `shared/src/commonMain/kotlin/com/dailysatori/config/Config.kt` 中递增 `currentSchemaVersion`
2. 在 `shared/src/commonMain/kotlin/com/dailysatori/service/migration/DatabaseMigration.kt` 中：
   - 在 `runMigrations()` 中添加 `if (currentVersion < N) migrateV(N-1)ToV(N)()`
   - 实现对应的私有方法，使用 `CREATE TABLE IF NOT EXISTS` 或 `ALTER TABLE ... ADD COLUMN`
   - 每个迁移用 try/catch 包裹，通过 logger 记录，不因单条失败中断整体流程
3. 验证迁移：重新安装 App 后不应崩溃

## Android 构建与部署（仅发布前或用户明确要求真机／UI 测试时）

日常只执行代码级测试（如单元测试）及必要的编译检查，不启动模拟器或 App。
发布代码前，或用户明确要求真机／UI 测试时，才启动 Android 模拟器并执行以下部署和 UI 验证。
用户说“真机测试”时默认使用模拟器；明确指定物理设备时使用指定设备。

```bash
# 编译并安装到已连接的设备
./gradlew :app:installDebug

# 启动 App
adb shell am start -n com.dailysatori/.MainActivity
```

**UI 测试结束后必须关闭模拟器；失败或中断时也必须清理，不得保持后台运行。**
