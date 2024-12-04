part of 'articles_controller.dart';

extension PartClipboard on ArticlesController {
  /// 检查剪切板内容,如果包含链接则提示保存
  Future<void> checkClipboardText() async {
    logger.i('检查剪切板内容');

    final text = await getClipboardText();
    logger.i('剪切板内容: $text');

    final url = getUrlFromText(text);
    if (!_isValidNewUrl(url)) return;

    await _showSaveUrlDialog(url);
  }

  bool _isValidNewUrl(String url) {
    return url.startsWith('http') && url != _clipboardText;
  }

  Future<void> _showSaveUrlDialog(String url) async {
    final message = '获取到剪切板链接:\n${getSubstring(url, length: 30, suffix: '...')}\n\n请确认是否保存?';

    await showConfirmationDialog(
      '是否保存',
      message,
      onConfirmed: () => _handleSaveUrl(url),
      onCanceled: () => _clipboardText = url,
    );
  }

  Future<void> _handleSaveUrl(String url) async {
    if (isProduction) {
      await setClipboardText('');
    }

    Get.toNamed(Routes.SHARE_DIALOG, arguments: {'shareURL': url});
    _clipboardText = url;
  }
}
