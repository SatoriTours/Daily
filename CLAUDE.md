# Daily Satori 项目指南

> 本文档是 AI 编码助手的入口指南，详细规范请查阅 `docs/` 目录。

## 文档索引

| 文档 | 用途 |
|------|------|
| [01-coding-standards](./docs/01-coding-standards.md) | 架构约束、代码质量规范 |
| [02-testing](./docs/02-testing.md) | 测试指南 |
| [03-app-features](./docs/03-app-features.md) | 功能模块说明 |
| [04-style-guide](./docs/04-style-guide.md) | 样式系统参考 |
| [05-i18n-guide](./docs/05-i18n-guide.md) | 国际化指南 |
| [06-koin-viewmodel-guide](./docs/06-koin-viewmodel-guide.md) | Koin + ViewModel 最佳实践 |

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

## Android 构建与部署（每次修改代码后自动执行）

```bash
# 编译并安装到已连接的设备
JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:installDebug

# 启动 App
adb shell am start -n com.dailysatori/.MainActivity
```

**修改代码后必须执行以上步骤，确保变更生效到设备上。**
