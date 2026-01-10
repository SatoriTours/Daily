import 'package:flutter_inappwebview/flutter_inappwebview.dart';

/// WebView 控制器基类
///
/// 提供 WebView 的基本操作功能，包括：
/// - 页面导航（加载、前进、后退、刷新）
/// - 页面滚动控制
/// - JavaScript 交互
/// - CSS 注入
/// - 页面翻译
///
/// 使用示例:
/// ```dart
/// final controller = BaseWebViewController(webViewController);
/// await controller.loadUrl('https://example.com');
/// await controller.injectCSSCode(source: 'body { background: #fff; }');
/// ```
class BaseWebViewController {
  /// WebView 控制器实例
  ///
  /// 底层的 InAppWebViewController 实例，用于直接控制 WebView
  final InAppWebViewController webViewController;

  /// 创建一个 WebView 控制器
  ///
  /// [webViewController] 底层的 InAppWebViewController 实例
  BaseWebViewController(this.webViewController);

  /// 加载指定 URL
  ///
  /// [url] 要加载的网页地址
  Future<void> loadUrl(String url) async {
    await webViewController.loadUrl(urlRequest: URLRequest(url: WebUri(url)));
  }

  /// 返回上一页
  Future<void> goBack() async {
    await webViewController.goBack();
  }

  /// 前进下一页
  Future<void> goForward() async {
    await webViewController.goForward();
  }

  /// 刷新当前页面
  Future<void> reload() async {
    await webViewController.reload();
  }

  /// 获取页面内容高度
  ///
  /// 返回页面内容的实际高度（像素）
  Future<int> getContentHeight() async {
    return (await webViewController.getContentHeight()) ?? 0;
  }

  /// 滚动到指定位置
  ///
  /// [x] 水平滚动位置
  /// [y] 垂直滚动位置
  Future<void> scrollTo({required int x, required int y}) async {
    await webViewController.scrollTo(x: x, y: y);
  }

  /// 滚动指定距离
  ///
  /// [x] 水平滚动距离
  /// [y] 垂直滚动距离
  Future<void> scrollBy({required int x, required int y}) async {
    await webViewController.scrollTo(x: x, y: y);
  }

  /// 添加 JavaScript 处理器
  ///
  /// [handlerName] 处理器名称
  /// [callback] 处理器回调函数
  void addJavaScriptHandler({
    required String handlerName,
    required void Function(List<dynamic> args) callback,
  }) {
    webViewController.addJavaScriptHandler(
      handlerName: handlerName,
      callback: callback,
    );
  }

  /// 执行 JavaScript 代码
  ///
  /// [source] JavaScript 代码
  /// 返回 JavaScript 代码的执行结果
  Future<dynamic> evaluateJavascript({required String source}) async {
    return await webViewController.evaluateJavascript(source: source);
  }

  /// 注入 JavaScript 文件
  ///
  /// [assetFilePath] JavaScript 文件的资源路径
  Future<void> injectJavascriptFileFromAsset({
    required String assetFilePath,
  }) async {
    await webViewController.injectJavascriptFileFromAsset(
      assetFilePath: assetFilePath,
    );
  }

  /// 注入 CSS 文件
  ///
  /// [assetFilePath] CSS 文件的资源路径
  Future<void> injectCSSFileFromAsset({required String assetFilePath}) async {
    await webViewController.injectCSSFileFromAsset(
      assetFilePath: assetFilePath,
    );
  }

  /// 注入 CSS 代码
  ///
  /// [source] CSS 代码
  Future<void> injectCSSCode({required String source}) async {
    await webViewController.injectCSSCode(source: source);
  }

  /// 将页面翻译为简体中文
  ///
  /// 使用内置的翻译功能将页面内容翻译为简体中文
  Future<void> translatePage() async {
    await evaluateJavascript(
      source: "translate.changeLanguage('chinese_simplified');",
    );
  }
}
