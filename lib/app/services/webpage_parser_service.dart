import 'dart:async';
import 'dart:isolate';
import 'package:flutter/material.dart';
import 'package:daily_satori/app/components/webview/headless_webview.dart';
import 'package:daily_satori/app/modules/articles/controllers/articles_controller.dart';

import 'package:daily_satori/app/services/ai_service/ai_service.dart';
import 'package:daily_satori/app/services/logger_service.dart';
import 'package:daily_satori/app/repositories/article_repository.dart';
import 'package:daily_satori/app/models/article_model.dart';
import 'package:daily_satori/app/repositories/tag_repository.dart';
import 'package:daily_satori/app/services/http_service.dart';
import 'package:get/get.dart';

/// 网页解析服务 - 负责协调网页内容获取、AI处理和持久化
class WebpageParserService with WidgetsBindingObserver {
  // 单例模式实现
  WebpageParserService._privateConstructor();
  static final WebpageParserService _instance = WebpageParserService._privateConstructor();
  static WebpageParserService get i => _instance;

  // 服务状态管理
  bool _isInitialized = false;
  int? _currentProcessingArticleId;
  Timer? _processingTimer;
  AppLifecycleState _lifecycleState = AppLifecycleState.resumed;

  // 任务队列和重试管理
  final ArticleQueue _articleQueue = ArticleQueue();

  // 后台处理组件
  final BackgroundProcessor _backgroundProcessor = BackgroundProcessor();

  /// 初始化服务
  Future<void> init() async {
    if (_isInitialized) return;

    logger.i("[WebpageParserService] 初始化中...");
    WidgetsBinding.instance.addObserver(this);
    _startProcessingTimer();
    _checkNextArticle();
    _isInitialized = true;
    logger.i("[WebpageParserService] 初始化完成");
  }

  /// 释放资源
  void dispose() {
    _stopProcessingTimer();
    _backgroundProcessor.cancel();
    WidgetsBinding.instance.removeObserver(this);
    _isInitialized = false;
    logger.i("[WebpageParserService] 已释放资源");
  }

  /// 处理应用生命周期变化
  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    logger.i("[WebpageParserService] 应用生命周期状态变更: $state");
    _lifecycleState = state;

    switch (state) {
      case AppLifecycleState.resumed:
        _startProcessingTimer();
        _checkNextArticle();
        break;
      case AppLifecycleState.paused:
        _stopProcessingTimer();
        break;
      case AppLifecycleState.detached:
        _saveCurrentArticleState();
        _stopProcessingTimer();
        _backgroundProcessor.cancel();
        break;
      default:
        break;
    }
  }

  /// 保存网页（对外API）
  Future<ArticleModel> saveWebpage({
    required String url,
    required String comment,
    bool isUpdate = false,
    int articleID = 0,
  }) async {
    logger.i("[WebpageParserService] 保存网页: $url");

    // 验证输入
    if (!isUpdate && await ArticleRepository.isArticleExists(url)) {
      throw Exception('网页已存在');
    }
    if (isUpdate && articleID <= 0) {
      throw Exception('网页不存在，无法更新');
    }

    // 创建文章
    final articleModel = await ArticleFactory.createInitialArticle(
      url: url,
      comment: comment,
      isUpdate: isUpdate,
      articleID: articleID,
    );

    // 立即触发处理
    _checkNextArticle();
    return articleModel;
  }

  // ====================== 私有方法 ======================

  /// 启动处理定时器
  void _startProcessingTimer() {
    if (_processingTimer != null && _processingTimer!.isActive) return;

    _processingTimer = Timer.periodic(const Duration(seconds: 30), (_) => _checkNextArticle());
    logger.i("[WebpageParserService] 启动定时处理器");
  }

  /// 停止处理定时器
  void _stopProcessingTimer() {
    _processingTimer?.cancel();
    _processingTimer = null;
    logger.i("[WebpageParserService] 停止定时处理器");
  }

  /// 保存当前处理文章状态
  void _saveCurrentArticleState() {
    if (_currentProcessingArticleId != null) {
      try {
        final article = ArticleRepository.find(_currentProcessingArticleId!);
        if (article != null && article.status == 'processing') {
          ArticleStatusManager.updateStatus(article, 'pending');
          logger.i("[WebpageParserService] 保存处理中文章状态: ${article.id}");
        }
      } catch (e) {
        logger.e("[WebpageParserService] 保存处理状态失败: $e");
      }
    }
  }

  /// 处理下一篇文章
  Future<void> _checkNextArticle() async {
    if (_currentProcessingArticleId != null) {
      return;
    }

    ArticleModel? article = _articleQueue.getNextRetryArticle();

    if (article == null) {
      article = ArticleRepository.findLastPending();
    }

    if (article != null) {
      _processArticle(article);
    }
  }

  /// 处理文章
  Future<void> _processArticle(ArticleModel article) async {
    final articleId = article.id;
    _currentProcessingArticleId = articleId;

    logger.i("[WebpageParserService] 处理文章 #$articleId: ${article.url}");

    try {
      // 更新状态
      await ArticleStatusManager.updateStatus(article, 'processing');
      _notifyUI(articleId);

      // 获取网页内容
      final webpageData = await _fetchWebContent(article.url);

      // 根据应用状态选择处理方式
      final processingResult = await _processContent(article, webpageData);

      // 更新文章
      await ArticleUpdater.updateWithProcessedData(
        article: article,
        webpageData: webpageData,
        processingResult: processingResult,
      );

      logger.i("[WebpageParserService] 文章 #$articleId 处理完成");
      _notifyUI(articleId);
    } catch (e, stackTrace) {
      logger.e("[WebpageParserService] 文章 #$articleId 处理失败: $e");

      if (_articleQueue.shouldRetry(articleId)) {
        _articleQueue.scheduleRetry(articleId);
        ArticleStatusManager.updateStatus(article, 'pending');
      } else {
        article.aiContent = "处理失败：$e";
        await ArticleStatusManager.updateStatus(article, 'error');
        article.updatedAt = DateTime.now().toUtc();
        await ArticleRepository.update(article);
      }
    } finally {
      _currentProcessingArticleId = null;
      Future.delayed(const Duration(seconds: 1), _checkNextArticle);
    }
  }

  /// 获取网页内容
  Future<WebpageData> _fetchWebContent(String? url) async {
    if (url == null || url.isEmpty) {
      return WebpageData.empty();
    }

    try {
      final headlessWebView = HeadlessWebView();
      final result = await headlessWebView.loadAndParseUrl(url);

      return WebpageData(
        title: result.title,
        excerpt: result.excerpt,
        htmlContent: result.htmlContent,
        textContent: result.textContent,
        publishedTime: result.publishedTime,
        coverImageUrl: result.coverImageUrl,
      );
    } catch (e) {
      logger.e("[WebpageParserService] 获取网页内容失败: $e");
      rethrow;
    }
  }

  /// 处理内容
  Future<ProcessingResult> _processContent(ArticleModel article, WebpageData webpageData) async {
    // 前台模式 - 并行处理
    if (_lifecycleState == AppLifecycleState.resumed) {
      return await ContentProcessor.processInForeground(webpageData);
    }
    // 后台模式 - 隔离处理
    else {
      return await _backgroundProcessor.process(article.id, webpageData);
    }
  }

  /// 通知UI更新
  void _notifyUI(int articleId) {
    try {
      Get.find<ArticlesController>().updateArticle(articleId);
    } catch (e) {
      // 控制器不存在时静默处理
    }
  }
}

/// 文章队列管理
class ArticleQueue {
  final List<int> _retryQueue = [];
  final Map<int, int> _retryCount = {};
  static const int _maxRetries = 3;

  /// 获取下一个需要重试的文章
  ArticleModel? getNextRetryArticle() {
    if (_retryQueue.isEmpty) return null;

    final articleId = _retryQueue.removeAt(0);
    return ArticleRepository.find(articleId);
  }

  /// 是否应该重试
  bool shouldRetry(int articleId) {
    final count = _retryCount[articleId] ?? 0;
    return count < _maxRetries;
  }

  /// 安排重试
  void scheduleRetry(int articleId) {
    _retryCount[articleId] = (_retryCount[articleId] ?? 0) + 1;
    if (!_retryQueue.contains(articleId)) {
      _retryQueue.add(articleId);
    }
  }

  /// 清除重试记录
  void clearRetry(int articleId) {
    _retryCount.remove(articleId);
  }
}

/// 文章工厂
class ArticleFactory {
  /// 创建初始文章
  static Future<ArticleModel> createInitialArticle({
    required String url,
    required String comment,
    required bool isUpdate,
    required int articleID,
  }) async {
    try {
      // 如果是更新模式
      if (isUpdate && articleID > 0) {
        final article = ArticleRepository.find(articleID);
        if (article != null) {
          article.comment = comment;
          await ArticleStatusManager.updateStatus(article, 'pending');
          article.updatedAt = DateTime.now().toUtc();
          await ArticleRepository.update(article);
          return article;
        }
      }

      // 创建新文章
      final now = DateTime.now().toUtc();
      final data = {
        'title': '正在加载...',
        'aiTitle': '',
        'content': '',
        'aiContent': '',
        'htmlContent': '',
        'url': url,
        'aiMarkdownContent': '',
        'coverImage': '',
        'coverImageUrl': '',
        'pubDate': now,
        'createdAt': now,
        'updatedAt': now,
        'comment': comment,
        'status': 'pending',
      };

      final articleModel = ArticleRepository.createArticleModel(data);
      final id = await ArticleRepository.create(articleModel);

      if (id <= 0) {
        throw Exception("创建文章失败");
      }

      final savedArticle = ArticleRepository.find(id);
      if (savedArticle == null) {
        throw Exception("无法找到刚创建的文章: $id");
      }

      return savedArticle;
    } catch (e) {
      logger.e("[ArticleFactory] 创建文章失败: $e");
      rethrow;
    }
  }
}

/// 文章状态管理
class ArticleStatusManager {
  /// 更新文章状态
  static Future<void> updateStatus(ArticleModel article, String status) async {
    article.setStatus(status);
    await article.save();
  }
}

/// 文章更新器
class ArticleUpdater {
  /// 使用处理结果更新文章
  static Future<void> updateWithProcessedData({
    required ArticleModel article,
    required WebpageData webpageData,
    required ProcessingResult processingResult,
  }) async {
    // 更新基本数据
    article.title = webpageData.title;
    article.aiTitle = processingResult.aiTitle;
    article.content = webpageData.textContent;
    article.aiContent = processingResult.aiContent;
    article.htmlContent = webpageData.htmlContent;
    article.aiMarkdownContent = processingResult.markdown;
    article.coverImage = processingResult.imagePath;
    article.coverImageUrl = webpageData.coverImageUrl;
    article.updatedAt = DateTime.now().toUtc();
    article.status = 'completed';

    // 保存标签
    await _saveTags(article, processingResult.tags);

    // 保存文章
    await ArticleRepository.update(article);
  }

  /// 保存标签
  static Future<void> _saveTags(ArticleModel article, List<String> tagNames) async {
    try {
      article.tags.clear();
      for (var tagName in tagNames) {
        await TagRepository.addTagToArticle(article, tagName);
      }
    } catch (e) {
      logger.e("[ArticleUpdater] 保存标签失败: $e");
    }
  }
}

/// 内容处理器
class ContentProcessor {
  /// 前台处理
  static Future<ProcessingResult> processInForeground(WebpageData webpageData) async {
    // 并行处理
    final results = await Future.wait([
      _processTitle(webpageData.title),
      _processSummary(webpageData.textContent),
      _processImage(webpageData.coverImageUrl),
      _processMarkdown(webpageData.htmlContent),
    ]);

    return ProcessingResult(
      aiTitle: results[0] as String,
      aiContent: (results[1] as (String, List<String>)).$1,
      tags: (results[1] as (String, List<String>)).$2,
      imagePath: (results[2] as String),
      markdown: results[3] as String,
    );
  }

  /// 处理标题
  static Future<String> _processTitle(String title) async {
    try {
      var aiTitle = await AiService.i.translate(title.trim());
      return aiTitle.length >= 50 ? await AiService.i.summarizeOneLine(aiTitle) : aiTitle;
    } catch (e) {
      logger.e("[ContentProcessor] 标题处理失败: $e");
      return '';
    }
  }

  /// 处理摘要
  static Future<(String, List<String>)> _processSummary(String content) async {
    try {
      return await AiService.i.summarize(content.trim());
    } catch (e) {
      logger.e("[ContentProcessor] 内容摘要处理失败: $e");
      return ('', const <String>[]);
    }
  }

  /// 处理图片
  static Future<String> _processImage(String imageUrl) async {
    try {
      return await HttpService.i.downloadImage(imageUrl);
    } catch (e) {
      logger.e("[ContentProcessor] 图片处理失败: $e");
      return '';
    }
  }

  /// 处理Markdown
  static Future<String> _processMarkdown(String html) async {
    try {
      return await AiService.i.convertHtmlToMarkdown(html);
    } catch (e) {
      logger.e("[ContentProcessor] Markdown处理失败: $e");
      return '';
    }
  }
}

/// 后台处理器
class BackgroundProcessor {
  Isolate? _isolate;
  ReceivePort? _receivePort;

  /// 处理内容
  Future<ProcessingResult> process(int articleId, WebpageData data) async {
    try {
      _receivePort = ReceivePort();

      final processingData = _IsolateData(
        articleId: articleId,
        title: data.title,
        textContent: data.textContent,
        htmlContent: data.htmlContent,
        coverImageUrl: data.coverImageUrl,
      );

      _isolate = await Isolate.spawn(_isolateProcess, [_receivePort!.sendPort, processingData]);

      final result = await _receivePort!.first as Map<String, dynamic>;

      if (result['error'] != null) {
        throw Exception(result['error']);
      }

      return ProcessingResult(
        aiTitle: result['aiTitle'] as String,
        aiContent: result['aiContent'] as String,
        tags: List<String>.from(result['tags'] ?? []),
        imagePath: result['imagePath'] as String,
        markdown: result['markdown'] as String,
      );
    } finally {
      cancel();
    }
  }

  /// 取消处理
  void cancel() {
    _isolate?.kill(priority: Isolate.immediate);
    _isolate = null;
    _receivePort?.close();
    _receivePort = null;
  }

  /// Isolate处理函数
  static Future<void> _isolateProcess(List<dynamic> args) async {
    final SendPort sendPort = args[0];
    final _IsolateData data = args[1];

    final result = <String, dynamic>{};

    try {
      // 获取服务实例
      final aiService = AiService.i;
      final httpService = HttpService.i;

      // 标题处理
      String aiTitle = '';
      try {
        aiTitle = await aiService.translate(data.title.trim());
        if (aiTitle.length >= 50) {
          aiTitle = await aiService.summarizeOneLine(aiTitle);
        }
      } catch (_) {}

      // 内容处理
      String aiContent = '';
      List<String> tags = [];
      try {
        final summarizeResult = await aiService.summarize(data.textContent.trim());
        aiContent = summarizeResult.$1;
        tags = summarizeResult.$2;
      } catch (_) {}

      // Markdown处理
      String markdown = '';
      try {
        markdown = await aiService.convertHtmlToMarkdown(data.htmlContent);
      } catch (_) {}

      // 图片处理
      String imagePath = '';
      try {
        imagePath = await httpService.downloadImage(data.coverImageUrl);
      } catch (_) {}

      // 设置结果
      result['aiTitle'] = aiTitle;
      result['aiContent'] = aiContent;
      result['tags'] = tags;
      result['imagePath'] = imagePath;
      result['markdown'] = markdown;
    } catch (e) {
      result['error'] = e.toString();
    }

    // 发送结果
    sendPort.send(result);
  }
}

/// 处理结果
class ProcessingResult {
  final String aiTitle;
  final String aiContent;
  final List<String> tags;
  final String imagePath;
  final String markdown;

  ProcessingResult({
    required this.aiTitle,
    required this.aiContent,
    required this.tags,
    required this.imagePath,
    required this.markdown,
  });
}

/// Isolate数据
class _IsolateData {
  final int articleId;
  final String title;
  final String textContent;
  final String htmlContent;
  final String coverImageUrl;

  _IsolateData({
    required this.articleId,
    required this.title,
    required this.textContent,
    required this.htmlContent,
    required this.coverImageUrl,
  });
}

/// 网页数据类
class WebpageData {
  final String title;
  final String excerpt;
  final String htmlContent;
  final String textContent;
  final String publishedTime;
  final String coverImageUrl;

  WebpageData({
    required this.title,
    required this.excerpt,
    required this.htmlContent,
    required this.textContent,
    required this.publishedTime,
    required this.coverImageUrl,
  });

  /// 创建空的网页数据
  factory WebpageData.empty() {
    return WebpageData(title: '', excerpt: '', htmlContent: '', textContent: '', publishedTime: '', coverImageUrl: '');
  }
}
