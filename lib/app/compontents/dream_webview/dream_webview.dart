import 'dart:async';
import 'dart:typed_data';

import 'package:flutter/cupertino.dart';

import 'package:flutter_inappwebview/flutter_inappwebview.dart';

import 'package:daily_satori/app/compontents/dream_webview/dream_webview_controller.dart';
import 'package:daily_satori/app/services/adblock_service.dart';
import 'package:daily_satori/app/services/logger_service.dart';
import 'package:daily_satori/global.dart';

class DreamWebView extends StatelessWidget {
  final String url;
  final void Function(DreamWebViewController controller)? onWebViewCreated;
  final void Function(WebUri? url)? onLoadStart;
  final void Function(double progress)? onProgressChanged;
  final void Function()? onLoadStop;

  const DreamWebView({
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
      initialSettings: _getWebViewSettings(),
      onWebViewCreated: _handleWebViewCreated,
      onPermissionRequest: _handlePermissionRequest,
      onLoadStart: (controller, url) => _handleLoadStart(context, controller, url),
      onLoadStop: (controller, url) => _handleLoadStop(context, url),
      onReceivedError: _handleError,
      onProgressChanged: _handleProgressChanged,
      onConsoleMessage: _handleConsoleMessage,
      shouldOverrideUrlLoading: _handleUrlLoading,
      shouldInterceptRequest: _handleShouldInterceptRequest,
    );
  }

  void _handleWebViewCreated(InAppWebViewController webController) {
    final controller = DreamWebViewController(webController);
    onWebViewCreated?.call(controller);
  }

  Future<PermissionResponse> _handlePermissionRequest(
      InAppWebViewController controller, PermissionRequest request) async {
    return PermissionResponse(resources: request.resources, action: PermissionResponseAction.GRANT);
  }

  Future<void> _handleLoadStart(BuildContext context, InAppWebViewController controller, WebUri? url) async {
    if (!context.mounted) return;

    logger.i("开始加载网页 $url");

    try {
      await Future.wait([
        _injectResources(controller),
        _injectCssRules(controller),
        _injectTwitterCssRules(controller),
      ]);
    } catch (e) {
      logger.e("加载资源时出错: $e");
    }

    onProgressChanged?.call(0);
    onLoadStart?.call(url);
  }

  Future<void> _injectResources(InAppWebViewController controller) async {
    await controller.injectJavascriptFileFromAsset(assetFilePath: "assets/js/translate.js");
    await controller.injectJavascriptFileFromAsset(assetFilePath: "assets/js/common.js");
    await controller.injectCSSFileFromAsset(assetFilePath: "assets/css/common.css");
  }

  // 注入 Twitter 样式规则, 解决 r-qlhcfr 样式设置 font-size=0.001px导致的显示问题, chrome等浏览器会隐藏显示这部分内容，但是 InAppWebview 不会隐藏从而导致url 显示一个很小的http://出来
  Future<void> _injectTwitterCssRules(InAppWebViewController controller) async {
    final url = await controller.getUrl();
    final host = url?.host ?? '';

    if (host.contains('twitter.com') || host.contains('x.com')) {
      logger.i("注入 Twitter 样式规则");
      const cssRule = '''
        a span.r-qlhcfr {
          display: none !important;
        }
      ''';
      // 也可以使用 font-size: inherit !important; 样式，把字体大小设置为正常，但是这样会导致 url 显示 http:// 出来, 和默认chrome浏览器显示效果不一致
      await controller.injectCSSCode(source: cssRule);
    }
  }

  // DOM 元素隐藏规则
  Future<void> _injectCssRules(InAppWebViewController controller) async {
    // 批量注入 CSS 规则
    final url = await controller.getUrl();
    final domain = getTopLevelDomain(url?.host);

    final StringBuffer css = StringBuffer();
    for (final rule in ADBlockService.i.cssRules) {
      css.writeln('$rule { display: none !important; }');
    }

    final rules = ADBlockService.i.domainCssRules[domain] ?? [];
    for (final rule in rules) {
      css.writeln('$rule { display: none !important; }');
    }

    final cssRules = css.toString();
    logger.i("注入 CSS广告屏蔽规则");
    await controller.injectCSSCode(source: cssRules);
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
    if (!isProduction) {
      logger.d("浏览器日志: ${message.message}");
    }
  }

  Future<NavigationActionPolicy> _handleUrlLoading(InAppWebViewController controller, NavigationAction action) async {
    final uri = action.request.url!;
    return (uri.scheme == "http" || uri.scheme == "https")
        ? NavigationActionPolicy.ALLOW
        : NavigationActionPolicy.CANCEL;
  }

  Future<WebResourceResponse?> _handleShouldInterceptRequest(
      InAppWebViewController controller, WebResourceRequest request) async {
    final url = request.url.toString().replaceFirst(RegExp(r'^https?://'), '');
    // final domain = getTopLevelDomain(url.host);

    // logger.d("准备拦截广告请求: $url");
    var rule = ADBlockService.i.exactNetworkRules.firstWhere((rule) => url == rule, orElse: () => '');

    if (rule.isNotEmpty) {
      logger.d("[广告拦截-完全匹配] => 开始拦截广告请求: $url => $rule");
      return WebResourceResponse(data: Uint8List(0));
    }

    rule = ADBlockService.i.containsNetworkRules.firstWhere((rule) => url.contains(rule), orElse: () => '');

    if (rule.isNotEmpty) {
      logger.d("[广告拦截-部分匹配] => 开始拦截广告请求: $url => $rule");
      return WebResourceResponse(data: Uint8List(0));
    }

    RegExp? regexRule =
        ADBlockService.i.regexNetworkRules.firstWhere((rule) => rule.hasMatch(url), orElse: () => RegExp(''));

    if (regexRule.pattern.isNotEmpty) {
      logger.d("[广告拦截-正则匹配] => 开始拦截广告请求: $url => ${regexRule.pattern}");
      return WebResourceResponse(data: Uint8List(0));
    }

    return null;
  }

  InAppWebViewSettings _getWebViewSettings() {
    return InAppWebViewSettings(
      isInspectable: !isProduction,
      mediaPlaybackRequiresUserGesture: false,
      allowsInlineMediaPlayback: true,
      iframeAllow: "camera; microphone",
      iframeAllowFullscreen: true,
      verticalScrollBarEnabled: false,
      horizontalScrollBarEnabled: false,
    );
  }
}
