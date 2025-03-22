import 'dart:async';
import 'package:flutter_inappwebview/flutter_inappwebview.dart';

import 'package:daily_satori/app/components/webview/base_webview.dart';
import 'package:daily_satori/app/components/webview/flutter_inappwebview_screenshot.dart';
import 'package:daily_satori/app/services/logger_service.dart';

/// 无头浏览器数据模型
class HeadlessWebViewResult {
  final String title;
  final String excerpt;
  final String htmlContent;
  final String textContent;
  final String publishedTime;
  final List<String> imageUrls;
  final List<String> screenshots;

  HeadlessWebViewResult({
    required this.title,
    required this.excerpt,
    required this.htmlContent,
    required this.textContent,
    required this.publishedTime,
    required this.imageUrls,
    required this.screenshots,
  });

  /// 创建空的结果数据
  factory HeadlessWebViewResult.empty() {
    return HeadlessWebViewResult(
      title: '',
      excerpt: '',
      htmlContent: '',
      textContent: '',
      publishedTime: '',
      imageUrls: const [],
      screenshots: const [],
    );
  }
}

/// 无头浏览器类
class HeadlessWebView extends BaseWebView {
  /// 加载URL并解析内容
  Future<HeadlessWebViewResult> loadAndParseUrl(String url) async {
    if (url.isEmpty) {
      logger.e("[HeadlessWebView] URL为空，无法获取内容");
      return HeadlessWebViewResult.empty();
    }

    logger.i("[HeadlessWebView] 开始获取网页内容: $url");

    // 创建无头浏览器
    final headlessWebView = HeadlessInAppWebView(
      initialUrlRequest: URLRequest(url: WebUri(url)),
      initialSettings: getWebViewSettings(isHeadless: true),
      onConsoleMessage: (controller, consoleMessage) {
        logger.d("[HeadlessWebView] ${consoleMessage.message}");
      },
    );

    String title = '';
    String excerpt = '';
    String htmlContent = '';
    String textContent = '';
    String publishedTime = '';
    List<String> imageUrls = [];
    List<String> screenshots = [];

    try {
      // 运行无头浏览器
      await headlessWebView.run();

      // 获取控制器
      final controller = headlessWebView.webViewController!;

      // 注入资源和脚本
      await Future.wait([injectResources(controller), injectCssRules(controller), injectTwitterCssRules(controller)]);

      // 等待页面加载完成
      await Future.delayed(const Duration(seconds: 3));

      // 注入解析脚本
      await controller.injectJavascriptFileFromAsset(assetFilePath: "assets/js/Readability.js");
      await controller.injectJavascriptFileFromAsset(assetFilePath: "assets/js/parse_content.js");

      // 执行解析
      final result = await controller.evaluateJavascript(source: "parseContent()");

      if (result != null) {
        // 转换为Map<String, dynamic>类型
        final resultMap = result as Map<dynamic, dynamic>;
        title = resultMap['title']?.toString() ?? '';
        excerpt = resultMap['excerpt']?.toString() ?? '';
        htmlContent = resultMap['htmlContent']?.toString() ?? '';
        textContent = resultMap['textContent']?.toString() ?? '';
        publishedTime = resultMap['publishedTime']?.toString() ?? '';

        if (resultMap['imageUrls'] != null && resultMap['imageUrls'] is List) {
          imageUrls = (resultMap['imageUrls'] as List).map((e) => e.toString()).toList();
        }
      }

      // 捕获网页截图
      screenshots = await captureFullPageScreenshot(controller);
    } catch (e) {
      logger.e("[HeadlessWebView] 获取网页内容失败: $e");
    } finally {
      // 关闭无头浏览器
      await headlessWebView.dispose();
    }

    return HeadlessWebViewResult(
      title: title,
      excerpt: excerpt,
      htmlContent: htmlContent,
      textContent: textContent,
      publishedTime: publishedTime,
      imageUrls: imageUrls,
      screenshots: screenshots,
    );
  }
}
