import 'dart:async';
import 'dart:typed_data';

import 'package:daily_satori/app/services/adblock_service.dart';
import 'package:daily_satori/app/services/logger_service.dart';
import 'package:daily_satori/global.dart';
import 'package:flutter_inappwebview/flutter_inappwebview.dart';

/// WebView 基类
///
/// 提供 WebView 的基础功能实现，包括：
/// - 资源注入（JavaScript 和 CSS）
/// - 广告拦截
/// - URL 加载控制
/// - 权限管理
/// - WebView 配置
///
/// 所有具体的 WebView 实现都应该继承此类。
abstract class BaseWebView {
  // MARK: - 资源注入

  /// 注入通用资源
  ///
  /// 注入应用程序的通用 JavaScript 和 CSS 文件
  /// - common.js: 通用 JavaScript 功能
  /// - common.css: 通用样式规则
  Future<void> injectResources(InAppWebViewController controller) async {
    await controller.injectJavascriptFileFromAsset(assetFilePath: "assets/js/common.js");
    await controller.injectCSSFileFromAsset(assetFilePath: "assets/css/common.css");
  }

  /// 注入 Twitter 专用样式规则
  ///
  /// 当页面是 Twitter 或 X.com 时，注入特定的样式规则
  /// 用于隐藏某些不需要的元素
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

  /// 注入广告屏蔽 CSS 规则
  ///
  /// 根据当前域名注入相应的广告屏蔽规则
  /// - 注入全局 CSS 规则
  /// - 注入特定域名的 CSS 规则
  Future<void> injectCssRules(InAppWebViewController controller) async {
    final url = await controller.getUrl();
    final domain = StringUtils.getTopLevelDomain(url?.host);

    final StringBuffer css = StringBuffer();

    // 添加全局规则
    for (final rule in ADBlockService.i.cssRules) {
      css.writeln('$rule { display: none !important; }');
    }

    // 添加域名特定规则
    final rules = ADBlockService.i.domainCssRules[domain] ?? [];
    for (final rule in rules) {
      css.writeln('$rule { display: none !important; }');
    }

    logger.i("注入 CSS 广告屏蔽规则");
    await controller.injectCSSCode(source: css.toString());
  }

  // MARK: - URL 和请求处理

  /// 处理 URL 加载请求
  ///
  /// 只允许加载 HTTP 和 HTTPS 协议的 URL
  /// 其他协议的 URL 将被取消加载
  Future<NavigationActionPolicy> handleUrlLoading(InAppWebViewController controller, NavigationAction action) async {
    final uri = action.request.url!;
    return (uri.scheme == "http" || uri.scheme == "https")
        ? NavigationActionPolicy.ALLOW
        : NavigationActionPolicy.CANCEL;
  }

  /// 处理网络请求拦截
  ///
  /// 根据广告拦截规则过滤请求：
  /// - 完全匹配规则
  /// - 包含匹配规则
  /// - 正则表达式匹配规则
  Future<WebResourceResponse?> handleShouldInterceptRequest(
    InAppWebViewController controller,
    WebResourceRequest request,
  ) async {
    final url = request.url.toString().replaceFirst(RegExp(r'^https?://'), '');

    // 检查完全匹配规则
    final exactRule = ADBlockService.i.exactNetworkRules.firstWhere((rule) => url == rule, orElse: () => '');

    if (exactRule.isNotEmpty) {
      logger.d("[广告拦截-完全匹配] => 开始拦截广告请求: $url => $exactRule");
      return WebResourceResponse(data: Uint8List(0));
    }

    // 检查包含匹配规则
    final containsRule = ADBlockService.i.containsNetworkRules.firstWhere(
      (rule) => url.contains(rule),
      orElse: () => '',
    );

    if (containsRule.isNotEmpty) {
      logger.d("[广告拦截-部分匹配] => 开始拦截广告请求: $url => $containsRule");
      return WebResourceResponse(data: Uint8List(0));
    }

    // 检查正则表达式匹配规则
    final regexRule = ADBlockService.i.regexNetworkRules.firstWhere(
      (rule) => rule.hasMatch(url),
      orElse: () => RegExp(''),
    );

    if (regexRule.pattern.isNotEmpty) {
      logger.d("[广告拦截-正则匹配] => 开始拦截广告请求: $url => ${regexRule.pattern}");
      return WebResourceResponse(data: Uint8List(0));
    }

    return null;
  }

  // MARK: - 配置和权限

  /// 获取 WebView 配置
  ///
  /// [isHeadless] 是否为无头模式
  /// 返回适合当前环境的 WebView 配置
  InAppWebViewSettings getWebViewSettings({bool isHeadless = false}) {
    return InAppWebViewSettings(
      isInspectable: !AppInfoUtils.isProduction,
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
  ///
  /// 默认授予所有请求的权限
  Future<PermissionResponse> handlePermissionRequest(
    InAppWebViewController controller,
    PermissionRequest request,
  ) async {
    return PermissionResponse(resources: request.resources, action: PermissionResponseAction.GRANT);
  }
}
