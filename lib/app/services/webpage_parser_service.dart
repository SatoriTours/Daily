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
    final completedArticles = ArticleRepository.findByStatus(ArticleStatus.completed);
    for (var article in completedArticles) {
      if (_isArticleIncomplete(article)) {
        logger.i("[网页解析] 发现不完整文章 #${article.id}，重置状态为网页内容已获取");
        await _updateArticleStatus(article.id, ArticleStatus.webContentFetched);
      }
    }

    // 检查web_content_fetched状态的文章，如果标题或内容为空，改为pending状态
    final webContentFetchedArticles = ArticleRepository.findByStatus(ArticleStatus.webContentFetched);
    for (var article in webContentFetchedArticles) {
      if (article.title.isNullOrEmpty || article.content.isNullOrEmpty || article.htmlContent.isNullOrEmpty) {
        logger.i("[网页解析] 发现网页内容不完整 #${article.id}，重置状态为pending");
        await _updateArticleStatus(article.id, ArticleStatus.pending);
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
          if (article.status == ArticleStatus.webContentFetched) {
            // 网页内容已获取但AI处理未完成，保持该状态
            logger.i("[网页解析] 保持网页内容已获取状态: ${article.id}");
          } else {
            // 其他情况回退到待处理
            _updateArticleStatus(article.id, ArticleStatus.pending);
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

    // 处理待处理文章，直到没有剩余任务或达到最大并行处理数
    for (int i = 0; i < availableSlots; i++) {
      // 获取所有待处理的文章
      final pendingArticles = ArticleRepository.findAllPending();

      if (pendingArticles.isEmpty) {
        // 没有待处理的文章
        break;
      }

      ArticleModel? article;
      try {
        // 先查找网页内容已获取的文章
        article = pendingArticles.firstWhere(
          (a) => a.status == ArticleStatus.webContentFetched && !_currentProcessingArticleIds.contains(a.id),
          orElse:
              () => pendingArticles.firstWhere(
                (a) => a.status == ArticleStatus.pending && !_currentProcessingArticleIds.contains(a.id),
                orElse: () => throw Exception('No pending article found'),
              ),
        );
      } catch (e) {
        // 如果没有找到任何待处理文章，忽略异常
        logger.d("[网页解析] 没有发现待处理的文章");
        break;
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
      if (article.status == ArticleStatus.pending) {
        // 获取网页内容阶段
        await _processFetchWebContent(article);
      } else if (article.status == ArticleStatus.webContentFetched) {
        // AI处理阶段
        await _processAiContent(
          article,
          title: article.title ?? '',
          htmlContent: article.htmlContent ?? '',
          content: article.content ?? '',
          coverImageUrl: article.coverImageUrl ?? '',
        );
      } else {
        logger.w("[网页解析] 文章状态异常: ${article.status}");
        await _updateArticleStatus(article.id, ArticleStatus.pending);
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

      // 获取网页内容 - 返回记录
      final (String title, String htmlContent, String textContent, String coverImageUrl) = await _fetchWebContent(
        article.url,
      );

      // 检查获取的内容是否为空
      if (_isWebContentEmpty(title, htmlContent, textContent)) {
        logger.w("[网页解析] 获取的网页内容为空: #$articleId, URL: ${article.url}");
        throw Exception("获取的网页内容为空或不完整");
      }

      // 保存基本网页内容
      article.title = title;
      article.content = textContent;
      article.htmlContent = htmlContent;
      article.coverImageUrl = coverImageUrl;
      article.updatedAt = DateTime.now().toUtc();
      article.status = ArticleStatus.webContentFetched; // 更新文章状态为"网页内容获取完成"

      await ArticleRepository.update(article);

      logger.i("[网页解析] 网页内容获取完成: #$articleId");
      _notifyUI(articleId);

      // 将获取的数据传递给 AI 处理阶段
      await _processAiContent(
        article,
        title: title,
        htmlContent: htmlContent,
        content: textContent,
        coverImageUrl: coverImageUrl,
      );
    } catch (e) {
      logger.e("[网页解析] 获取网页内容失败: #$articleId, $e");
      // 直接标记为错误
      article.title = "加载失败";
      article.content = "无法获取网页内容：$e";
      article.updatedAt = DateTime.now().toUtc();
      article.status = ArticleStatus.error;
      await ArticleRepository.update(article);
      logger.w("[网页解析] 网页内容获取失败，标记为错误状态 #$articleId");
    }
  }

  /// 检查网页内容是否为空
  bool _isWebContentEmpty(String title, String htmlContent, String textContent) {
    // 检查关键字段
    return (htmlContent.isEmpty || htmlContent.length < 100) || // HTML内容为空或太短
        (textContent.isEmpty || textContent.length < 50) || // 文本内容为空或太短
        title.isEmpty; // 标题为空
  }

  /// 阶段2: 处理AI内容
  Future<void> _processAiContent(
    ArticleModel article, {
    required String title,
    required String htmlContent,
    required String content,
    required String coverImageUrl,
  }) async {
    final articleId = article.id;

    try {
      logger.i("[网页解析] 开始处理AI内容: #$articleId");
      _notifyUI(articleId);

      // 直接调用处理逻辑，传递所需数据
      bool success = await _processForeground(
        article,
        title: title,
        htmlContent: htmlContent,
        content: content,
        coverImageUrl: coverImageUrl,
      );

      if (success) {
        logger.i("[网页解析] AI内容处理完成，所有任务成功: #$articleId");
      } else {
        logger.w("[网页解析] AI内容处理部分失败: #$articleId");
      }

      _notifyUI(articleId);
    } catch (e, stackTrace) {
      logger.e("[网页解析] 处理AI内容失败: #$articleId, $e $stackTrace");
      // 直接标记为失败
      article.aiContent = "AI处理失败：$e";
      article.updatedAt = DateTime.now().toUtc();
      article.status = ArticleStatus.error;
      await ArticleRepository.update(article);
      rethrow; // 重新抛出异常供上层处理
    }
  }

  /// 统一内容处理 - 不再区分前台和后台 (直接调用 _processForeground)
  // Future<bool> _processArticleContent(ArticleModel article, WebpageData webpageData) async {
  //   // 直接使用前台处理模式
  //   return await _processForeground(article, webpageData);
  // }

  /// 在主线程执行并行任务
  Future<bool> _processForeground(
    ArticleModel article, {
    required String title,
    required String htmlContent,
    required String content,
    required String coverImageUrl,
  }) async {
    logger.i("[网页解析] 处理文章 #${article.id}");
    final articleId = article.id;

    // 移除 taskResults 和 processTask 辅助函数
    // 直接创建和管理 Futures

    final futures = <Future<void>>[];
    // 使用布尔标志跟踪每个任务的成功状态
    bool titleSuccess = article.aiTitle != null && article.aiTitle!.isNotEmpty;
    bool summarySuccess = article.aiContent != null && article.aiContent!.isNotEmpty;
    // 封面图片为空不一定是失败，只有在下载失败时才标记为失败
    bool imageSuccess = true; // Assume success unless download fails
    bool markdownSuccess = article.aiMarkdownContent != null && article.aiMarkdownContent!.isNotEmpty;

    // 标题处理任务 (仅当需要且未完成时)
    if (title.isNotEmpty && !titleSuccess) {
      futures.add(() async {
        try {
          final result = await _processTitle(title);
          if (result.isNotEmpty) {
            await ArticleRepository.updateField(articleId, ArticleFieldName.aiTitle, result);
            titleSuccess = true;
            logger.i("[网页解析] 标题处理完成: #$articleId");
          } else {
            logger.w("[网页解析] 标题处理结果为空: #$articleId");
            titleSuccess = false; // 结果为空视为失败
          }
        } catch (e, stackTrace) {
          logger.e("[网页解析] 标题处理失败: #$articleId, $e");
          logger.e(stackTrace);
          titleSuccess = false; // 标记为失败
        }
      }()); // 立即调用 async 函数
    }

    // 摘要处理任务 (仅当需要且未完成时)
    if (content.isNotEmpty && !summarySuccess) {
      futures.add(() async {
        try {
          final (summary, tags) = await _processSummary(content);
          if (summary.isNotEmpty) {
            await ArticleRepository.updateField(articleId, ArticleFieldName.aiContent, summary);
            await _saveTags(article, tags);
            summarySuccess = true;
            logger.i("[网页解析] 摘要和标签处理完成: #$articleId");
          } else {
            logger.w("[网页解析] 摘要处理结果为空: #$articleId");
            summarySuccess = false;
          }
        } catch (e, stackTrace) {
          logger.e("[网页解析] 摘要处理失败: #$articleId, $e");
          logger.e(stackTrace);
          summarySuccess = false;
        }
      }());
    }

    // 图片处理任务 (仅当需要且未完成时)
    // 注意: article.coverImage.isNullOrEmpty 检查本地图片是否存在
    if (coverImageUrl.isNotEmpty && article.coverImage.isNullOrEmpty) {
      futures.add(() async {
        try {
          final result = await _processImage(coverImageUrl);
          // 即使 result 为空 (例如图片无法下载但不是严重错误)，也可能不算失败
          // 这里我们假设下载方法会抛出异常如果是严重错误
          await ArticleRepository.updateField(articleId, ArticleFieldName.coverImage, result);
          imageSuccess = true; // 只要没抛异常就算成功（即使图片为空）
          logger.i("[网页解析] 图片处理完成: #$articleId");
        } catch (e, stackTrace) {
          logger.e("[网页解析] 图片处理失败: #$articleId, $e");
          logger.e(stackTrace);
          imageSuccess = false; // 下载失败则标记为失败
        }
      }());
    }

    // Markdown处理任务 (仅当需要且未完成时)
    if (htmlContent.isNotEmpty && !markdownSuccess) {
      futures.add(() async {
        try {
          final result = await _processMarkdown(htmlContent);
          if (result.isNotEmpty) {
            await ArticleRepository.updateField(articleId, ArticleFieldName.aiMarkdownContent, result);
            markdownSuccess = true;
            logger.i("[网页解析] Markdown处理完成: #$articleId");
          } else {
            logger.w("[网页解析] Markdown处理结果为空: #$articleId");
            markdownSuccess = false;
          }
        } catch (e, stackTrace) {
          logger.e("[网页解析] Markdown处理失败: #$articleId, $e");
          logger.e(stackTrace);
          markdownSuccess = false;
        }
      }());
    }

    // 如果没有需要执行的任务，直接判断当前状态
    if (futures.isEmpty) {
      logger.i("[网页解析] 无需执行AI任务: #$articleId");
    } else {
      // 并行执行所有需要执行的任务
      await Future.wait(futures);
    }

    // 检查所有任务是否都已成功完成（包括之前已完成的）
    final allTasksSucceeded = titleSuccess && summarySuccess && imageSuccess && markdownSuccess;

    // 只有当所有必需的任务都成功完成时，才更新状态为completed
    if (allTasksSucceeded) {
      await _updateArticleStatus(article.id, ArticleStatus.completed);
      logger.i("[网页解析] 所有AI任务完成，状态更新为 completed: #$articleId");
    } else {
      // 如果有任何任务失败，文章状态将保持 webContentFetched (或之前的状态)
      // _checkIncompleteArticles 会在之后检查并可能重试
      logger.w(
        "[网页解析] AI 内容处理部分失败或有任务未完成: #${article.id}. 成功状态 - title: $titleSuccess, summary: $summarySuccess, image: $imageSuccess, markdown: $markdownSuccess",
      );
    }

    _notifyUI(articleId); // 通知UI更新，无论成功与否

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
    if (imageUrl.isEmpty) return '';
    return await HttpService.i.downloadImage(imageUrl);
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

    // 标记为失败
    article.aiContent = "处理失败：$error";
    article.status = ArticleStatus.error;
    article.updatedAt = DateTime.now().toUtc();
    ArticleRepository.update(article);
    logger.w("[网页解析] 处理失败，标记为错误状态 #$articleId");
  }

  /// 获取网页内容
  Future<(String title, String htmlContent, String textContent, String coverImageUrl)> _fetchWebContent(
    String? url,
  ) async {
    if (url.isNullOrEmpty) {
      // 返回一个包含空字符串的无名记录
      return ('', '', '', '');
    }

    try {
      final headlessWebView = HeadlessWebView();
      final result = await headlessWebView.loadAndParseUrl(url!);

      // 直接返回包含所需数据的无名记录
      return (result.title, result.htmlContent, result.textContent, result.coverImageUrl);
    } catch (e, stackTrace) {
      logger.e("[网页解析] 获取网页内容失败: $e $stackTrace");
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
  Future<void> _updateArticleStatus(int articleID, String status) async {
    await ArticleRepository.updateField(articleID, ArticleFieldName.status, status);
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
          article.aiTitle = '';
          article.aiContent = '';
          article.aiMarkdownContent = '';
          article.coverImage = '';
          article.status = ArticleStatus.pending;
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
        'status': ArticleStatus.pending,
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
