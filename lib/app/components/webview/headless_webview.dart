import 'dart:async';
import 'package:flutter_inappwebview/flutter_inappwebview.dart';

import 'package:daily_satori/app/components/webview/base_webview.dart';
import 'package:daily_satori/app/services/logger_service.dart';
import 'package:daily_satori/app/config/app_config.dart';

/// 无头浏览器数据模型
class HeadlessWebViewResult {
  final String title;
  final String excerpt;
  final String htmlContent;
  final String textContent;
  final String publishedTime;
  final String coverImageUrl; // 只保留一张封面图

  HeadlessWebViewResult({
    required this.title,
    required this.excerpt,
    required this.htmlContent,
    required this.textContent,
    required this.publishedTime,
    required this.coverImageUrl,
  });

  /// 创建空的结果数据
  factory HeadlessWebViewResult.empty() {
    return HeadlessWebViewResult(
      title: '',
      excerpt: '',
      htmlContent: '',
      textContent: '',
      publishedTime: '',
      coverImageUrl: '',
    );
  }
}

/// 无头浏览器类
class HeadlessWebView extends BaseWebView {
  // 资源管理
  final Set<_HeadlessWebViewSession> _activeSessions = {};
  Timer? _resourceMonitorTimer;

  HeadlessWebView() {
    _startResourceMonitor();
  }

  /// 启动资源监控
  void _startResourceMonitor() {
    _resourceMonitorTimer = Timer.periodic(NetworkConfig.timeout, (_) {
      _cleanupInactiveSessions();
    });
  }

  /// 清理不活跃的会话
  void _cleanupInactiveSessions() {
    final now = DateTime.now();
    final expiredSessions = _activeSessions.where((session) => session.isExpired(now)).toList();

    for (var session in expiredSessions) {
      _activeSessions.remove(session);
      logger.w("[HeadlessWebView] 强制清理过期会话: ${session.url}");
      session.forceDispose();
    }

    if (_activeSessions.isEmpty && _resourceMonitorTimer != null) {
      logger.i("[HeadlessWebView] 所有会话已清理，暂停资源监控");
      _resourceMonitorTimer?.cancel();
      _resourceMonitorTimer = null;
    }
  }

  /// 释放资源
  void dispose() {
    _resourceMonitorTimer?.cancel();

    for (var session in _activeSessions) {
      session.forceDispose();
    }
    _activeSessions.clear();
  }

  /// 加载URL并解析内容
  Future<HeadlessWebViewResult> loadAndParseUrl(String url) async {
    if (url.isEmpty) {
      logger.e("[HeadlessWebView] URL为空，无法获取内容");
      return HeadlessWebViewResult.empty();
    }

    logger.i("[HeadlessWebView] 开始获取网页内容: $url");

    // 检查是否需要等待其他会话完成
    while (_activeSessions.length >= WebViewConfig.maxConcurrentSessions) {
      logger.w("[HeadlessWebView] 已达到最大并发会话数(${WebViewConfig.maxConcurrentSessions})，等待中...");
      await Future.delayed(NetworkConfig.retryDelay);
      _cleanupInactiveSessions();
    }

    // 如果资源监控器未启动，现在启动它
    if (_resourceMonitorTimer == null) {
      _startResourceMonitor();
    }

    // 创建无头浏览器会话
    final session = _HeadlessWebViewSession(url, this);
    _activeSessions.add(session);

    try {
      // 运行无头浏览器并等待内容处理完成
      final result = await session.start();
      _activeSessions.remove(session);
      return result;
    } catch (e) {
      logger.e("[HeadlessWebView] 获取网页内容失败: $e");
      _activeSessions.remove(session);
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
  bool _isDisposed = false;

  // 会话创建和最后活动时间
  final DateTime _creationTime = DateTime.now();
  DateTime _lastActivityTime = DateTime.now();

  // DOM稳定性检测变量
  int _stabilityCounter = 0;
  int _lastDOMSize = 0;
  static const _requiredStableChecks = 3; // 从5次降低到3次连续检测DOM稳定就认为页面加载完成
  static const _domStabilityThreshold = 0.02; // DOM变化率小于2%视为稳定（从3%降低）

  // 定时器
  Timer? _stabilityTimer;
  Timer? _timeoutTimer;

  // 会话超时设置
  static const _sessionMaxLifetime = WebViewConfig.sessionMaxLifetime; // 最大生命周期
  static const _sessionInactivityTimeout = SessionConfig.inactivityTimeout; // 不活动超时时间

  _HeadlessWebViewSession(this.url, this._baseWebView);

  /// 检查会话是否过期
  bool isExpired(DateTime now) {
    if (_isDisposed) return true;

    // 检查会话是否超过最大生命周期
    if (now.difference(_creationTime) > _sessionMaxLifetime) {
      return true;
    }

    // 检查会话是否超过不活动超时时间
    if (now.difference(_lastActivityTime) > _sessionInactivityTimeout) {
      return true;
    }

    return false;
  }

  /// 强制释放会话资源
  Future<void> forceDispose() async {
    if (_isDisposed) return;

    _isDisposed = true;
    _cleanup();

    try {
      if (!_completer.isCompleted) {
        _completer.complete(HeadlessWebViewResult.empty());
      }

      await _headlessWebView.dispose();
    } catch (e) {
      logger.e("[HeadlessWebView] 释放会话资源失败: $e");
    }
  }

  /// 更新最后活动时间
  void _updateActivityTime() {
    _lastActivityTime = DateTime.now();
  }

  /// 启动无头浏览器会话
  Future<HeadlessWebViewResult> start() async {
    _updateActivityTime();

    // 配置无头浏览器
    _configureWebView();

    // 设置超时保护
    _setupTimeout();

    try {
      // 运行无头浏览器
      await _headlessWebView.run();
      _updateActivityTime();

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
      // onConsoleMessage: _handleConsoleMessage,
      onLoadStop: _handleLoadStop,
    );
  }

  /// 处理页面加载停止事件
  void _handleLoadStop(InAppWebViewController controller, Uri? url) async {
    _updateActivityTime();
    logger.i("[HeadlessWebView] onLoadStop 触发: $url");
    _hasLoadStopFired = true;

    // 加载停止后，延迟1.5秒启动DOM稳定性检测（从3秒减少到1.5秒）
    if (!_isStabilityCheckStarted) {
      await Future.delayed(WebViewConfig.domStabilityCheckDelay);
      _startDOMStabilityCheck(controller);
      _isStabilityCheckStarted = true;
    }
  }

  /// 设置超时保护
  void _setupTimeout() {
    const maxTimeout = WebViewConfig.timeout;
    _timeoutTimer = Timer(maxTimeout, () {
      if (!_isCompleted) {
        logger.w("[HeadlessWebView] 页面加载超时(${maxTimeout.inSeconds}秒)，使用当前内容");
        _processContent();
      }
    });
  }

  /// 检查加载进度，如果3秒后仍未触发onLoadStop，手动启动DOM稳定性检测
  void _checkLoadProgress() {
    Future.delayed(WebViewConfig.loadProgressCheckDelay, () {
      // 从6秒减少到4秒
      if (!_hasLoadStopFired && !_isStabilityCheckStarted && !_isCompleted) {
        logger.w("[HeadlessWebView] 4秒内未触发onLoadStop，手动启动DOM稳定性检测");
        _startDOMStabilityCheck(_headlessWebView.webViewController!);
        _isStabilityCheckStarted = true;
      }
    });
  }

  /// 启动DOM稳定性检测
  void _startDOMStabilityCheck(InAppWebViewController controller) {
    _stabilityTimer = Timer.periodic(AnimationConfig.longDuration, (timer) async {
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

  /// 基于DOM大小的稳定性检测（从原来的_checkDOMStability拆分出来）
  Future<void> _checkDOMStability(InAppWebViewController controller) async {
    // 使用更高效的DOM大小获取方法，只检查DOM元素数量和DOM元素属性总数，避免获取完整DOM字符串
    final domSizeResult = await controller.evaluateJavascript(
      source: """
        (function() {
          const elements = document.querySelectorAll('*');
          const elementCount = elements.length;
          let attributeCount = 0;

          // 只采样前500个元素以提高性能（从1000减少到500）
          const sampleSize = Math.min(elementCount, 500);
          for (let i = 0; i < sampleSize; i++) {
            attributeCount += elements[i].attributes.length;
          }

          // 计算文本内容大小估计值
          const bodyText = document.body ? document.body.innerText || '' : '';
          const textLength = bodyText.length;

          // 综合指标
          return {
            elementCount: elementCount,
            attributeCount: attributeCount,
            textLength: textLength,
            timestamp: Date.now()
          };
        })();
      """,
    );

    if (domSizeResult == null) return;

    try {
      final Map<String, dynamic> domMetrics = domSizeResult;

      // 如果是第一次检查，记录当前状态并返回
      if (_lastDOMSize == 0) {
        // 使用元素数和属性数的加权总和作为DOM大小的评估指标
        _lastDOMSize =
            (domMetrics['elementCount'] as int) * 10 +
            (domMetrics['attributeCount'] as int) +
            (domMetrics['textLength'] as int) ~/ 100;
        return;
      }

      // 计算当前的加权DOM大小
      final int currentSize =
          (domMetrics['elementCount'] as int) * 10 +
          (domMetrics['attributeCount'] as int) +
          (domMetrics['textLength'] as int) ~/ 100;

      // 计算变化率
      double changePercent = (currentSize - _lastDOMSize).abs() / (_lastDOMSize > 0 ? _lastDOMSize : 1);
      logger.d("[HeadlessWebView] DOM变化率: ${(changePercent * 100).toStringAsFixed(2)}%");

      // 添加启发式判断：如果DOM已经很大但变化很小，可以快速完成（新增）
      final bool isLargePage = currentSize > 10000;
      final bool hasMinimalChanges = changePercent < 0.01;

      if (changePercent < _domStabilityThreshold || (isLargePage && hasMinimalChanges)) {
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

      _lastDOMSize = currentSize;
    } catch (e) {
      logger.e("[HeadlessWebView] 解析DOM指标失败: $e");
    }
  }

  /// 处理页面内容
  Future<void> _processContent() async {
    _updateActivityTime();

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
    try {
      // 使用并行执行注入资源和脚本
      final futures = await Future.wait([
        // 注入资源和脚本
        Future(() async {
          try {
            await _baseWebView.injectResources(controller);
            return true;
          } catch (e) {
            logger.e("[HeadlessWebView] 注入资源失败: $e");
            return false;
          }
        }),

        Future(() async {
          try {
            await _baseWebView.injectCssRules(controller);
            return true;
          } catch (e) {
            logger.e("[HeadlessWebView] 注入CSS规则失败: $e");
            return false;
          }
        }),

        Future(() async {
          try {
            await _baseWebView.injectTwitterCssRules(controller);
            return true;
          } catch (e) {
            logger.e("[HeadlessWebView] 注入Twitter CSS规则失败: $e");
            return false;
          }
        }),
      ]);

      // 检查资源注入是否成功
      if (futures.contains(false)) {
        logger.w("[HeadlessWebView] 部分资源注入失败，但继续执行解析");
      }

      // 注入解析脚本
      try {
        await controller.injectJavascriptFileFromAsset(assetFilePath: "assets/js/Readability.js");
        await controller.injectJavascriptFileFromAsset(assetFilePath: "assets/js/parse_content.js");
      } catch (e) {
        logger.e("[HeadlessWebView] 注入解析脚本失败: $e");
        rethrow;
      }

      // 执行解析，使用超时保护
      final parseResult = await _executeScriptWithTimeout(controller, "parseContent()", 5000);

      if (parseResult == null) {
        logger.w("[HeadlessWebView] 解析结果为空，返回空结果");
        return HeadlessWebViewResult.empty();
      }

      // 提取解析结果
      final resultMap = parseResult as Map<dynamic, dynamic>;
      final result = _extractResultFromMap(resultMap);

      // 提取封面图URL
      String coverImageUrl = '';
      if (result['imageUrls'] != null && (result['imageUrls'] as List).isNotEmpty) {
        coverImageUrl = (result['imageUrls'] as List).first;
      }

      // 创建最终结果对象
      return HeadlessWebViewResult(
        title: result['title'] ?? '',
        excerpt: result['excerpt'] ?? '',
        htmlContent: result['htmlContent'] ?? '',
        textContent: result['textContent'] ?? '',
        publishedTime: result['publishedTime'] ?? '',
        coverImageUrl: coverImageUrl,
      );
    } catch (e) {
      logger.e("[HeadlessWebView] 解析网页内容失败: $e");
      return HeadlessWebViewResult.empty();
    }
  }

  /// 带超时保护地执行JavaScript
  Future<dynamic> _executeScriptWithTimeout(InAppWebViewController controller, String script, int timeoutMs) async {
    try {
      // 创建一个可以在超时的情况下取消的计算
      final result = await controller.evaluateJavascript(source: script).timeout(Duration(milliseconds: timeoutMs));
      return result;
    } on TimeoutException {
      logger.w("[HeadlessWebView] JavaScript执行超时: $script");
      return null;
    } catch (e) {
      logger.e("[HeadlessWebView] JavaScript执行失败: $e");
      return null;
    }
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
