import 'package:flutter/services.dart';
import 'package:get/get.dart';
import 'package:daily_satori/app_exports.dart';

/// 剪贴板工具类
class ClipboardUtils {
  // 私有构造函数，防止实例化
  ClipboardUtils._();

  // 用于记录上次处理过的剪贴板内容
  static String _lastProcessedText = '';

  /// 获取剪贴板文本
  static Future<String> getText() async {
    final data = await Clipboard.getData(Clipboard.kTextPlain);
    return data?.text ?? '';
  }

  /// 设置剪贴板文本
  static Future<void> setText(String text) async {
    await Clipboard.setData(ClipboardData(text: text));
  }

  /// 从文本中提取URL
  static String extractUrl(String text) {
    if (text.isEmpty) return '';

    // 简单的URL提取逻辑，可以根据需要进一步完善
    final urlRegExp = RegExp(
      r'https?://(www\.)?[-a-zA-Z0-9@:%._\+~#=]{1,256}\.[a-zA-Z0-9()]{1,6}\b([-a-zA-Z0-9()@:%_\+.~#?&//=]*)',
      caseSensitive: false,
    );

    final match = urlRegExp.firstMatch(text);
    return match != null ? match.group(0) ?? '' : '';
  }

  /// 检查剪贴板内容并处理URL
  /// [onUrlDetected] 检测到URL时的回调函数
  /// [clearClipboard] 处理URL后是否清空剪贴板
  /// [urlValidator] 自定义URL验证函数，返回true表示URL有效
  static Future<void> checkForUrl({
    required Function(String url) onUrlDetected,
    bool clearClipboard = true,
    bool Function(String url)? urlValidator,
    bool showConfirmation = true,
  }) async {
    logger.i('检查剪切板URL');

    final text = await getText();
    final url = extractUrl(text);

    // 验证URL
    bool isValidUrl = url.isNotEmpty && url.startsWith('http');
    if (urlValidator != null) {
      isValidUrl = isValidUrl && urlValidator(url);
    }

    // 检查URL是否有效且未处理过
    if (isValidUrl && url != _lastProcessedText) {
      if (showConfirmation) {
        // 显示确认对话框
        await _showUrlConfirmation(
          url,
          onConfirmed: () async {
            // 处理确认后的逻辑
            if (clearClipboard && AppInfoUtils.isProduction) {
              await setText('');
            }
            onUrlDetected(url);
            _lastProcessedText = url;
          },
          onCanceled: () {
            // 用户取消，但记录URL以避免重复提示
            _lastProcessedText = url;
          },
        );
      } else {
        // 不显示确认直接处理
        if (clearClipboard && AppInfoUtils.isProduction) {
          await setText('');
        }
        onUrlDetected(url);
        _lastProcessedText = url;
      }
    }
  }

  /// 检查剪贴板并导航到分享对话框
  /// 这是一个高级方法，直接处理URL导航逻辑，避免控制器重复实现相同代码
  /// [clearClipboard] 处理URL后是否清空剪贴板
  /// [urlValidator] 自定义URL验证函数，返回true表示URL有效
  static Future<void> checkAndNavigateToShareDialog({
    bool clearClipboard = true,
    bool Function(String url)? urlValidator,
    bool showConfirmation = true,
  }) async {
    await checkForUrl(
      onUrlDetected: (url) {
        // 统一的URL处理逻辑：导航到分享对话框
        Get.toNamed(Routes.shareDialog, arguments: {'shareURL': url});
      },
      clearClipboard: clearClipboard,
      urlValidator: urlValidator,
      showConfirmation: showConfirmation,
    );
  }

  /// 显示URL确认对话框
  static Future<void> _showUrlConfirmation(
    String url, {
    required Function() onConfirmed,
    required Function() onCanceled,
  }) async {
    final message = '获取到剪切板链接:\n${StringUtils.getSubstring(url, length: 30, suffix: '...')}\n\n请确认是否处理?';

    await DialogUtils.showConfirm(title: '发现URL', message: message, onConfirm: onConfirmed, onCancel: onCanceled);
  }
}
