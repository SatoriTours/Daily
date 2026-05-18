# Daily Satori 项目指南

> 本文档是 AI 编码助手的入口指南，详细规范请查阅 `docs/` 目录。

## 文档索引

| 文档 | 用途 |
|------|------|
| [01-coding-standards](../docs/01-coding-standards.md) | 架构约束、代码质量规范 |
| [02-testing](../docs/02-testing.md) | 测试指南 |
| [03-app-features](../docs/03-app-features.md) | 功能模块说明 |
| [04-style-guide](../docs/04-style-guide.md) | 样式系统参考 |
| [05-i18n-guide](../docs/05-i18n-guide.md) | 国际化指南 |
| [06-koin-viewmodel-guide](../docs/06-koin-viewmodel-guide.md) | Koin + ViewModel 最佳实践 |

## 核心约束

1. **KMP 架构**：Kotlin Multiplatform，共享模块 `shared/` + Android 模块 `app/`
2. **代码质量**：函数 ≤50 行，缩进 ≤3 层，无重复代码
3. **样式系统**：`import com.dailysatori.ui.theme.*`，禁止硬编码颜色/间距/字体
4. **质量检查**：修改后执行 `./gradlew :app:compileDebugKotlin --no-configuration-cache`

## 项目结构

\`\`\`
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
\`\`\`

## 禁止事项

- 禁止硬编码颜色/间距/字体
- 禁止日志输出敏感信息
- 禁止修改数据库 Schema 不编写迁移脚本

## 代码校验（每次修改后必须执行）

```bash
# 编译检查 - 检查语法错误和代码问题
./gradlew :app:compileDebugKotlin --no-configuration-cache

# 单元测试
./gradlew :app:testDebugUnitTest :shared:testDebugUnitTest --no-configuration-cache

# 设备安装（需要连接 Android 设备时）
JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:installDebug --no-configuration-cache
```

**必须确保 Gradle 编译和相关测试通过后才能提交代码。**
