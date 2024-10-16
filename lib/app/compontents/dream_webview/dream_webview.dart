import 'dart:async';

import 'package:daily_satori/app/compontents/dream_webview/dream_webview_controller.dart';
import 'package:daily_satori/app/services/adblock_service.dart';
import 'package:daily_satori/global.dart';
import 'package:flutter/cupertino.dart';
import 'package:flutter_inappwebview/flutter_inappwebview.dart';

class DreamWebView extends StatelessWidget {
  final String url;

  final void Function(DreamWebViewController controller)? onWebViewCreated;
  final void Function(WebUri? url)? onLoadStart;
  final void Function(double progress)? onProgressChanged;
  final void Function()? onLoadStop;

  const DreamWebView(
      {super.key, required this.url, this.onLoadStart, this.onProgressChanged, this.onWebViewCreated, this.onLoadStop});

  @override
  Widget build(BuildContext context) {
    return InAppWebView(
      initialUrlRequest: URLRequest(url: WebUri(url)),
      initialSettings: _webViewSettings(),
      onWebViewCreated: (webController) {
        DreamWebViewController controller = DreamWebViewController(webController);
        onWebViewCreated?.call(controller);
      },
      onPermissionRequest: (controller, request) async {
        return PermissionResponse(resources: request.resources, action: PermissionResponseAction.GRANT);
      },
      onLoadStart: (webController, url) async {
        logger.i("开始加载网页 $url");
        await webController.injectJavascriptFileFromAsset(assetFilePath: "assets/js/translate.js");
        await webController.injectJavascriptFileFromAsset(assetFilePath: "assets/js/common.js");
        await webController.injectCSSFileFromAsset(assetFilePath: "assets/css/common.css");

        onProgressChanged?.call(0);
        onLoadStart?.call(url);
        removeADNodes(webController);
      },
      onLoadStop: (webController, url) async {
        logger.i("网页加载完成 $url");
        onLoadStop?.call();
        onProgressChanged?.call(0);
      },
      onReceivedError: (webController, request, error) {
        onProgressChanged?.call(0);
      },
      onProgressChanged: (webController, progress) {
        onProgressChanged?.call(progress / 100);
      },
      onConsoleMessage: (controller, consoleMessage) {
        if (!isProduction) {
          logger.d("浏览器日志: ${consoleMessage.message}");
        }
      },
    );
  }

  Future<void> removeADNodes(InAppWebViewController controller) async {
    // await removeNodeByCssSelectors(controller, ADBlockService.instance.elementHidingRules);
    final url = await controller.getUrl();
    final domain = getTopLevelDomain(url?.host);
    logger.i("处理 $domain 的广告规则");
    if (domain.isNotEmpty && ADBlockService.instance.elementHidingRulesBySite.containsKey(domain)) {
      int count = 0;
      Timer.periodic(Duration(seconds: 2), (Timer t) async {
        await removeNodeByCssSelectors(controller, ADBlockService.instance.elementHidingRulesBySite[domain] ?? []);
        count += 1;
        // 当计数达到 10 次时，取消定时器
        if (count >= 20) {
          t.cancel();
          print('定时器已停止');
        }
      });
    }
  }

  Future<void> removeNodeByCssSelectors(InAppWebViewController controller, List<String> rules) async {
    for (var rule in rules) {
      // logger.i("执行规则 , $rule");
      try {
        await controller.evaluateJavascript(source: "document.querySelectorAll('$rule').forEach(e => e.remove())");
      } catch (e) {
        logger.d("执行广告规则出错 $e");
      }
    }
  }

  InAppWebViewSettings _webViewSettings() {
    return InAppWebViewSettings(
      isInspectable: !isProduction,
      mediaPlaybackRequiresUserGesture: false,
      allowsInlineMediaPlayback: true,
      iframeAllow: "camera; microphone",
      iframeAllowFullscreen: true,
      verticalScrollBarEnabled: false, // 隐藏垂直滚动条
    );
  }
}
