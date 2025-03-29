import 'dart:async';
import 'package:flutter/material.dart';
import 'package:daily_satori/app/components/webview/headless_webview.dart';
import 'package:daily_satori/app/modules/articles/controllers/articles_controller.dart';

import 'package:daily_satori/app/services/ai_service/ai_service.dart';
import 'package:daily_satori/app/services/logger_service.dart';
import 'package:daily_satori/app/repositories/article_repository.dart';
import 'package:daily_satori/app/models/article_model.dart';
import 'package:daily_satori/app/repositories/tag_repository.dart';
import 'package:daily_satori/app/services/http_service.dart';
import 'package:daily_satori/app/utils/string_extensions.dart';
import 'package:get/get.dart';

/// 网页解析服务 - 负责协调网页内容获取、AI处理和持久化
class WebpageParserService with WidgetsBindingObserver {
  // 单例模式实现
  WebpageParserService._privateConstructor();
  static final WebpageParserService _instance = WebpageParserService._privateConstructor();
  static WebpageParserService get i => _instance;

  // 服务状态管理
  bool _isInitialized = false;
  final Set<int> _currentProcessingArticleIds = <int>{};
  static const int _maxConcurrentProcessing = 5;
  Timer? _processingTimer;

  // 任务队列和重试管理
  final Map<int, int> _retryCount = {};
  final List<int> _retryQueue = [];
  static const int _maxRetries = 3;

  // 状态常量定义
  static const String statusPending = 'pending';
  static const String statusWebContentFetched = 'web_content_fetched';
  static const String statusCompleted = 'completed';
  static const String statusError = 'error';

  /// 初始化服务
  Future<void> init() async {
    if (_isInitialized) return;

    logger.i("[网页解析] 初始化中...");
    WidgetsBinding.instance.addObserver(this);
    _startProcessingTimer();
    _checkNextArticle();
    _isInitialized = true;
    logger.i("[网页解析] 初始化完成");
  }

  /// 释放资源
  void dispose() {
    _stopProcessingTimer();
    WidgetsBinding.instance.removeObserver(this);
    _isInitialized = false;
    logger.i("[网页解析] 已释放资源");
  }

  /// 处理应用生命周期变化
  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    logger.i("[网页解析] 应用生命周期状态变更: $state");

    switch (state) {
      case AppLifecycleState.resumed:
        _startProcessingTimer();
        _checkIncompleteArticles(); // 首先检查不完整的文章
        _checkNextArticle();
        break;
      case AppLifecycleState.paused:
        _stopProcessingTimer();
        break;
      case AppLifecycleState.detached:
        _saveCurrentArticleState();
        _stopProcessingTimer();
        break;
      default:
        break;
    }
  }

  /// 检查不完整的文章，重新处理那些处理失败或部分失败的文章
  Future<void> _checkIncompleteArticles() async {
    logger.i("[网页解析] 检查不完整文章");

    if (_currentProcessingArticleIds.length >= _maxConcurrentProcessing) {
      logger.d("[网页解析] 当前已达到最大并行处理数量，稍后再检查不完整文章");
      return;
    }

    // 获取所有文章，并筛选出状态为completed的不完整文章
    final completedArticles = ArticleRepository.findByStatus(statusCompleted);
    for (var article in completedArticles) {
      if (_isArticleIncomplete(article)) {
        logger.i("[网页解析] 发现不完整文章 #${article.id}，重置状态为网页内容已获取");
        await _updateArticleStatus(article, statusWebContentFetched);
        // 添加到重试队列中优先处理
        _scheduleRetry(article.id);
      }
    }

    // 检查web_content_fetched状态的文章，如果标题或内容为空，改为pending状态
    final webContentFetchedArticles = ArticleRepository.findByStatus(statusWebContentFetched);
    for (var article in webContentFetchedArticles) {
      if (article.title.isNullOrEmpty || article.content.isNullOrEmpty || article.htmlContent.isNullOrEmpty) {
        logger.i("[网页解析] 发现网页内容不完整 #${article.id}，重置状态为pending");
        await _updateArticleStatus(article, statusPending);
        // 添加到重试队列中优先处理
        _scheduleRetry(article.id);
      }
    }
  }

  /// 检查文章是否不完整（关键字段为空）
  bool _isArticleIncomplete(ArticleModel article) {
    // 检查关键字段是否为空
    return article.aiTitle.isNullOrEmpty ||
        article.aiContent.isNullOrEmpty ||
        article.aiMarkdownContent.isNullOrEmpty ||
        (article.coverImageUrl.isNotNullOrEmpty && article.coverImage.isNullOrEmpty);
  }

  /// 保存网页（对外API）
  Future<ArticleModel> saveWebpage({
    required String url,
    required String comment,
    bool isUpdate = false,
    int articleID = 0,
  }) async {
    logger.i("[网页解析] 保存网页: $url");

    // 验证输入
    if (!isUpdate && await ArticleRepository.isArticleExists(url)) {
      throw Exception('网页已存在');
    }
    if (isUpdate && articleID <= 0) {
      throw Exception('网页不存在，无法更新');
    }

    // 创建文章
    final articleModel = await _createInitialArticle(
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
    logger.i("[网页解析] 启动定时处理器");
  }

  /// 停止处理定时器
  void _stopProcessingTimer() {
    _processingTimer?.cancel();
    _processingTimer = null;
    logger.i("[网页解析] 停止定时处理器");
  }

  /// 保存当前处理文章状态
  void _saveCurrentArticleState() {
    for (final articleId in _currentProcessingArticleIds.toList()) {
      try {
        final article = ArticleRepository.find(articleId);
        if (article != null) {
          // 根据当前处理阶段回退到适当状态
          if (article.status == statusWebContentFetched) {
            // 网页内容已获取但AI处理未完成，保持该状态
            logger.i("[网页解析] 保持网页内容已获取状态: ${article.id}");
          } else {
            // 其他情况回退到待处理
            _updateArticleStatus(article, statusPending);
            logger.i("[网页解析] 保存处理中文章状态: ${article.id}");
          }
        }
      } catch (e) {
        logger.e("[网页解析] 保存处理状态失败: $e");
      }
    }
  }

  /// 处理下一篇文章
  Future<void> _checkNextArticle() async {
    // 判断是否达到最大并行处理数
    if (_currentProcessingArticleIds.length >= _maxConcurrentProcessing) {
      return;
    }

    // 可以处理的并行任务数
    final availableSlots = _maxConcurrentProcessing - _currentProcessingArticleIds.length;

    // 处理重试队列和待处理文章，直到没有剩余任务或达到最大并行处理数
    for (int i = 0; i < availableSlots; i++) {
      // 先处理重试队列中的文章
      ArticleModel? article = _getNextRetryArticle();

      // 然后尝试获取数据库中的待处理文章
      if (article == null) {
        // 获取所有待处理的文章并筛选
        final pendingArticles = ArticleRepository.findAllPending();

        if (pendingArticles.isNotEmpty) {
          try {
            // 先查找网页内容已获取的文章
            article = pendingArticles.firstWhere(
              (a) => a.status == statusWebContentFetched && !_currentProcessingArticleIds.contains(a.id),
              orElse:
                  () => pendingArticles.firstWhere(
                    (a) => a.status == statusPending && !_currentProcessingArticleIds.contains(a.id),
                    orElse: () => throw Exception('No pending article found'),
                  ),
            );
          } catch (e) {
            // 如果没有找到任何待处理文章，忽略异常
            logger.d("[网页解析] 没有发现待处理的文章");
            break;
          }
        } else {
          // 没有待处理的文章
          break;
        }
      }

      if (!_currentProcessingArticleIds.contains(article.id)) {
        _processArticle(article);
      } else {
        // 没有可以处理的文章或已经在处理
        break;
      }
    }
  }

  /// 处理文章 - 根据文章状态执行不同的处理逻辑
  Future<void> _processArticle(ArticleModel article) async {
    final articleId = article.id;
    _currentProcessingArticleIds.add(articleId);

    logger.i("[网页解析] 处理文章 #$articleId: ${article.url}, 状态: ${article.status}");

    try {
      // 根据文章状态执行不同处理逻辑
      if (article.status == statusPending) {
        // 获取网页内容阶段
        await _processFetchWebContent(article);
      } else if (article.status == statusWebContentFetched) {
        // AI处理阶段
        await _processAiContent(article);
      } else {
        logger.w("[网页解析] 文章状态异常: ${article.status}");
        await _updateArticleStatus(article, statusPending);
      }
    } catch (e, stackTrace) {
      logger.e("[网页解析] 文章 #$articleId 处理失败: $e");
      logger.e(stackTrace);

      // 处理错误
      _handleProcessingError(article, e);
    } finally {
      _currentProcessingArticleIds.remove(articleId);
      // 处理完成后，尝试处理下一篇文章
      Future.delayed(const Duration(seconds: 1), _checkNextArticle);
    }
  }

  /// 阶段1: 获取网页内容
  Future<void> _processFetchWebContent(ArticleModel article) async {
    final articleId = article.id;

    try {
      logger.i("[网页解析] 开始获取网页内容: #$articleId");
      _notifyUI(articleId);

      // 获取网页内容
      final webpageData = await _fetchWebContent(article.url);

      // 检查获取的内容是否为空
      if (_isWebContentEmpty(webpageData)) {
        logger.w("[网页解析] 获取的网页内容为空: #$articleId, URL: ${article.url}");
        throw Exception("获取的网页内容为空或不完整");
      }

      // 保存基本网页内容
      article.title = webpageData.title;
      article.content = webpageData.textContent;
      article.htmlContent = webpageData.htmlContent;
      article.coverImageUrl = webpageData.coverImageUrl;
      article.updatedAt = DateTime.now().toUtc();
      article.status = statusWebContentFetched; // 更新文章状态为"网页内容获取完成"

      await ArticleRepository.update(article);

      logger.i("[网页解析] 网页内容获取完成: #$articleId");
      _notifyUI(articleId);

      await _processAiContent(article);
    } catch (e) {
      logger.e("[网页解析] 获取网页内容失败: #$articleId, $e");
      if (_shouldRetry(articleId)) {
        _scheduleRetry(articleId);
      } else {
        // 重试次数已用完
        article.title = "加载失败";
        article.content = "无法获取网页内容：$e";
        article.updatedAt = DateTime.now().toUtc();
        article.status = statusError;
        await ArticleRepository.update(article);
        logger.w("[网页解析] 网页内容获取重试次数已用完，标记为错误状态 #$articleId");
      }
    }
  }

  /// 检查网页内容是否为空
  bool _isWebContentEmpty(WebpageData data) {
    // 检查关键字段
    return (data.htmlContent.isEmpty || data.htmlContent.length < 100) || // HTML内容为空或太短
        (data.textContent.isEmpty || data.textContent.length < 50) || // 文本内容为空或太短
        data.title.isEmpty; // 标题为空
  }

  /// 阶段2: 处理AI内容
  Future<void> _processAiContent(ArticleModel article) async {
    final articleId = article.id;

    try {
      logger.i("[网页解析] 开始处理AI内容: #$articleId");
      _notifyUI(articleId);

      // 创建网页数据对象，确保所有字段都是非空字符串
      final webpageData = WebpageData(
        title: article.title ?? '',
        excerpt: '',
        htmlContent: article.htmlContent ?? '',
        textContent: article.content ?? '',
        publishedTime: '',
        coverImageUrl: article.coverImageUrl ?? '',
      );

      // 统一处理，根据应用状态决定在前台或后台执行
      bool success = await _processArticleContent(article, webpageData);

      if (success) {
        logger.i("[网页解析] AI内容处理完成，所有任务成功: #$articleId");
      } else {
        logger.w("[网页解析] AI内容处理部分失败: #$articleId");
      }

      _notifyUI(articleId);
    } catch (e) {
      logger.e("[网页解析] 处理AI内容失败: #$articleId, $e");
      if (_shouldRetry(articleId)) {
        _scheduleRetry(articleId);
      } else {
        // 重试次数已用完
        article.aiContent = "AI处理失败：$e";
        article.updatedAt = DateTime.now().toUtc();
        article.status = statusError;
        await ArticleRepository.update(article);
        logger.w("[网页解析] AI处理重试次数已用完，标记为错误状态 #$articleId");
        throw e; // 重新抛出异常供上层处理
      }
    }
  }

  /// 统一内容处理 - 不再区分前台和后台
  Future<bool> _processArticleContent(ArticleModel article, WebpageData webpageData) async {
    // 直接使用前台处理模式
    return await _processForeground(article, webpageData);
  }

  /// 在主线程执行并行任务
  Future<bool> _processForeground(ArticleModel article, WebpageData webpageData) async {
    logger.i("[网页解析] 处理文章 #${article.id}");
    final articleId = article.id;

    // 创建任务结果跟踪
    final taskResults = <String, bool>{
      'title': article.aiTitle != null && article.aiTitle!.isNotEmpty,
      'summary': article.aiContent != null && article.aiContent!.isNotEmpty,
      'image': article.coverImage != null && article.coverImage!.isNotEmpty,
      'markdown': article.aiMarkdownContent != null && article.aiMarkdownContent!.isNotEmpty,
    };

    // 定义统一的任务处理函数
    Future<void> processTask(
      String taskName,
      Future<dynamic> Function() processor,
      Future<void> Function(dynamic result) updater,
      bool skipIfDone,
    ) async {
      // 如果任务已完成且允许跳过，则跳过该任务
      if (skipIfDone && taskResults[taskName] == true) {
        logger.i("[网页解析] 跳过已完成的$taskName任务: #$articleId");
        return;
      }

      try {
        final result = await processor();
        // 检查结果是否为空字符串，如果是则视为失败
        if (result is String && result.isEmpty) {
          logger.w("[网页解析] $taskName处理结果为空: #$articleId");
          taskResults[taskName] = false;
          return;
        }
        await updater(result);
        logger.i("[网页解析] $taskName处理完成: #$articleId");
        taskResults[taskName] = true;
      } catch (e) {
        logger.e("[网页解析] $taskName处理失败: #$articleId, $e");
        taskResults[taskName] = false;
      }
    }

    // 创建处理任务列表
    final tasks = [
      // 标题处理任务
      processTask(
        'title',
        () => _processTitle(webpageData.title),
        (result) => ArticleRepository.updateField(articleId, 'aiTitle', result),
        true,
      ),

      // 摘要处理任务
      processTask('summary', () => _processSummary(webpageData.textContent), (result) async {
        await ArticleRepository.updateField(articleId, 'aiContent', result.$1);
        await _saveTags(article, result.$2);
      }, true),

      // 图片处理任务
      processTask(
        'image',
        () => _processImage(webpageData.coverImageUrl),
        (result) => ArticleRepository.updateField(articleId, 'coverImage', result),
        true,
      ),

      // Markdown处理任务
      processTask(
        'markdown',
        () => _processMarkdown(webpageData.htmlContent),
        (result) => ArticleRepository.updateField(articleId, 'aiMarkdownContent', result),
        true,
      ),
    ];

    // 并行执行所有任务
    await Future.wait(tasks);

    // 检查所有任务是否成功
    final allTasksSucceeded = taskResults.values.every((success) => success);

    // 只有当所有任务都成功完成时，才更新状态为completed
    if (allTasksSucceeded) {
      await ArticleRepository.updateField(article.id, 'status', statusCompleted);
    }

    return allTasksSucceeded;
  }

  /// 处理标题
  Future<String> _processTitle(String title) async {
    try {
      var aiTitle = await AiService.i.translate(title.trim());
      return aiTitle.length >= 50 ? await AiService.i.summarizeOneLine(aiTitle) : aiTitle;
    } catch (e) {
      logger.e("[网页解析] 标题处理失败: $e");
      rethrow;
    }
  }

  /// 处理摘要
  Future<(String, List<String>)> _processSummary(String content) async {
    try {
      return await AiService.i.summarize(content.trim());
    } catch (e) {
      logger.e("[网页解析] 内容摘要处理失败: $e");
      rethrow;
    }
  }

  /// 处理图片
  Future<String> _processImage(String imageUrl) async {
    try {
      return await HttpService.i.downloadImage(imageUrl);
    } catch (e) {
      logger.e("[网页解析] 图片处理失败: $e");
      rethrow;
    }
  }

  /// 处理Markdown
  Future<String> _processMarkdown(String html) async {
    try {
      return await AiService.i.convertHtmlToMarkdown(html);
    } catch (e) {
      logger.e("[网页解析] Markdown处理失败: $e");
      rethrow;
    }
  }

  /// 保存标签
  Future<void> _saveTags(ArticleModel article, List<String> tagNames) async {
    try {
      article.tags.clear();
      for (var tagName in tagNames) {
        await TagRepository.addTagToArticle(article, tagName);
      }
    } catch (e) {
      logger.e("[网页解析] 保存标签失败: $e");
      rethrow;
    }
  }

  /// 处理错误
  void _handleProcessingError(ArticleModel article, dynamic error) {
    final articleId = article.id;

    if (_shouldRetry(articleId)) {
      _scheduleRetry(articleId);
      // 保持当前状态，等待重试
      logger.i("[网页解析] 安排重试 #$articleId，当前重试次数: ${_retryCount[articleId]}");
    } else {
      // 重试次数用完，标记为失败
      article.aiContent = "处理失败：$error";
      _updateArticleStatus(article, statusError);
      article.updatedAt = DateTime.now().toUtc();
      ArticleRepository.update(article);
      logger.w("[网页解析] 重试次数已用完，标记为失败 #$articleId");
    }
  }

  /// 获取网页内容
  Future<WebpageData> _fetchWebContent(String? url) async {
    if (url.isNullOrEmpty) {
      return WebpageData.empty();
    }

    try {
      final headlessWebView = HeadlessWebView();
      final result = await headlessWebView.loadAndParseUrl(url!);

      return WebpageData(
        title: result.title,
        excerpt: result.excerpt,
        htmlContent: result.htmlContent,
        textContent: result.textContent,
        publishedTime: result.publishedTime,
        coverImageUrl: result.coverImageUrl,
      );
    } catch (e) {
      logger.e("[网页解析] 获取网页内容失败: $e");
      rethrow;
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

  /// 更新文章状态
  Future<void> _updateArticleStatus(ArticleModel article, String status) async {
    article.setStatus(status);
    await article.save();
  }

  /// 获取下一个需要重试的文章
  ArticleModel? _getNextRetryArticle() {
    if (_retryQueue.isEmpty) return null;

    // 找到第一个不在当前处理列表中的文章
    for (int i = 0; i < _retryQueue.length; i++) {
      final articleId = _retryQueue[i];
      if (!_currentProcessingArticleIds.contains(articleId)) {
        _retryQueue.removeAt(i);
        return ArticleRepository.find(articleId);
      }
    }

    return null;
  }

  /// 是否应该重试
  bool _shouldRetry(int articleId) {
    final count = _retryCount[articleId] ?? 0;
    return count < _maxRetries;
  }

  /// 安排重试
  void _scheduleRetry(int articleId) {
    _retryCount[articleId] = (_retryCount[articleId] ?? 0) + 1;
    if (!_retryQueue.contains(articleId)) {
      _retryQueue.add(articleId);
    }
  }

  /// 创建初始文章
  Future<ArticleModel> _createInitialArticle({
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
          article.status = statusPending;
          article.updatedAt = DateTime.now().toUtc();
          article.htmlContent = '';
          article.content = '';
          article.aiContent = '';
          article.aiTitle = '';
          article.aiMarkdownContent = '';
          article.coverImage = '';
          article.coverImageUrl = '';
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
        'status': statusPending,
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
      logger.e("[网页解析] 创建文章失败: $e");
      rethrow;
    }
  }
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
