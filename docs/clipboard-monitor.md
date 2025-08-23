# 全局剪贴板监听方案

本文档说明为什么移除各页面 Controller 中的 `checkClipboard()`，并在应用层实现统一的剪贴板检测与导航逻辑。

## 设计目标
- 去重：避免在多个页面重复实现“检查剪贴板并打开分享弹窗”的逻辑。
- 解耦：页面只关注各自业务，剪贴板属于应用层关注点，由服务统一处理。
- 不打扰：仅在合适的生命周期时机触发，且避免二次弹窗。

## 实现概要
- 新增 `ClipboardMonitorService`（`lib/app/services/clipboard_monitor_service.dart`）
  - 在应用首帧渲染后检查一次剪贴板。
  - 在应用从后台恢复（resumed）时检查一次剪贴板。
  - 通过 `ClipboardUtils` 的去重机制（基于 `_lastProcessedText`）避免对同一 URL 反复弹窗。
  - 当当前路由已在分享页（`Routes.shareDialog`）时，跳过检查。
- 在 `init_app.dart` 的低优先级服务初始化中注册：
  - `ClipboardMonitorService.i.init()`
- 移除页面 Controller 中的 `checkClipboard()` 及其调用（如 `ArticlesController`、`DiaryController` 等）。

## 相关文件
- `lib/app/services/clipboard_monitor_service.dart`：全局监听服务
- `lib/app/utils/clipboard_utils.dart`：剪贴板工具方法（抽取 URL、确认弹窗、导航封装等）
- `lib/init_app.dart`：在 `_initLowPriorityServices()` 中初始化监听服务
- `lib/app/services/services.dart`：服务聚合导出，已新增导出该服务

## 使用说明
- 页面无需再调用任何“检查剪贴板”的方法。
- 如需在程序化处理后避免重复弹窗，可调用：
  - `ClipboardUtils.markUrlProcessed(url)` 标记该 URL 已处理。
- 若业务需要自定义校验或关闭确认弹窗，可在 `ClipboardUtils.checkForUrl(...)` 基础上封装新的流程。

## 与 ShareReceiveService 的关系
- `ShareReceiveService` 处理来自系统级“分享至应用”的入口（原生 MethodChannel）。
- `ClipboardMonitorService` 处理用户复制链接后的“被动检测”。
- 二者互不干扰；当前路由为分享弹窗时，`ClipboardMonitorService` 会跳过检查，避免冲突。

## 常见问题
- 首次进入应用没有弹窗？
  - 弹窗会在首帧渲染后触发检查，避免在构建阶段打断 UI；若剪贴板为空或 URL 已处理过，不会弹窗。
- 开发环境是否会清空剪贴板？
  - `ClipboardUtils` 默认仅在生产环境清空剪贴板（通过 `AppInfoUtils.isProduction` 判断）。
