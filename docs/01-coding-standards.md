# Daily Satori 编码规范

> 所有 AI 工具生成的代码必须遵循本规范。

## 技术栈

| 类别 | 技术 |
|------|------|
| 框架 | Kotlin Multiplatform (KMP) |
| UI | Jetpack Compose (Material 3) |
| 状态管理 | ViewModel + StateFlow |
| 依赖注入 | Koin |
| 数据库 | SQLDelight |
| AI | AiService (OpenAI + Anthropic 兼容) |

## 项目架构

| 层级 | 路径 | 职责 |
|------|------|------|
| 界面层 | `app/.../ui/feature/*/` | Composable 页面 + Screen |
| 控制层 | `app/.../ui/feature/*/` | ViewModel (StateFlow 驱动) |
| 共享服务 | `shared/.../service/*/` | 跨模块业务逻辑 |
| 数据层 | `shared/.../data/repository/` | 数据访问 (SQLDelight) |
| 数据库 | `shared/.../sqldelight/` | Schema + Query 定义 |

## Koin + ViewModel 架构（核心）

> 详见 [Koin + ViewModel 最佳实践](./06-koin-viewmodel-guide.md)

### 必须遵守

- ViewModel 通过 Koin constructor injection 获取依赖
- 使用 `MutableStateFlow<StateData>` + `StateFlow` 管理状态
- UI 层通过 `collectAsState()` 订阅状态
- 异步操作使用 `viewModelScope.launch(Dispatchers.IO)`
- Repository 提供 Flow（响应式）和 Sync（工具调用）双版本方法

### 严禁

- 在 Composable 中直接调用 Repository（应通过 ViewModel）
- 在 ViewModel 的 StateFlow 中存储可变对象
- 使用全局单例模式获取依赖（应通过 Koin DI）
- 在 `build()` 方法中执行副作用

## 样式系统

> 详见 [样式指南](./04-style-guide.md)

```kotlin
// 唯一导入方式
import com.dailysatori.ui.theme.*

// 使用主题常量
MaterialTheme.colorScheme.primary
Spacing.m
Radius.l
AppTypography.bodyMedium
Height.button

// 禁止硬编码
Color(0xFF5E8BFF)    // 禁止
16.dp                // 禁止（使用 Spacing.m）
fontSize = 14.sp     // 禁止（使用 AppTypography）
```

## 代码质量

### 强制约束

| 约束 | 限制 |
|------|------|
| 函数长度 | ≤ 50 行 |
| 缩进层数 | ≤ 3 层 |
| 编译检查 | `./gradlew :app:compileDebugKotlin` 无错误 |

### 命名约定

| 类型 | 风格 | 示例 |
|------|------|------|
| 文件 | PascalCase | `DiaryViewModel.kt` |
| 类 | PascalCase | `DiaryViewModel` |
| Repository | PascalCase + Repository | `DiaryRepository` |
| Service | PascalCase + Service | `AiService` |
| 方法/变量 | camelCase | `sendMessage()` |
| 常量 | UPPER_SNAKE_CASE | `MAX_TOOL_CALL_ROUNDS` |
| Composable | PascalCase | `AiChatScreen` |

### 日志规范

```kotlin
val log = Logger.withTag("TagName")
log.d { "调试信息" }
log.i { "信息日志" }
log.w(exception) { "警告" }
log.e(exception) { "错误" }
```

## 数据访问

```kotlin
// Repository 注入
class DiaryViewModel(
    private val diaryRepo: DiaryRepository,
) : ViewModel()

// Flow 响应式读取
fun getAll(): Flow<List<Diary>> = q.selectAllDiaries().asFlow().mapToList(Dispatchers.IO)

// 同步读取（供 MCP 工具调用）
fun getAllSync(): List<Diary> = q.selectAllDiaries().executeAsList()

// 时间戳
val now = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
```

## Repository 模式

每个 Repository 遵循统一模式：

- 构造函数接收 `DailySatoriDatabase`
- 通过 `private val q get() = db.dailySatoriQueries` 访问查询
- 提供 Flow 版本（UI 使用）和 Sync 版本（MCP 工具使用）
- 所有写操作填充 `created_at` / `updated_at`

## 安全与隐私

- 敏感信息（API Key）存储于 `ai_config` 表的 `api_token` 字段
- 禁止在日志中输出 Token/口令
- API 响应中的敏感信息不持久化

## 检查清单

- [ ] ViewModel 通过 Koin constructor injection 获取依赖
- [ ] 使用 `MutableStateFlow` + `StateFlow` 管理状态
- [ ] UI 通过 `collectAsState()` 订阅
- [ ] 导入 `com.dailysatori.ui.theme.*`
- [ ] 无硬编码颜色/间距/字体
- [ ] 函数 ≤ 50 行，缩进 ≤ 3 层
- [ ] `./gradlew :app:compileDebugKotlin` 无错误
- [ ] 修改数据库 Schema 编写了迁移脚本
- [ ] 无重复代码
- [ ] 无日志输出敏感信息
