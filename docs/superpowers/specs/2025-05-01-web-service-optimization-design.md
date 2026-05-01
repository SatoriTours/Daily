# Web 服务优化 设计文档

## 概述

优化 Daily Satori 内嵌 Web 服务的引擎、认证、静态资源服务和 Web UI，
修复点击开启后崩溃的 bug，支持 Token 认证为未来桌面客户端做准备，全面重新设计 Web 管理后台界面。

## 问题分析

### 问题 1：Netty 引擎崩溃
- **根因**：Ktor Netty 引擎在 Android 上尝试加载 Linux 原生 epoll 库，Android 不支持导致 `UnsatisfiedLinkError`
- **修复**：将 `ktor-server-netty-jvm` 替换为 `ktor-server-cio-jvm`（纯 Kotlin，无原生依赖）

### 问题 2：缺少静态文件路由
- 当前 `WebServerService` 只有 `/ping` 和 `/api/v2/*` 路由，没有提供 `admin.html` 和 `/website/*` 静态资源的路由
- 需要添加 `static` 路由和 SPA fallback 路由

### 问题 3：缺少 Token 认证
- 当前仅有 Session/Cookie 认证，未来桌面客户端需要 Token 方式
- 需要增加固定 Token 生成、显示、验证机制

### 问题 4：Web UI 桌面体验差
- 布局窄小（移动端优先），桌面端浪费空间
- CDN 资源依赖外网（离线不可用）
- 缺少键盘快捷键、批量操作、数据可视化等桌面端特性

## 设计

### 1. 引擎替换

**依赖变更**：
- 移除：`ktor-server-netty-jvm`
- 新增：`ktor-server-cio-jvm`

**代码变更**：
```kotlin
// 旧
import io.ktor.server.netty.Netty
server = embeddedServer(Netty, port = port) { ... }

// 新
import io.ktor.server.cio.CIO
server = embeddedServer(CIO, port = port, host = "0.0.0.0") { ... }
```

CIO 引擎 API 与 Netty 完全兼容，路由代码无需修改。

### 2. 静态资源服务

新增路由：
```
GET /                    → 返回 admin.html（SPA 入口）
GET /website/{path...}   → 返回 assets/website/ 下的静态文件（CSS, JS）
GET /website/            → 404（目录列表禁止）
```

实现方式：使用 Ktor `static` plugin 或手动 `respondFile`，从 Android assets 读取。

**注意**：CDN 资源（Bootstrap, Vue, Marked）改为本地打包到 assets，确保离线可用。

### 3. Token 认证

**Token 生命周期**：
- 首次启动自动生成 32 位随机 Token
- 存储在 `SettingRepository`，key = `web_server_token`
- Settings 页面显示 Token，提供复制和刷新按钮
- 刷新 Token 后旧 Token 立即失效

**认证中间件**：
请求优先级：
1. 检查 Cookie `session_id` → 查 `SessionRepository`
2. 检查 Header `Authorization: Bearer <token>` → 对比 `SettingRepository` 中存储的 token
3. 均不匹配 → 401 Unauthorized

**API 结构**：
```
POST /api/v2/auth/login     → Cookie 登录（密码）
POST /api/v2/auth/logout    → Cookie 登出
GET  /api/v2/auth/status    → 查询当前认证状态（Cookie 或 Token）
```

所有 `/api/v2/*` 路由都经过认证检查（除 `/api/v2/auth/login` 外）。

### 4. Web UI 重新设计

**设计理念**：专业后台管理面板，桌面端优先，宽屏布局。

**布局结构**：
```
┌──────────────────────────────────────────────┐
│ 顶栏: Logo + 导航标签 + 搜索(Ctrl+K) + 设置  │
├──────────┬───────────────────────────────────┤
│ 侧边栏   │  主内容区                          │
│ 仪表盘   │  ┌─────────┬─────────┬─────────┐  │
│ 文章     │  │ 统计卡片 │ 统计卡片 │ 统计卡片 │  │
│ 日记     │  ├─────────┴─────────┴─────────┤  │
│ 书籍     │  │       周报 / 最近活动        │  │
│ 标签     │  └─────────────────────────────┘  │
│          │                                    │
│ 底部:    │  数据表格（排序/筛选/分页）        │
│ 设置     │                                    │
└──────────┴───────────────────────────────────┘
```

**功能清单**：

| 功能 | 说明 |
|------|------|
| 宽屏双栏布局 | 侧边栏 + 主内容区，最大宽度 1400px |
| 深色/浅色主题 | 跟随系统偏好，可手动切换，持久化 |
| 键盘快捷键 | `Ctrl+K` 搜索, `←→` 翻页, `N` 新建, `Esc` 关闭弹窗 |
| 统计图表 | 仪表盘显示文章/日记/书籍趋势图 |
| 数据表格 | 可排序表头、列筛选、行选择、批量操作 |
| 批量操作 | 多选后批量删除、批量导出 |
| 图片查看器 | 键盘左右翻页、缩放、全屏 |
| 面包屑导航 | 层级导航，快速跳转 |
| 响应式设计 | 桌面 > 平板 > 手机三档自适应 |
| 离线可用 | 所有资源本地打包，无 CDN 依赖 |

**技术栈**：
- Vue 3 (CDN → 本地打包)
- 纯 CSS (移除 Bootstrap 依赖，减小体积)
- Chart.js (本地打包，用于统计图表)
- Marked.js (本地打包，Markdown 渲染)

**文件结构**：
```
assets/website/
├── admin.html          # SPA 入口（重写）
├── css/
│   ├── base.css        # CSS 变量 + 重置（重写）
│   ├── layout.css      # 布局（重写）
│   ├── components.css  # 组件样式（重写）
│   └── pages.css       # 页面样式（重写）
├── js/
│   ├── app.js          # Vue 应用（重写）
│   └── lib/            # 第三方库
│       ├── vue.min.js
│       ├── marked.min.js
│       └── chart.min.js
└── img/                # 图标等
```

### 5. 设置页面改进

- Web 服务开关增加加载状态指示
- 显示服务地址 `http://<device_ip>:8888`
- 显示 Token 和复制/刷新按钮
- 启动失败时显示错误信息

### 6. SettingsViewModel 改动

```kotlin
fun toggleWebServer() {
    viewModelScope.launch(Dispatchers.IO) {
        _state.update { it.copy(isTogglingWebServer = true, webServerError = null) }
        try {
            if (_state.value.webServerRunning) {
                webServerService.stop()
                _state.update { it.copy(webServerRunning = false) }
            } else {
                webServerService.start()
                _state.update { it.copy(webServerRunning = true) }
            }
        } catch (e: Exception) {
            _state.update { it.copy(webServerError = e.message) }
        }
        _state.update { it.copy(isTogglingWebServer = false) }
    }
}
```

将 `toggleWebServer()` 移到 `Dispatchers.IO` 上异步执行，捕获异常并显示错误。

### 7. 数据库变更

无需变更，Token 使用已有的 `SettingRepository` 存储，key = `web_server_token`。

Schema version 保持不变。

## 文件变更清单

| 文件 | 操作 | 说明 |
|------|------|------|
| `gradle/libs.versions.toml` | 修改 | Netty → CIO 依赖 |
| `app/build.gradle.kts` | 修改 | 依赖替换 |
| `app/src/main/kotlin/.../WebServerService.kt` | 重写 | 引擎 + 静态资源 + Token 认证 |
| `app/src/main/kotlin/.../SettingsViewModel.kt` | 修改 | 异步启停 + 错误处理 + Token 管理 |
| `app/src/main/kotlin/.../SettingsScreen.kt` | 修改 | Web 服务信息展示 |
| `app/src/main/assets/website/admin.html` | 重写 | 全新 SPA |
| `app/src/main/assets/website/css/*.css` | 重写 | 全新样式系统 |
| `app/src/main/assets/website/js/app.js` | 重写 | 全新 Vue 应用 |
| `app/src/main/assets/website/js/lib/*.js` | 新增 | 本地第三方库 |

## 测试验证

1. App 编译通过：`./gradlew :app:compileDebugKotlin`
2. App 安装运行：`./gradlew :app:installDebug`
3. 开启 Web 服务 → 不崩溃，显示运行中
4. 浏览器访问 `http://<ip>:8888` → 显示登录页
5. 登录后 → 仪表盘、文章、日记、书籍管理功能正常
6. 使用 Token 访问 API → 返回正确数据
7. 关闭 Web 服务 → 浏览器连接断开
8. 再次开启 → 正常工作
