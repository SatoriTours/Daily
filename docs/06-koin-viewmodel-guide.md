# Koin + ViewModel 最佳实践指南

> 本文档提供 Koin 依赖注入 + Jetpack ViewModel 的最佳实践和代码规范。

## 架构概览

```
UI Layer (Composable)
  └── collectAsState()       # 订阅 StateFlow
       ↓
ViewModel Layer
  └── MutableStateFlow       # 管理状态
  └── viewModelScope.launch  # 异步操作
       ↓
Service Layer (shared/)
  └── 业务逻辑
       ↓
Repository Layer (shared/)
  └── SQLDelight queries
```

## DI 注册

### SharedModule.kt — 共享模块（shared/）

```kotlin
// shared/.../di/SharedModule.kt
val sharedModule: Module = module {
    // Repositories — 统一模式
    single { ArticleRepository(get()) }
    single { DiaryRepository(get()) }
    single { MemoryRepository(get()) }

    // Services — 通过 get() 自动解析依赖
    single { AiService(get()) }
    single { McpAgentService(get(), get(), get(), get(), get(), get(), get()) }
    single { MemoryExtractService(get(), get(), get()) }
}
```

### ViewModelModule.kt — Android 模块（app/）

```kotlin
// app/.../core/di/ViewModelModule.kt
val viewModelModule: Module = module {
    viewModel {
        ArticlesViewModel(
            articleRepo = get<ArticleRepository>(),
            tagRepo = get<TagRepository>(),
        )
    }
    viewModel { params ->
        ArticleDetailViewModel(
            articleId = params.get<Long>(),
            articleRepo = get<ArticleRepository>(),
            tagRepo = get<TagRepository>(),
        )
    }
}
```

### DI 规则

| 层级 | 注册位置 | 注册方式 |
|------|----------|----------|
| Repository | SharedModule | `single { XxxRepository(get()) }` |
| Service | SharedModule | `single { XxxService(get(), ...) }` |
| ViewModel | ViewModelModule | `viewModel { XxxViewModel(...) }` |

## ViewModel 模式

### 标准 ViewModel

```kotlin
// app/.../ui/feature/example/ExampleViewModel.kt
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ExampleState(
    val items: List<Item> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
)

class ExampleViewModel(
    private val repo: ExampleRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(ExampleState())
    val state: StateFlow<ExampleState> = _state.asStateFlow()

    fun loadItems() {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isLoading = true) }
            try {
                val items = repo.getAllSync()
                _state.update { it.copy(items = items, isLoading = false) }
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message, isLoading = false) }
            }
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }
}
```

### ViewModel 规则

- 状态使用 `data class`，所有字段有默认值
- `MutableStateFlow` 私有，`StateFlow` 公开
- `viewModelScope.launch(Dispatchers.IO)` 执行异步 I/O
- `_state.update { it.copy(...) }` 原子更新状态
- 不在 `init {}` 中执行耗时操作（用 `Future.microtask` 或 lazy init）

## Composable 使用 ViewModel

```kotlin
// app/.../ui/feature/example/ExampleScreen.kt
@Composable
fun ExampleScreen() {
    val viewModel: ExampleViewModel = koinViewModel()
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadItems()
    }

    when {
        state.isLoading -> LoadingIndicator()
        state.error != null -> ErrorMessage(state.error!!)
        else -> ItemsList(state.items)
    }
}
```

### Composable 规则

- 使用 `koinViewModel()` 获取 ViewModel
- 使用 `collectAsState()` 订阅状态
- `LaunchedEffect` 执行一次性初始化
- 不直接在 Composable 中调用 Repository/Service

## Repository 模式

```kotlin
// shared/.../data/repository/ExampleRepository.kt
class ExampleRepository(private val db: DailySatoriDatabase) {
    private val q get() = db.dailySatoriQueries

    // 响应式（供 Flow 使用）
    fun getAll(): Flow<List<Example>> =
        q.selectAll().asFlow().mapToList(Dispatchers.IO)

    // 同步（供 MCP 工具调用）
    fun getAllSync(): List<Example> =
        q.selectAll().executeAsList()

    fun getById(id: Long): Example? =
        q.selectById(id).executeAsOneOrNull()

    fun insert(...) {
        val now = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        q.insertExample(..., now, now)
    }

    fun delete(id: Long) = q.deleteExample(id)
}
```

### Repository 规则

- 构造函数接收 `DailySatoriDatabase`
- `private val q get() = db.dailySatoriQueries` 访问查询
- 提供 Flow 和 Sync 双版本读取方法
- 写操作自动填充 `created_at`/`updated_at`
- 不包含业务逻辑，纯数据访问
- `executeAsOneOrNull()` 用于可能返回空结果的查询
- `executeAsOne()` 用于 count 等必定有结果的查询
- `executeAsList()` 用于列表查询

## 依赖解析规则

### Koin `get()` 解析

```kotlin
// 按类型自动匹配（同一个接口只有一个实现时）
single { ArticleRepository(get()) }  // get() = DailySatoriDatabase

// 显式指定类型（同一接口有多个实现时）
single { ConcreteImpl() } bind Interface::class

// 多个参数按位置匹配
single { McpAgentService(get(), get(), get()) }
// 依次解析: AiService, AiConfigService, ArticleRepository
```

---

## 常见模式与反模式

### ✅ 正确：ViewModel 管理异步

```kotlin
fun sendMessage(content: String) {
    _state.update { it.copy(isProcessing = true) }
    viewModelScope.launch(Dispatchers.IO) {
        val result = service.processQuery(content)
        _state.update { it.copy(
            messages = it.messages + result.toMessage(),
            isProcessing = false,
        ) }
    }
}
```

### ❌ 错误：在 Composable 中直接调用 Repository

```kotlin
// 禁止
@Composable
fun BadExample() {
    val repo = koinInject<ArticleRepository>()
    val articles = remember { repo.getAllSync() }  // 阻塞主线程！
}
```

### ✅ 正确：通过 Koin 获取非 ViewModel 依赖

```kotlin
// 在 Composable 中获取 Service/Repository（仅在必要时）
@Composable
fun GoodExample() {
    val service = koinInject<MemoryExtractService>()
    // 在 LaunchedEffect 中调用
    LaunchedEffect(Unit) {
        service.rebuildAll(...)
    }
}
```

### ❌ 错误：在 StateFlow 中存可变对象

```kotlin
// 禁止
data class BadState(
    val items: MutableList<Item> = mutableListOf()  // 可变！
)
```

### ✅ 正确：不可变状态

```kotlin
data class GoodState(
    val items: List<Item> = emptyList()  // 不可变
)
```

---

## 检查清单

- [ ] ViewModel 通过 Koin constructor injection
- [ ] 状态是 `data class`，所有字段有默认值
- [ ] `MutableStateFlow` 私有，`StateFlow` 公开
- [ ] UI 通过 `collectAsState()` 订阅
- [ ] 不直接调用 Repository/Service（通过 ViewModel）
- [ ] Repository 提供 Flow + Sync 双版本
- [ ] 写操作自动填充时间戳
- [ ] 无循环依赖
