import 'package:flutter/widgets.dart';

import 'package:daily_satori/app/utils/clipboard_utils.dart';
import 'package:daily_satori/app/services/logger_service.dart';

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

  /// 初始化并注册生命周期监听
  Future<void> init() async {
    if (_initialized) return;
    _initialized = true;
    logger.i('[初始化服务] ClipboardMonitorService');

    // 注册应用生命周期监听
    WidgetsBinding.instance.addObserver(this);

    // 首帧后检查一次（避免在构建期间弹窗）
    WidgetsBinding.instance.addPostFrameCallback((_) {
      _checkClipboardSafe(source: 'postFrame');
    });
  }

  @override
  Future<void> didChangeAppLifecycleState(AppLifecycleState state) async {
    if (state == AppLifecycleState.resumed) {
      // 延迟一小段时间再检查，确保系统剪贴板已同步
      await Future.delayed(const Duration(milliseconds: 300));
      await _checkClipboardSafe(source: 'resumed');
    }
  }

  /// 公开方法：延迟检查剪切板
  ///
  /// 用于在分享对话框关闭后调用，延迟一小段时间再检查
  /// 以便检测用户是否复制了新的URL
  Future<void> checkAfterDelay({int delayMs = 500}) async {
    await Future.delayed(Duration(milliseconds: delayMs));
    await _checkClipboardSafe(source: 'afterShareDialog');
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
