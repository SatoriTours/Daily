import 'dart:async';

import 'package:flutter/widgets.dart';
import 'package:flutter/services.dart';

import 'package:daily_satori/app/routes/app_navigation.dart';
import 'package:daily_satori/app/routes/app_router.dart';
import 'package:daily_satori/app/routes/app_routes.dart';
import 'package:daily_satori/app/services/logger_service.dart';
import 'package:daily_satori/app/services/service_base.dart';
import 'package:daily_satori/app/utils/dialog_utils.dart';
import 'package:daily_satori/app/utils/string_utils.dart';

/// 剪贴板监听服务
///
/// 监听剪贴板中的 URL，自动弹出确认对话框并导航到分享页面。
/// - 应用启动后首帧渲染完成时检查一次
/// - 应用从后台恢复时检查一次
/// - 内存去重，避免同一 URL 重复弹窗
/// - 在 share dialog 页面时不弹窗
class ClipboardMonitorService extends AppService with WidgetsBindingObserver {
  ClipboardMonitorService._();
  static final ClipboardMonitorService i = ClipboardMonitorService._();

  @override
  ServicePriority get priority => ServicePriority.low;

  String _lastProcessedUrl = '';
  Timer? _checkTimer;

  @override
  Future<void> init() async {
    WidgetsBinding.instance.addObserver(this);
    WidgetsBinding.instance.addPostFrameCallback((_) => _scheduleCheck(200));
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) {
      _scheduleCheck(600);
    }
  }

  /// 延迟检查剪贴板（分享对话框关闭后调用）
  void checkAfterDelay({int delayMs = 500}) => _scheduleCheck(delayMs);

  /// 标记 URL 为已处理，避免重复弹窗
  void markUrlProcessed(String url) {
    if (url.trim().isNotEmpty) _lastProcessedUrl = url.trim();
  }

  void _scheduleCheck(int delayMs) {
    _checkTimer?.cancel();
    _checkTimer = Timer(Duration(milliseconds: delayMs), _checkClipboard);
  }

  Future<void> _checkClipboard() async {
    try {
      // 在 share dialog 页面时不检查
      if (_isOnShareDialogPage()) return;

      final url = await _getClipboardUrl();
      if (url == null || url == _lastProcessedUrl) return;

      _lastProcessedUrl = url;
      await _showConfirmDialog(url);
    } catch (e, s) {
      logger.e('[ClipboardMonitorService] 检查失败', error: e, stackTrace: s);
    }
  }

  bool _isOnShareDialogPage() {
    final location = appRouter.routerDelegate.currentConfiguration.uri.path;
    return location == Routes.shareDialog;
  }

  Future<String?> _getClipboardUrl() async {
    final data = await Clipboard.getData(Clipboard.kTextPlain);
    final text = data?.text?.trim() ?? '';
    if (text.isEmpty) return null;

    final uri = Uri.tryParse(text);
    if (uri == null) return null;
    if (!uri.isScheme('http') && !uri.isScheme('https')) return null;
    if (uri.host.isEmpty) return null;

    return text;
  }

  Future<void> _showConfirmDialog(String url) async {
    final shortUrl = StringUtils.getSubstring(url, length: 30, suffix: '...');

    await DialogUtils.showConfirm(
      title: '发现URL',
      message: '获取到剪切板链接:\n$shortUrl\n\n请确认是否处理?',
      onConfirm: () {
        logger.i('[ClipboardMonitorService] 用户确认处理URL');
        AppNavigation.toNamed(Routes.shareDialog, arguments: {'shareURL': url, 'fromClipboard': true});
      },
    );
  }
}
