import 'package:daily_satori/app/components/webview/headless_webview.dart';
import 'package:daily_satori/app/services/logger_service.dart';
import 'package:daily_satori/app/services/web_content/article_manager.dart';

/// 网页内容提取器
class ContentExtractor {
  /// 提取网页内容
  Future<ExtractedWebContent> extract(String url) async {
    logger.i('[WebContent] 提取内容: $url');

    try {
      final webView = HeadlessWebView();
      final result = await webView.loadAndParseUrl(url);

      if (result.title.isEmpty) throw Exception('网页标题为空');
      if (result.htmlContent.length < 100) throw Exception('HTML内容过短');
      if (result.textContent.length < 50) throw Exception('文本内容过短');

      logger.i('[WebContent] 内容提取成功: ${result.title}');
      return ExtractedWebContent(
        title: result.title,
        content: result.textContent,
        htmlContent: result.htmlContent,
        coverImageUrl: result.coverImageUrl,
      );
    } catch (e) {
      logger.e('[WebContent] 内容提取失败: $e');
      throw Exception('网页内容提取失败: $e');
    }
  }
}
