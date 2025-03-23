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

    // 创建无头浏览器会话
    final session = _HeadlessWebViewSession(url, this);

    try {
      // 运行无头浏览器并等待内容处理完成
      return await session.start();
    } catch (e) {
      logger.e("[HeadlessWebView] 获取网页内容失败: $e");
      return HeadlessWebViewResult.empty();
    }
  }
}

/// 无头浏览器会话，封装单次网页访问的所有状态和操作
class _HeadlessWebViewSession {
  final String url;
  final BaseWebView _baseWebView;
  final Completer<HeadlessWebViewResult> _completer = Completer<HeadlessWebViewResult>();

  // 浏览器实例
  late HeadlessInAppWebView _headlessWebView;

  // 状态标志
  bool _isCompleted = false;
  bool _isStabilityCheckStarted = false;
  bool _hasLoadStopFired = false;

  // DOM稳定性检测变量
  int _stabilityCounter = 0;
  int _lastDOMSize = 0;
  static const _requiredStableChecks = 5; // 需要连续三次检测DOM稳定才认为页面加载完成
  static const _domStabilityThreshold = 0.03; // DOM变化率小于3%视为稳定

  // 定时器
  Timer? _stabilityTimer;
  Timer? _timeoutTimer;

  _HeadlessWebViewSession(this.url, this._baseWebView);

  /// 启动无头浏览器会话
  Future<HeadlessWebViewResult> start() async {
    // 配置无头浏览器
    _configureWebView();

    // 设置超时保护
    _setupTimeout();

    try {
      // 运行无头浏览器
      await _headlessWebView.run();

      // 检查加载进度
      _checkLoadProgress();

      // 等待操作完成
      return await _completer.future;
    } catch (e) {
      _cleanup();
      rethrow;
    }
  }

  /// 配置无头浏览器
  void _configureWebView() {
    _headlessWebView = HeadlessInAppWebView(
      initialUrlRequest: URLRequest(url: WebUri(url)),
      initialSettings: _baseWebView.getWebViewSettings(isHeadless: true),
      onConsoleMessage: _handleConsoleMessage,
      onLoadStop: _handleLoadStop,
    );
  }

  /// 处理控制台消息
  void _handleConsoleMessage(InAppWebViewController controller, ConsoleMessage consoleMessage) {
    logger.d("[HeadlessWebView] ${consoleMessage.message}");
  }

  /// 处理页面加载停止事件
  void _handleLoadStop(InAppWebViewController controller, Uri? url) async {
    logger.i("[HeadlessWebView] onLoadStop 触发: $url");
    _hasLoadStopFired = true;

    // 加载停止后，延迟1秒启动DOM稳定性检测
    if (!_isStabilityCheckStarted) {
      await Future.delayed(const Duration(seconds: 3));
      _startDOMStabilityCheck(controller);
      _isStabilityCheckStarted = true;
    }
  }

  /// 设置超时保护
  void _setupTimeout() {
    const maxTimeout = Duration(seconds: 30);
    _timeoutTimer = Timer(maxTimeout, () {
      if (!_isCompleted) {
        logger.w("[HeadlessWebView] 页面加载超时(${maxTimeout.inSeconds}秒)，使用当前内容");
        _processContent();
      }
    });
  }

  /// 检查加载进度，如果5秒后仍未触发onLoadStop，手动启动DOM稳定性检测
  void _checkLoadProgress() {
    Future.delayed(const Duration(seconds: 6), () {
      if (!_hasLoadStopFired && !_isStabilityCheckStarted && !_isCompleted) {
        logger.w("[HeadlessWebView] 5秒内未触发onLoadStop，手动启动DOM稳定性检测");
        _startDOMStabilityCheck(_headlessWebView.webViewController!);
        _isStabilityCheckStarted = true;
      }
    });
  }

  /// 启动DOM稳定性检测
  void _startDOMStabilityCheck(InAppWebViewController controller) {
    _stabilityTimer = Timer.periodic(const Duration(milliseconds: 800), (timer) async {
      try {
        if (_isCompleted) {
          timer.cancel();
          return;
        }

        await _checkDOMStability(controller);
      } catch (e) {
        logger.e("[HeadlessWebView] DOM稳定性检测错误: $e");
      }
    });
  }

  /// 检查DOM稳定性
  Future<void> _checkDOMStability(InAppWebViewController controller) async {
    // 获取DOM大小作为稳定性指标
    final domSizeResult = await controller.evaluateJavascript(source: "document.documentElement.outerHTML.length");

    if (domSizeResult == null) return;

    int currentSize = int.tryParse(domSizeResult.toString()) ?? 0;

    // 检查DOM是否稳定(变化小于阈值)
    if (_lastDOMSize > 0) {
      double changePercent = (currentSize - _lastDOMSize).abs() / _lastDOMSize;
      logger.d("[HeadlessWebView] DOM变化率: ${(changePercent * 100).toStringAsFixed(2)}%");

      if (changePercent < _domStabilityThreshold) {
        _stabilityCounter++;
        logger.d("[HeadlessWebView] DOM稳定计数: $_stabilityCounter/$_requiredStableChecks");

        if (_stabilityCounter >= _requiredStableChecks) {
          logger.i("[HeadlessWebView] DOM已稳定，进行内容提取");
          _stabilityTimer?.cancel();
          await _processContent();
        }
      } else {
        // DOM变化较大，重置稳定计数器
        _stabilityCounter = 0;
      }
    }

    _lastDOMSize = currentSize;
  }

  /// 处理页面内容
  Future<void> _processContent() async {
    if (_isCompleted) return;
    _isCompleted = true;

    try {
      final controller = _headlessWebView.webViewController!;

      // 清除计时器
      _cleanup();

      // 解析网页内容
      final result = await _parseWebPageContent(controller);

      // 完成操作
      _completer.complete(result);
    } catch (e) {
      logger.e("[HeadlessWebView] 处理网页内容失败: $e");
      _completer.complete(HeadlessWebViewResult.empty());
    } finally {
      // 关闭无头浏览器
      await _headlessWebView.dispose();
    }
  }

  /// 解析网页内容
  Future<HeadlessWebViewResult> _parseWebPageContent(InAppWebViewController controller) async {
    // 注入资源和脚本
    await Future.wait([
      _baseWebView.injectResources(controller),
      _baseWebView.injectCssRules(controller),
      _baseWebView.injectTwitterCssRules(controller),
    ]);

    // 注入解析脚本
    await controller.injectJavascriptFileFromAsset(assetFilePath: "assets/js/Readability.js");
    await controller.injectJavascriptFileFromAsset(assetFilePath: "assets/js/parse_content.js");

    // 执行解析
    final parseResult = await controller.evaluateJavascript(source: "parseContent()");

    if (parseResult == null) {
      return HeadlessWebViewResult.empty();
    }

    // 提取解析结果
    final resultMap = parseResult as Map<dynamic, dynamic>;
    final result = _extractResultFromMap(resultMap);

    // 捕获网页截图
    final screenshots = await captureFullPageScreenshot(controller);

    // 创建最终结果对象
    return HeadlessWebViewResult(
      title: result['title'] ?? '',
      excerpt: result['excerpt'] ?? '',
      htmlContent: result['htmlContent'] ?? '',
      textContent: result['textContent'] ?? '',
      publishedTime: result['publishedTime'] ?? '',
      imageUrls: result['imageUrls'] ?? [],
      screenshots: screenshots,
    );
  }

  /// 从解析结果Map中提取数据
  Map<String, dynamic> _extractResultFromMap(Map<dynamic, dynamic> resultMap) {
    final Map<String, dynamic> result = {};

    result['title'] = resultMap['title']?.toString() ?? '';
    result['excerpt'] = resultMap['excerpt']?.toString() ?? '';
    result['htmlContent'] = resultMap['htmlContent']?.toString() ?? '';
    result['textContent'] = resultMap['textContent']?.toString() ?? '';
    result['publishedTime'] = resultMap['publishedTime']?.toString() ?? '';

    List<String> imageUrls = [];
    if (resultMap['imageUrls'] != null && resultMap['imageUrls'] is List) {
      imageUrls = (resultMap['imageUrls'] as List).map((e) => e.toString()).toList();
    }
    result['imageUrls'] = imageUrls;

    return result;
  }

  /// 清理资源
  void _cleanup() {
    _stabilityTimer?.cancel();
    _timeoutTimer?.cancel();
  }
}
