import 'package:daily_satori/app/compontents/dream_webview/flutter_inappwebview_screenshot.dart';
import 'package:flutter_inappwebview/flutter_inappwebview.dart';

class DreamWebViewController {
  InAppWebViewController webViewController;
  DreamWebViewController(this.webViewController);

  void loadUrl(String url) {
    webViewController.loadUrl(urlRequest: URLRequest(url: WebUri(url)));
  }

  void goBack() {
    webViewController.goBack();
  }

  void goForward() {
    webViewController.goForward();
  }

  void reload() {
    webViewController.reload();
  }

  void addJavaScriptHandler({required String handlerName, required void Function(List<dynamic> args) callback}) {
    webViewController.addJavaScriptHandler(handlerName: handlerName, callback: callback);
  }

  Future<dynamic> evaluateJavascript({required String source}) async {
    return await webViewController.evaluateJavascript(source: source);
  }

  Future<void> injectJavascriptFileFromAsset({required String assetFilePath}) async {
    await webViewController.injectJavascriptFileFromAsset(assetFilePath: assetFilePath);
  }

  Future<String> captureFulScreenshot() async {
    return await captureFullPageScreenshot(webViewController);
  }

  Future<void> translatePage() async {
    await evaluateJavascript(source: "translate.changeLanguage('chinese_simplified');");
  }
}
