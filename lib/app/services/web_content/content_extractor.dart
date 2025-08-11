import 'package:daily_satori/app/components/webview/headless_webview.dart';
import 'package:daily_satori/app/services/logger_service.dart';

/// 网页内容数据结构
class ExtractedWebContent {
  final String title;
  final String content;
  final String htmlContent;
  final String? coverImageUrl;
  
  ExtractedWebContent({
    required this.title,
    required this.content,
    required this.htmlContent,
    this.coverImageUrl,
  });
}

/// 网页内容提取器
/// 专门负责从网页中提取标题、内容、图片等基本信息
class ContentExtractor {
  
  /// 提取网页内容
  Future<ExtractedWebContent> extractContent(String url) async {
    logger.i('[内容提取] ▶ 开始提取网页内容: $url');
    
    try {
      final headlessWebView = HeadlessWebView();
      final result = await headlessWebView.loadAndParseUrl(url);
      
      // 验证内容
      _validateContent(result);
      
      logger.i('[内容提取] ◀ 内容提取成功: ${result.title}');
      
      return ExtractedWebContent(
        title: result.title,
        content: result.textContent,
        htmlContent: result.htmlContent,
        coverImageUrl: result.coverImageUrl,
      );
    } catch (e) {
      logger.e('[内容提取] 提取失败: $e');
      throw Exception('网页内容提取失败: $e');
    }
  }
  
  /// 验证内容有效性
  void _validateContent(dynamic result) {
    if (result.title.isEmpty) {
      throw Exception('网页标题为空');
    }
    
    if (result.htmlContent.isEmpty || result.htmlContent.length < 100) {
      throw Exception('HTML内容为空或过短(${result.htmlContent.length}字节)');
    }
    
    if (result.textContent.isEmpty || result.textContent.length < 50) {
      throw Exception('文本内容为空或过短(${result.textContent.length}字节)');
    }
  }
}

