import 'package:flutter/material.dart';
import 'package:flutter_inappwebview/flutter_inappwebview.dart';

import 'package:daily_satori/app/components/webview/base_webview.dart';
import 'package:daily_satori/app/components/webview/base_webview_controller.dart';
import 'package:daily_satori/app/services/logger_service.dart';

/// 可视化WebView组件
class VisibleWebView extends StatelessWidget {
  final String url;
  final void Function(BaseWebViewController controller)? onWebViewCreated;
  final void Function(WebUri? url)? onLoadStart;
  final void Function(double progress)? onProgressChanged;
  final void Function()? onLoadStop;

  /// 基类实例
  final BaseWebView _baseWebView = _VisibleWebViewImpl();

  VisibleWebView({
    super.key,
    required this.url,
    this.onWebViewCreated,
    this.onLoadStart,
    this.onProgressChanged,
    this.onLoadStop,
  });

  @override
  Widget build(BuildContext context) {
    return InAppWebView(
      initialUrlRequest: URLRequest(url: WebUri(url)),
      initialSettings: _baseWebView.getWebViewSettings(),
      onWebViewCreated: _handleWebViewCreated,
      onPermissionRequest: _baseWebView.handlePermissionRequest,
      onLoadStart: (controller, url) => _handleLoadStart(context, controller, url),
      onLoadStop: (controller, url) => _handleLoadStop(context, url),
      onReceivedError: _handleError,
      onProgressChanged: _handleProgressChanged,
      onConsoleMessage: _handleConsoleMessage,
      shouldOverrideUrlLoading: _baseWebView.handleUrlLoading,
      shouldInterceptRequest: _baseWebView.handleShouldInterceptRequest,
    );
  }

  void _handleWebViewCreated(InAppWebViewController webController) {
    final controller = BaseWebViewController(webController);
    onWebViewCreated?.call(controller);
  }

  Future<void> _handleLoadStart(BuildContext context, InAppWebViewController controller, WebUri? url) async {
    if (!context.mounted) return;

    logger.i("开始加载网页 $url");

    try {
      await Future.wait([
        _baseWebView.injectResources(controller),
        _baseWebView.injectCssRules(controller),
        _baseWebView.injectTwitterCssRules(controller),
      ]);
    } catch (e) {
      logger.e("加载资源时出错: $e");
    }

    onProgressChanged?.call(0);
    onLoadStart?.call(url);
  }

  Future<void> _handleLoadStop(BuildContext context, WebUri? url) async {
    if (!context.mounted) return;
    logger.i("网页加载完成 $url");
    onLoadStop?.call();
    onProgressChanged?.call(0);
  }

  void _handleError(InAppWebViewController controller, WebResourceRequest request, WebResourceError error) {
    onProgressChanged?.call(0);
  }

  void _handleProgressChanged(InAppWebViewController controller, int progress) {
    onProgressChanged?.call(progress / 100);
  }

  void _handleConsoleMessage(InAppWebViewController controller, ConsoleMessage message) {
    // if (!isProduction) {
    //   logger.d("浏览器日志: ${message.message}");
    // }
  }
}

/// 可视化WebView的内部实现
class _VisibleWebViewImpl extends BaseWebView {}
