import 'dart:async';

import 'package:flutter/widgets.dart';
import 'package:get/get.dart';

import 'package:daily_satori/app/utils/clipboard_utils.dart';
import 'package:daily_satori/app/services/logger_service.dart';
import 'package:daily_satori/app/routes/app_pages.dart';

/// 全局剪贴板监听服务
///
/// 目标：把“检查剪贴板并导航到分享对话框”的逻辑从各个页面的 Controller 中抽离，
/// 统一在应用层按生命周期时机触发，避免重复代码和遗漏。
///
/// 行为：
/// - 应用启动后首帧渲染完成时检查一次剪贴板；
/// - 应用从后台恢复到前台（resumed）时检查一次剪贴板；
/// - 通过 ClipboardUtils 自带的去重机制，避免同一 URL 重复弹窗。
class ClipboardMonitorService with WidgetsBindingObserver {
  ClipboardMonitorService._();
  static final ClipboardMonitorService _instance = ClipboardMonitorService._();
  static ClipboardMonitorService get i => _instance;

  bool _initialized = false;
  int _suspendCount = 0;
  Timer? _pendingTimer;
  int _checkToken = 0;

  /// 初始化并注册生命周期监听
  Future<void> init() async {
    if (_initialized) return;
    _initialized = true;
    logger.i('[初始化服务] ClipboardMonitorService');

    // 注册应用生命周期监听
    WidgetsBinding.instance.addObserver(this);

    // 首帧后检查一次（避免在构建期间弹窗）
    WidgetsBinding.instance.addPostFrameCallback((_) {
      _scheduleCheck(source: 'postFrame', delayMs: 200);
    });
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) {
      // 延迟一小段时间再检查，确保系统剪贴板已同步；
      // 同时给页面恢复/刷新（可能会弹 loading）留出时间，避免与其它弹窗冲突。
      _scheduleCheck(source: 'resumed', delayMs: 600);
    }
  }

  /// 公开方法：延迟检查剪切板
  ///
  /// 用于在分享对话框关闭后调用，延迟一小段时间再检查
  /// 以便检测用户是否复制了新的URL
  Future<void> checkAfterDelay({int delayMs = 500}) async {
    _scheduleCheck(source: 'afterShareDialog', delayMs: delayMs);
  }

  /// 暂停剪贴板弹窗/导航（可重入）。
  ///
  /// 适用场景：系统分享面板、应用内分享页等不希望被剪贴板弹窗打断的流程。
  void suspend({String reason = 'manual'}) {
    _suspendCount++;
    logger.d('[ClipboardMonitorService] suspend: reason=$reason, count=$_suspendCount');
  }

  /// 恢复剪贴板弹窗/导航（与 [suspend] 配对）。
  void resume({String reason = 'manual'}) {
    if (_suspendCount <= 0) return;
    _suspendCount--;
    logger.d('[ClipboardMonitorService] resume: reason=$reason, count=$_suspendCount');
  }

  void _scheduleCheck({required String source, int delayMs = 0, int attempt = 0}) {
    _checkToken++;
    final token = _checkToken;

    _pendingTimer?.cancel();
    _pendingTimer = Timer(Duration(milliseconds: delayMs), () {
      if (token != _checkToken) return;
      _checkClipboardWithDeferral(source: source, attempt: attempt);
    });
  }

  Future<void> _checkClipboardWithDeferral({required String source, required int attempt}) async {
    if (_shouldDeferClipboardPrompt()) {
      if (attempt >= 6) {
        logger.d('[ClipboardMonitorService] 跳过剪贴板检查（多次延后仍不可弹窗）: $source');
        return;
      }
      _scheduleCheck(source: source, delayMs: 500, attempt: attempt + 1);
      return;
    }

    await _checkClipboardSafe(source: source);
  }

  bool _shouldDeferClipboardPrompt() {
    if (_suspendCount > 0) return true;

    // 在分享页内绝不触发（用户明确要求：分享对话框期间不要弹任何剪贴板对话框）。
    if (Get.currentRoute == Routes.shareDialog) return true;

    // 若当前已有对话框/底部弹层/全局 loading 等，延后执行，避免弹窗互相打断。
    if (Get.isDialogOpen == true) return true;
    if (Get.isBottomSheetOpen == true) return true;

    return false;
  }

  /// 安全触发检查（带异常保护与日志）
  Future<void> _checkClipboardSafe({required String source}) async {
    try {
      logger.i('ClipboardMonitorService: 触发检查 ($source)');
      await ClipboardUtils.checkAndNavigateToShareDialog();
    } catch (e, s) {
      logger.e('ClipboardMonitorService 检查失败: $e', stackTrace: s);
    }
  }
}
