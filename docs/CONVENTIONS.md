# 项目约束与约定（Daily Satori）

本文件定义项目的通用约束、代码风格、系统架构与功能边界，用于降低回归与沟通成本。提交前请自查是否违反以下规则。

## 技术栈与基础依赖
- Flutter 3.32.x，Dart 3.8.x
- 状态管理与路由：GetX（GetMaterialApp、GetPage、Bindings、Controller + Rx）
- 本地存储：ObjectBox（仓储模式封装）
- 网络与系统：dio、flutter_inappwebview、web_socket_channel、url_launcher、connectivity_plus
- UI 与主题：自定义主题 `AppTheme`、`app/styles/theme`、`components/*`
- AI 能力：openai_dart + 自定义配置（assets/configs/ai_models.yaml、ai_prompts.yaml）
- 其它：share_plus、image_picker、flutter_markdown、permission_handler、archive

## 系统架构（分层与启动流程）
- 分层结构
  - 界面层：`app/modules/*/views`（仅负责展示与交互）
  - 控制层：`app/modules/*/controllers`（GetX Controller，包含状态 Rx、视图行为、生命周期）
  - 绑定层：`app/modules/*/bindings`（注册 Controller 及依赖）
  - 服务层：`app/services/*`（跨模块领域服务，如 AI/备份/网页解析/剪贴板/升级等），集中注册于 `ServiceRegistry`
  - 仓储层：`app/repositories/*`（封装 ObjectBox 查询与聚合，返回 Model 包装类）
  - 模型层：`app/models/*`（与 ObjectBox 实体配套的模型包装与领域对象）
  - 工具与样式：`app/utils/*`、`app/styles/*`、`app/components/*`
- 启动流程
  1) `main.dart` → `initApp()`
  2) `ServiceRegistry.registerAll()` 注册所有服务并按优先级初始化：
     - critical：启动前必须完成（如 Logger、Flutter、Time、Objectbox、Setting、File、Http）
     - high：启动后立即（如 Font、ADBlock、FreeDisk、AIConfig）
     - normal：启动后异步（如 Ai、Backup、Migration、Plugin、Web、Book）
     - low：首帧后延迟（如 AppUpgrade、ShareReceive、ClipboardMonitor）
  3) `GetMaterialApp` 使用 `AppPages.routes`、`AppPages.initial`

## 目录结构约定
- `lib/app/modules/<feature>/{bindings,controllers,views}`：模块化组织，三件套全且命名一致
- `lib/app/services`：每个服务独立文件，导出于 `services.dart`，受 `ServiceRegistry` 管理
- `lib/app/repositories`：采用静态方法风格的仓储类，导出于 `repositories.dart`
- `lib/app/models`：模型与实体包装，集中导出 `models.dart`
- `lib/app/components`：通用 UI 组件复用
- `lib/app/styles`：主题、颜色、字体与尺寸等
- `lib/app/utils`：工具方法与基类（`BaseController` 等）
- 聚合导出：`app_exports.dart` 提供单点导入

## 路由与状态管理约定
- 路由
  - 统一登记于 `app/routes/app_pages.dart`，常量在 `app_routes.dart`（part）
  - 页面创建必须绑定对应 Binding，禁止在视图中 `Get.put` 业务 Controller
- Controller
  - 命名以 `<Feature>Controller`，继承 `BaseController`
  - 使用 Rx（如 `.obs`）管理 UI 状态；列表用 `Obx` 驱动
  - 生命周期：在 `onInit` 做监听/数据加载；在 `onClose` 释放资源
  - 跨页面共享引用：优先从上层 Controller 获取共享引用（如 `ArticlesController.getRef`）

## 代码风格与规范
- Dart/Flutter
  - 文件与目录：snake_case；类与枚举：PascalCase；方法/变量：camelCase；常量：SCREAMING_SNAKE_CASE
  - import 顺序：dart/flutter → 第三方 → 项目内（聚合导出优先）
  - 禁止在视图中写复杂业务逻辑，放入 Controller/Service
  - UI 与数据解耦：视图只调用 Controller 的公开方法和状态
  - 日志：使用 `logger`（集中于 `logger_service.dart`）；禁止输出敏感信息（API Token 等）
  - 异常：仓储与服务层捕获后转换为业务语义，必要时向上抛出；UI 通过 `UIUtils/DialogUtils` 反馈
  - 时间：统一使用 UTC 存储（见 `DateTimeUtils`），展示时本地化
  - 资源释放：Controller 中必须正确 dispose 各种 `Controller/FocusNode/ScrollController`
- 约定工具
  - 统一通过 `app_exports.dart` 导入常用类型/服务/仓储
  - 公用弹窗/提示：`DialogUtils`、`UIUtils`
  - 模板渲染：`template_expressions`（见 `AiService._renderTemplate`）

## 功能清单（按模块/服务）
- 首页（Home）
  - 底部导航：文章、日记、读书、设置
- 文章（Articles、ArticleDetail）
  - 列表：分页滚动（首尾向前/向后加载）、搜索、标签筛选、收藏筛选、按日期筛选
  - 统计：`ArticleRepository.getDailyArticleCounts`
  - 详情：截图分享、图片管理、AI 生成 Markdown、依赖列表共享引用刷新
- 日记（Diary）
  - 编辑器组件 `DiaryEditor`，供读书页快速记录复用
- 读书（Books）
  - 书籍管理、观点列表、随机推荐、按书籍过滤
  - 固定 FAB：打开 `DiaryEditor`，根据是否有观点预填模板（强约束，见下）
- 备份与还原（Backup/Restore & Settings）
  - 本地备份目录设置、归档/解档（archive）
  - 恢复后图片路径自动修复（强约束，见下）
- 设置（Settings）
  - 基本配置、AI 相关地址/令牌、插件地址、Web 服务口令等（见 `SettingService.defaultSettings`）
- 插件中心（Plugin Center）
- 分享（ShareDialog / ShareReceiveService）
  - 接收系统分享与应用内分享
- AI 能力（AiService + AIConfigService）
  - 翻译、摘要（长/短）、HTML → Markdown
  - 模型/地址/令牌按功能维度可覆盖（assets 配置 + 设置）
- Web 内容与解析
  - `WebService`、`WebpageParserService`、`ADBlockService`、内置网站资源 `assets/website`
- 其它
  - 应用升级检查：`AppUpgradeService`
  - 剪贴板监控：`ClipboardMonitorService`
  - 磁盘清理：`FreeDiskService`（定时清理）

## 服务注册与生命周期约束
- 新增服务需实现 `AppService`，并在 `ServiceRegistry.registerAll()` 注册，指定合理优先级
- 关键服务异常不得吞没：`critical` 阶段初始化失败会中断启动
- `low` 优先级服务由首帧后触发，避免阻塞首屏

## 数据访问与仓储约定
- 仓储类均为静态方法风格（如 `ArticleRepository.where` / `find` / `update`）
- 查询必须通过仓储，禁止在 UI/Controller 层直接访问 ObjectBox Box
- 分页策略：
  - 列表分页通过锚点 ID 与方向标记实现（参考 `ArticlesController._loadAdjacentArticles`）
  - 统一 pageSize 与排序规则（按 `id` 倒序）
- 删除需清理关联（如文章删除需清空 tags/images/screenshots 再删除实体）

## 时间与本地化约定
- 持久化时间统一存储为 UTC；展示由 `DateTimeUtils.formatDateTimeToLocal` 本地化
- `DateTimeUtils.nowToString()` 仅用于日志与非持久化场景

## 错误处理与日志
- 统一使用 `logger` 输出；禁止记录敏感字段（Token、口令）
- UI 提示通过 `UIUtils.showSuccess/showError/showLoading`；确认对话统一 `DialogUtils.showConfirm`

## 性能与内存
- 避免在 `build` 中执行重计算；长任务放入 Service/Repository 层
- 列表滚动加载需防抖/去重；注意 `ScrollController` 边界判定
- 定时清理：`FreeDiskService`（每 15 分钟触发，见 `init_app.dart`）

## 安全与隐私
- API Token、口令等存储于 `SettingRepository`，默认值见 `SettingService.defaultSettings`
- 禁止在日志/异常栈中输出 Token/口令
- 插件与 Web 服务地址需可配置，默认使用可信源

## 提交流程与 DoD（Definition of Done）
- 改动涉及：
  - 视图/交互：需遵循本文交互约束，保持关键 UX 不回归
  - 服务/仓储：更新/新增应补充最小测试或自检说明
  - 文档：如改变行为或约束，需同步更新本文件
- 质量闸门：本地通过 `flutter analyze`；确保无新增警告

---

## 读书页（BooksView）
- 必须始终显示“添加感悟”悬浮按钮（FAB）。
  - 位置：右下角，`FloatingActionButtonLocation.endFloat`
  - 图标：`Icons.edit_note`
  - tooltip：`添加感悟`
- FAB 点击行为：
  - 若当前存在观点：
    - 预填模板包含：观点标题、来源书籍（含作者，若有）以及深链占位 `[](app://books/viewpoint/<id>)`
  - 若无观点：
    - 预填 `读书感悟：` 的空白模板
  - 打开组件：`DiaryEditor`
- 禁止在“无观点时隐藏 FAB”或移除上述点击行为。

## 图片路径恢复
- 从备份恢复后，必须自动修复数据库中图片的本地路径（文章封面、文章图片、日记图片）。
- 运行时渲染前也应调用路径解析 `FileService.i.resolveLocalMediaPath` 以增强兼容性。

## 变更管理
- 修改 `BooksView` 或相关控制器时，必须保证上述读书页行为不变。
- 如需临时移除或修改，请先在此文件更新约束，并在 PR 描述中说明原因与回滚计划。
