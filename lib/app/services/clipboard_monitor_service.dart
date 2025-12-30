import 'package:daily_satori/app/navigation/app_navigation.dart';
import 'dart:async';

import 'package:flutter/widgets.dart';
import 'package:flutter/services.dart';

import 'package:daily_satori/app/services/logger_service.dart';
import 'package:daily_satori/app/routes/app_routes.dart';
import 'package:daily_satori/app/utils/dialog_utils.dart';
import 'package:daily_satori/app/utils/string_utils.dart';

/// 全局剪贴板监听服务
///
/// 目标：把“检查剪贴板并导航到分享对话框”的逻辑从各个页面的 Controller 中抽离，
/// 统一在应用层按生命周期时机触发，避免重复代码和遗漏。
///
/// 行为：
/// - 应用启动后首帧渲染完成时检查一次剪贴板；
/// - 应用从后台恢复到前台（resumed）时检查一次剪贴板；
/// - 通过内存去重，避免同一 URL 重复弹窗。
class ClipboardMonitorService with WidgetsBindingObserver {
  ClipboardMonitorService._();
  static final ClipboardMonitorService _instance = ClipboardMonitorService._();
  static ClipboardMonitorService get i => _instance;

  bool _initialized = false;
  Timer? _pendingTimer;
  int _checkToken = 0;

  /// 规则(1)：用于避免重复弹出同一 URL 的内存记录
  String _lastUrlInMemory = '';

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

  /// 允许业务侧标记“已处理/已弹出”的 URL（例如 share dialog 打开时）。
  ///
  /// 规则(1)：若剪贴板检测到的 URL 与此一致，则不再弹出。
  void markUrlProcessed(String url) {
    final trimmed = url.trim();
    if (trimmed.isEmpty) return;
    _lastUrlInMemory = trimmed;
  }

  void _scheduleCheck({required String source, int delayMs = 0}) {
    _checkToken++;
    final token = _checkToken;

    _pendingTimer?.cancel();
    _pendingTimer = Timer(Duration(milliseconds: delayMs), () {
      if (token != _checkToken) return;
      _checkClipboardSafe(source: source);
    });
  }

  /// 安全触发检查（带异常保护与日志）
  Future<void> _checkClipboardSafe({required String source}) async {
    try {
      logger.d('[ClipboardMonitorService] 触发检查: source=$source');

      // // 规则(2)：如果当前在 share dialog 页面，直接跳过
      // // 已移除 GetX，Riverpod架构下不需要此检查


      final clipboardText = await _getClipboardText();
      if (clipboardText.isEmpty) return;

      // 规则(1)：内存去重
      if (clipboardText == _lastUrlInMemory) return;

      // 规则(3)：剪贴板内容必须是“完整 URL”
      if (!_isFullUrl(clipboardText)) return;

      // 规则(1)：记录到内存，避免重复弹窗
      _lastUrlInMemory = clipboardText;

      await _confirmAndNavigateToShareDialog(clipboardText);
    } catch (e, s) {
      logger.e('ClipboardMonitorService 检查失败: $e', stackTrace: s);
    }
  }

  Future<void> _confirmAndNavigateToShareDialog(String url) async {
    final shortUrl = StringUtils.getSubstring(url, length: 30, suffix: '...');
    final message = '获取到剪切板链接:\n$shortUrl\n\n请确认是否处理?';

    await DialogUtils.showConfirm(
      title: '发现URL',
      message: message,
      onConfirm: () {
        logger.i('[ClipboardMonitorService] 用户确认处理URL，跳转到分享页');
        AppNavigation.toNamed(Routes.shareDialog, arguments: {'shareURL': url, 'fromClipboard': true});
      },
      onCancel: () {
        logger.i('[ClipboardMonitorService] 用户取消处理URL');
      },
    );
  }

  Future<String> _getClipboardText() async {
    final data = await Clipboard.getData(Clipboard.kTextPlain);
    return (data?.text ?? '').trim();
  }

  bool _isFullUrl(String text) {
    final uri = Uri.tryParse(text);
    if (uri == null) return false;
    if (!(uri.isScheme('http') || uri.isScheme('https'))) return false;
    return uri.host.isNotEmpty;
  }
}
