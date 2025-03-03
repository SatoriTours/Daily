import 'package:daily_satori/app/components/dream_webview/flutter_inappwebview_screenshot.dart';
import 'package:flutter_inappwebview/flutter_inappwebview.dart';

class DreamWebViewController {
  /// WebView控制器实例
  final InAppWebViewController webViewController;

  /// 构造函数
  DreamWebViewController(this.webViewController);

  /// 加载URL
  void loadUrl(String url) {
    webViewController.loadUrl(urlRequest: URLRequest(url: WebUri(url)));
  }

  /// 返回上一页
  void goBack() => webViewController.goBack();

  /// 前进下一页
  void goForward() => webViewController.goForward();

  /// 刷新页面
  void reload() => webViewController.reload();

  /// 获取内容高度
  Future<int> getContentHeight() async {
    return (await webViewController.getContentHeight()) ?? 0;
  }

  /// 滚动到指定位置
  Future<void> scrollTo({required int x, required int y}) async {
    await webViewController.scrollTo(x: x, y: y);
  }

  /// 滚动指定距离
  Future<void> scrollBy({required int x, required int y}) async {
    await webViewController.scrollTo(x: x, y: y);
  }

  /// 添加JavaScript处理器
  void addJavaScriptHandler({required String handlerName, required void Function(List<dynamic> args) callback}) {
    webViewController.addJavaScriptHandler(handlerName: handlerName, callback: callback);
  }

  /// 执行JavaScript代码
  Future<dynamic> evaluateJavascript({required String source}) async {
    return await webViewController.evaluateJavascript(source: source);
  }

  /// 注入JavaScript文件
  Future<void> injectJavascriptFileFromAsset({required String assetFilePath}) async {
    await webViewController.injectJavascriptFileFromAsset(assetFilePath: assetFilePath);
  }

  /// 捕获全屏截图
  Future<List<String>> captureFulScreenshot() async {
    return await captureFullPageScreenshot(webViewController);
  }

  /// 翻译页面为简体中文
  Future<void> translatePage() async {
    await evaluateJavascript(source: "translate.changeLanguage('chinese_simplified');");
  }
}
