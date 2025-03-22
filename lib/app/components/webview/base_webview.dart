import 'dart:async';
import 'dart:typed_data';

import 'package:flutter_inappwebview/flutter_inappwebview.dart';

import 'package:daily_satori/app/services/adblock_service.dart';
import 'package:daily_satori/app/services/logger_service.dart';
import 'package:daily_satori/global.dart';

/// WebView基类，抽象出共同的功能
abstract class BaseWebView {
  /// 注入资源
  Future<void> injectResources(InAppWebViewController controller) async {
    await controller.injectJavascriptFileFromAsset(assetFilePath: "assets/js/translate.js");
    await controller.injectJavascriptFileFromAsset(assetFilePath: "assets/js/common.js");
    await controller.injectCSSFileFromAsset(assetFilePath: "assets/css/common.css");
  }

  /// 注入Twitter样式规则
  Future<void> injectTwitterCssRules(InAppWebViewController controller) async {
    final url = await controller.getUrl();
    final host = url?.host ?? '';

    if (host.contains('twitter.com') || host.contains('x.com')) {
      logger.i("注入 Twitter 样式规则");
      const cssRule = '''
        a span.r-qlhcfr {
          display: none !important;
        }
      ''';
      await controller.injectCSSCode(source: cssRule);
    }
  }

  /// 注入CSS广告屏蔽规则
  Future<void> injectCssRules(InAppWebViewController controller) async {
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

  /// 处理URL加载
  Future<NavigationActionPolicy> handleUrlLoading(InAppWebViewController controller, NavigationAction action) async {
    final uri = action.request.url!;
    return (uri.scheme == "http" || uri.scheme == "https")
        ? NavigationActionPolicy.ALLOW
        : NavigationActionPolicy.CANCEL;
  }

  /// 处理请求拦截
  Future<WebResourceResponse?> handleShouldInterceptRequest(
    InAppWebViewController controller,
    WebResourceRequest request,
  ) async {
    final url = request.url.toString().replaceFirst(RegExp(r'^https?://'), '');

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

    RegExp? regexRule = ADBlockService.i.regexNetworkRules.firstWhere(
      (rule) => rule.hasMatch(url),
      orElse: () => RegExp(''),
    );

    if (regexRule.pattern.isNotEmpty) {
      logger.d("[广告拦截-正则匹配] => 开始拦截广告请求: $url => ${regexRule.pattern}");
      return WebResourceResponse(data: Uint8List(0));
    }

    return null;
  }

  /// 获取WebView设置
  InAppWebViewSettings getWebViewSettings({bool isHeadless = false}) {
    return InAppWebViewSettings(
      isInspectable: !isProduction,
      javaScriptEnabled: true,
      useShouldInterceptFetchRequest: true,
      mediaPlaybackRequiresUserGesture: false,
      allowsInlineMediaPlayback: true,
      iframeAllow: "camera; microphone",
      iframeAllowFullscreen: true,
      verticalScrollBarEnabled: !isHeadless,
      horizontalScrollBarEnabled: !isHeadless,
    );
  }

  /// 处理权限请求
  Future<PermissionResponse> handlePermissionRequest(
    InAppWebViewController controller,
    PermissionRequest request,
  ) async {
    return PermissionResponse(resources: request.resources, action: PermissionResponseAction.GRANT);
  }
}
