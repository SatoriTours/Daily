part of 'articles_controller.dart';

extension PartClipboard on ArticlesController {
  // 处理剪切板
  void checkClipboardText() {
    logger.i("[checkClipboardText] 检查剪切板里面是否包含http开头的链接");
    getClipboardText().then((String url) {
      logger.i("[checkClipboardText] 读取剪切板内容 $url");
      if (url.startsWith('http') && url != _clipboardText) {
        showConfirmationDialog(
          '是否保存',
          '获取到剪切板链接:\n${getSubstring(url, length: 30, suffix: '...')}\n\n请确认是否保存?',
          onConfirmed: () async {
            await setClipboardText('');
            Get.toNamed(Routes.SHARE_DIALOG, arguments: {'shareURL': url});
            _clipboardText = url;
          },
          onCanceled: () => _clipboardText = url,
        );
      }
    });
  }
}
