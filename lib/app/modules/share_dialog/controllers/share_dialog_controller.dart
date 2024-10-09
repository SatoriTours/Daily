import 'package:daily_satori/app/services/ai_service.dart';
import 'package:daily_satori/global.dart';
import 'package:flutter_inappwebview/flutter_inappwebview.dart';
import 'package:get/get.dart';

class ShareDialogController extends GetxController {
  String? shareURL = isProduction ? null : 'https://www.oschina.net/news/315112/python-3130-final-released';

  InAppWebViewController? webViewController;
  final webloadProgress = 0.0.obs;

  String _title = '';
  String _excerpt = '';
  String _content = '';
  String _textContent = '';

  void saveArticleInfo(title, excerpt, content, textContent) {
    _title = title.trim();
    _excerpt = excerpt.trim();
    _content = content.trim();
    _textContent = textContent.trim();
  }

  Future<void> saveArticle() async {
    logger.d(await AiService.instance.translate(_title));
    logger.d(await AiService.instance.summarize(_textContent));
  }
}
