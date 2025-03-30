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
  // ====================== 单例实现 ======================
  WebpageParserService._privateConstructor();
  static final WebpageParserService _instance = WebpageParserService._privateConstructor();
  static WebpageParserService get i => _instance;

  // ====================== 状态管理 ======================
  bool _isInitialized = false;
  final Set<int> _currentProcessingArticleIds = <int>{};
  static const int _maxConcurrentProcessing = 5;
  Timer? _processingTimer;

  // ====================== 生命周期管理 ======================

  /// 初始化服务
  Future<void> init() async {
    if (_isInitialized) {
      logger.d("[网页解析][初始化] 服务已初始化，跳过");
      return;
    }

    logger.i("[网页解析][初始化] ▶ 开始初始化服务");
    WidgetsBinding.instance.addObserver(this);
    _startProcessingTimer();
    _checkNextArticle();
    _isInitialized = true;
    logger.i("[网页解析][初始化] ◀ 服务初始化完成");
  }

  /// 释放资源
  void dispose() {
    logger.i("[网页解析][释放] ▶ 开始释放资源");
    _stopProcessingTimer();
    WidgetsBinding.instance.removeObserver(this);
    _isInitialized = false;
    logger.i("[网页解析][释放] ◀ 资源释放完成");
  }

  /// 处理应用生命周期变化
  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    logger.i("[网页解析][生命周期] 应用状态变更: $state");

    switch (state) {
      case AppLifecycleState.resumed:
        _startProcessingTimer();
        _checkIncompleteArticles();
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

  // ====================== 公共API ======================

  /// 保存网页（对外API）
  ///
  /// 用于创建新文章或更新现有文章，并触发处理流程
  Future<ArticleModel> saveWebpage({
    required String url,
    required String comment,
    bool isUpdate = false,
    int articleID = 0,
  }) async {
    logger.i("[网页解析][API] ▶ 保存网页请求: URL=$url, 更新=$isUpdate, ID=$articleID");

    // 验证输入
    await _validateSaveWebpageInput(url, isUpdate, articleID);

    // 创建或更新文章
    final ArticleModel article;
    if (isUpdate && articleID > 0) {
      article = await _resetExistingArticle(articleID, comment);
      logger.i("[网页解析][API] 重置现有文章 #$articleID");
    } else {
      article = await _createNewArticle(url, comment);
      logger.i("[网页解析][API] 创建新文章 #${article.id}");
    }

    // 立即触发处理
    _checkNextArticle();

    logger.i("[网页解析][API] ◀ 保存网页完成: #${article.id}");
    return article;
  }

  // ====================== 任务调度 ======================

  /// 启动处理定时器
  void _startProcessingTimer() {
    if (_processingTimer != null && _processingTimer!.isActive) {
      logger.d("[网页解析][调度] 定时器已在运行");
      return;
    }

    _processingTimer = Timer.periodic(const Duration(seconds: 30), (_) => _checkNextArticle());
    logger.i("[网页解析][调度] ▶ 定时处理器已启动（30秒间隔）");
  }

  /// 停止处理定时器
  void _stopProcessingTimer() {
    if (_processingTimer == null) return;

    _processingTimer!.cancel();
    _processingTimer = null;
    logger.i("[网页解析][调度] ◀ 定时处理器已停止");
  }

  /// 处理下一批文章
  Future<void> _checkNextArticle() async {
    // 检查是否达到并行处理上限
    if (_currentProcessingArticleIds.length >= _maxConcurrentProcessing) {
      logger.d("[网页解析][调度] 已达到最大并行处理数量 (${_currentProcessingArticleIds.length}/$_maxConcurrentProcessing)");
      return;
    }

    // 计算可用处理槽位
    final availableSlots = _maxConcurrentProcessing - _currentProcessingArticleIds.length;
    logger.d("[网页解析][调度] 可用处理槽位: $availableSlots");

    // 获取待处理文章
    final pendingArticles = ArticleRepository.findAllPending();
    if (pendingArticles.isEmpty) {
      logger.d("[网页解析][调度] 没有待处理文章");
      return;
    }

    // 优先处理webContentFetched状态的文章
    int processedCount = 0;
    for (int i = 0; i < availableSlots; i++) {
      final article = _selectNextArticleToProcess(pendingArticles);
      if (article == null) break;

      _processArticle(article); // 异步处理，不等待
      processedCount++;
    }

    if (processedCount > 0) {
      logger.i("[网页解析][调度] 本次启动 $processedCount 个新任务");
    }
  }

  /// 从待处理文章列表中选择下一个要处理的文章
  ArticleModel? _selectNextArticleToProcess(List<ArticleModel> pendingArticles) {
    if (pendingArticles.isEmpty) return null;

    // 先尝试获取已有网页内容但未完成AI处理的文章
    try {
      // 优先处理 webContentFetched 状态的文章
      final article = pendingArticles.firstWhere(
        (a) => a.status == ArticleStatus.webContentFetched && !_currentProcessingArticleIds.contains(a.id),
        orElse:
            () => pendingArticles.firstWhere(
              (a) => a.status == ArticleStatus.pending && !_currentProcessingArticleIds.contains(a.id),
              orElse: () => throw Exception('没有可处理的文章'),
            ),
      );
      return article;
    } catch (e) {
      logger.d("[网页解析][调度] 没有更多可处理文章");
      return null;
    }
  }

  // ====================== 核心处理流程 ======================

  /// 处理单篇文章
  ///
  /// 主要处理流程入口，根据文章状态执行不同处理逻辑
  Future<void> _processArticle(ArticleModel article) async {
    final articleId = article.id;
    if (_currentProcessingArticleIds.contains(articleId)) {
      logger.w("[网页解析][流程] 文章 #$articleId 已在处理中，跳过");
      return;
    }

    _currentProcessingArticleIds.add(articleId);
    logger.i("[网页解析][流程] ▶ 开始处理文章 #$articleId [状态: ${article.status}]");
    _notifyUI(articleId);

    try {
      // 根据文章状态执行不同处理逻辑
      if (article.status == ArticleStatus.pending) {
        // 阶段1: 获取网页内容
        await _executeWebContentFetchStage(article);
      } else if (article.status == ArticleStatus.webContentFetched) {
        // 阶段2: AI内容处理
        await _executeAiProcessingStage(article);
      } else {
        // 状态异常，重置为待处理
        logger.w("[网页解析][流程] 文章状态异常: ${article.status}，重置为待处理");
        await _updateArticleStatus(articleId, ArticleStatus.pending);
      }
    } catch (e, stackTrace) {
      logger.e("[网页解析][流程] 文章处理错误 #$articleId: $e");
      logger.e(stackTrace.toString());
      await _handleProcessingError(articleId, e, "处理流程");
    } finally {
      _currentProcessingArticleIds.remove(articleId);
      logger.i("[网页解析][流程] ◀ 完成处理文章 #$articleId");
      _notifyUI(articleId);

      // 延迟一秒后检查下一篇文章
      Future.delayed(const Duration(seconds: 1), _checkNextArticle);
    }
  }

  /// 执行网页内容获取阶段
  Future<void> _executeWebContentFetchStage(ArticleModel article) async {
    final articleId = article.id;
    logger.i("[网页解析][阶段1] ▶ 获取网页内容: #$articleId, URL: ${article.url}");

    try {
      // 获取网页内容
      final (title, htmlContent, textContent, coverImageUrl) = await _fetchWebContent(article.url);

      // 验证内容
      _validateWebContent(articleId, title, htmlContent, textContent);

      // 更新文章基本信息
      await _saveWebContentToArticle(articleId, title, htmlContent, textContent, coverImageUrl);

      logger.i("[网页解析][阶段1] ◀ 网页内容获取成功: #$articleId");

      // 继续执行AI处理阶段
      final updatedArticle = ArticleRepository.find(articleId);
      if (updatedArticle != null) {
        await _executeAiProcessingStage(updatedArticle);
      }
    } catch (e, stackTrace) {
      logger.e("[网页解析][阶段1] 网页内容获取失败 #$articleId: $e");
      logger.e(stackTrace.toString());
      await _markArticleAsFailed(articleId, "网页内容获取失败: $e");
      throw Exception("获取网页内容失败: $e");
    }
  }

  /// 执行AI内容处理阶段
  Future<void> _executeAiProcessingStage(ArticleModel article) async {
    final articleId = article.id;
    final title = article.title ?? '';
    final htmlContent = article.htmlContent ?? '';
    final content = article.content ?? '';
    final coverImageUrl = article.coverImageUrl ?? '';

    logger.i("[网页解析][阶段2] ▶ 开始AI内容处理: #$articleId");

    try {
      // 运行AI处理管道
      final success = await _runAiProcessingPipeline(article, title, htmlContent, content, coverImageUrl);

      if (success) {
        await _updateArticleStatus(articleId, ArticleStatus.completed);
        logger.i("[网页解析][阶段2] ◀ AI处理成功完成: #$articleId");
      } else {
        // 部分任务可能失败，状态保持为webContentFetched以便后续重试
        logger.w("[网页解析][阶段2] ◀ AI处理部分失败: #$articleId");
      }
    } catch (e, stackTrace) {
      logger.e("[网页解析][阶段2] AI处理发生错误 #$articleId: $e");
      logger.e(stackTrace.toString());
      await _markArticleAsFailed(articleId, "AI处理失败: $e");
      throw Exception("AI处理失败: $e");
    }
  }

  // ====================== 网页内容处理 ======================

  /// 获取网页内容
  Future<(String title, String htmlContent, String textContent, String coverImageUrl)> _fetchWebContent(
    String? url,
  ) async {
    if (url.isNullOrEmpty) {
      logger.w("[网页解析][内容] URL为空，无法获取内容");
      return ('', '', '', '');
    }

    logger.d("[网页解析][内容] 开始加载URL: $url");

    final headlessWebView = HeadlessWebView();
    final result = await headlessWebView.loadAndParseUrl(url!);

    logger.d(
      "[网页解析][内容] 成功获取内容: 标题长度=${result.title.length}, "
      "HTML=${result.htmlContent.length}字节, "
      "文本=${result.textContent.length}字节",
    );

    return (result.title, result.htmlContent, result.textContent, result.coverImageUrl);
  }

  /// 验证网页内容是否有效
  void _validateWebContent(int articleId, String title, String htmlContent, String textContent) {
    final List<String> errors = [];

    if (title.isEmpty) {
      errors.add("标题为空");
    }

    if (htmlContent.isEmpty || htmlContent.length < 100) {
      errors.add("HTML内容为空或过短(${htmlContent.length}字节)");
    }

    if (textContent.isEmpty || textContent.length < 50) {
      errors.add("文本内容为空或过短(${textContent.length}字节)");
    }

    if (errors.isNotEmpty) {
      final errorMsg = errors.join(", ");
      logger.w("[网页解析][验证] 内容验证失败 #$articleId: $errorMsg");
      throw Exception("网页内容无效: $errorMsg");
    }

    logger.d("[网页解析][验证] 内容验证通过 #$articleId");
  }

  /// 保存网页内容到文章
  Future<void> _saveWebContentToArticle(
    int articleId,
    String title,
    String htmlContent,
    String textContent,
    String coverImageUrl,
  ) async {
    logger.d("[网页解析][保存] 保存网页内容到文章 #$articleId");

    await ArticleRepository.updateField(articleId, ArticleFieldName.title, title);
    await ArticleRepository.updateField(articleId, ArticleFieldName.content, textContent);
    await ArticleRepository.updateField(articleId, ArticleFieldName.htmlContent, htmlContent);
    await ArticleRepository.updateField(articleId, ArticleFieldName.coverImageUrl, coverImageUrl);
    await ArticleRepository.updateField(
      articleId,
      ArticleFieldName.updatedAt,
      DateTime.now().toUtc().toIso8601String(),
    );
    await ArticleRepository.updateField(articleId, ArticleFieldName.status, ArticleStatus.webContentFetched);

    logger.d("[网页解析][保存] 网页内容保存完成 #$articleId");
  }

  // ====================== AI处理 ======================

  /// 执行AI处理管道，处理标题、摘要、图片和Markdown内容
  Future<bool> _runAiProcessingPipeline(
    ArticleModel article,
    String title,
    String htmlContent,
    String content,
    String coverImageUrl,
  ) async {
    final articleId = article.id;
    logger.d("[网页解析][AI] ▶ 启动AI处理管道 #$articleId");

    // 确定需要执行的任务
    final needsTitle = article.aiTitle.isNullOrEmpty && title.isNotEmpty;
    final needsSummary = article.aiContent.isNullOrEmpty && content.isNotEmpty;
    final needsImage = article.coverImage.isNullOrEmpty && coverImageUrl.isNotEmpty;
    final needsMarkdown = article.aiMarkdownContent.isNullOrEmpty && htmlContent.isNotEmpty;

    // 创建任务列表
    final tasks = <Future<bool>>[];

    if (needsTitle) {
      logger.d("[网页解析][AI] 添加标题处理任务 #$articleId");
      tasks.add(_processTitleTask(articleId, title));
    }

    if (needsSummary) {
      logger.d("[网页解析][AI] 添加摘要处理任务 #$articleId");
      tasks.add(_processSummaryTask(articleId, content, article));
    }

    if (needsImage) {
      logger.d("[网页解析][AI] 添加图片处理任务 #$articleId");
      tasks.add(_processImageTask(articleId, coverImageUrl));
    }

    if (needsMarkdown) {
      logger.d("[网页解析][AI] 添加Markdown处理任务 #$articleId");
      tasks.add(_processMarkdownTask(articleId, htmlContent));
    }

    // 检查是否有任务需要执行
    if (tasks.isEmpty) {
      logger.i("[网页解析][AI] 无需执行AI任务 #$articleId");
      return _isArticleContentComplete(article);
    }

    // 并行执行所有任务
    logger.i("[网页解析][AI] 开始执行 ${tasks.length} 个AI任务 #$articleId");
    final results = await Future.wait(tasks);
    final allTasksSucceeded = results.every((success) => success);

    // 重新检查文章是否完整
    final updatedArticle = ArticleRepository.find(articleId);
    final isComplete = updatedArticle != null && _isArticleContentComplete(updatedArticle);

    logger.i("[网页解析][AI] ◀ AI处理完成 #$articleId: 成功=${allTasksSucceeded}, 完整=${isComplete}");
    return isComplete;
  }

  /// 处理标题任务
  Future<bool> _processTitleTask(int articleId, String title) async {
    logger.d("[网页解析][AI:标题] ▶ 开始处理标题 #$articleId");

    try {
      var aiTitle = await AiService.i.translate(title.trim());

      // 如果标题太长，进行概括
      if (aiTitle.length >= 50) {
        logger.d("[网页解析][AI:标题] 标题太长，进行概括 #$articleId");
        aiTitle = await AiService.i.summarizeOneLine(aiTitle);
      }

      if (aiTitle.isEmpty) {
        logger.w("[网页解析][AI:标题] 处理结果为空 #$articleId");
        return false;
      }

      await ArticleRepository.updateField(articleId, ArticleFieldName.aiTitle, aiTitle);
      logger.d("[网页解析][AI:标题] ◀ 标题处理成功 #$articleId");
      return true;
    } catch (e) {
      logger.e("[网页解析][AI:标题] 处理失败 #$articleId: $e");
      return false;
    }
  }

  /// 处理摘要和标签任务
  Future<bool> _processSummaryTask(int articleId, String content, ArticleModel article) async {
    logger.d("[网页解析][AI:摘要] ▶ 开始处理摘要 #$articleId");

    try {
      final (summary, tags) = await AiService.i.summarize(content.trim());

      if (summary.isEmpty) {
        logger.w("[网页解析][AI:摘要] 处理结果为空 #$articleId");
        return false;
      }

      await ArticleRepository.updateField(articleId, ArticleFieldName.aiContent, summary);
      await _saveTags(article, tags);

      logger.d("[网页解析][AI:摘要] ◀ 摘要处理成功，提取了 ${tags.length} 个标签 #$articleId");
      return true;
    } catch (e) {
      logger.e("[网页解析][AI:摘要] 处理失败 #$articleId: $e");
      return false;
    }
  }

  /// 处理图片任务
  Future<bool> _processImageTask(int articleId, String imageUrl) async {
    if (imageUrl.isEmpty) {
      logger.d("[网页解析][AI:图片] 图片URL为空，跳过处理 #$articleId");
      return true;
    }

    logger.d("[网页解析][AI:图片] ▶ 开始处理图片 #$articleId: $imageUrl");

    try {
      final imagePath = await HttpService.i.downloadImage(imageUrl);

      if (imagePath.isNotEmpty) {
        await ArticleRepository.updateField(articleId, ArticleFieldName.coverImage, imagePath);
        logger.d("[网页解析][AI:图片] ◀ 图片处理成功 #$articleId");
        return true;
      } else {
        logger.w("[网页解析][AI:图片] 图片下载结果为空 #$articleId");
        return true; // 图片下载失败但不影响整体流程
      }
    } catch (e) {
      logger.e("[网页解析][AI:图片] 处理失败 #$articleId: $e");
      return false;
    }
  }

  /// 处理Markdown转换任务
  Future<bool> _processMarkdownTask(int articleId, String html) async {
    logger.d("[网页解析][AI:Markdown] ▶ 开始处理Markdown #$articleId");

    try {
      final markdown = await AiService.i.convertHtmlToMarkdown(html);

      if (markdown.isEmpty) {
        logger.w("[网页解析][AI:Markdown] 处理结果为空 #$articleId");
        return false;
      }

      await ArticleRepository.updateField(articleId, ArticleFieldName.aiMarkdownContent, markdown);
      logger.d("[网页解析][AI:Markdown] ◀ Markdown处理成功 #$articleId");
      return true;
    } catch (e) {
      logger.e("[网页解析][AI:Markdown] 处理失败 #$articleId: $e");
      return false;
    }
  }

  /// 保存标签到文章
  Future<void> _saveTags(ArticleModel article, List<String> tagNames) async {
    if (tagNames.isEmpty) return;

    final articleId = article.id;
    logger.d("[网页解析][标签] ▶ 开始保存 ${tagNames.length} 个标签 #$articleId");

    try {
      article.tags.clear();

      for (var tagName in tagNames) {
        await TagRepository.addTagToArticle(article, tagName);
      }

      logger.d("[网页解析][标签] ◀ 标签保存完成 #$articleId");
    } catch (e) {
      logger.e("[网页解析][标签] 保存标签失败 #$articleId: $e");
      // 标签失败不阻止主流程
    }
  }

  // ====================== 文章状态和验证 ======================

  /// 检查不完整文章，重置状态以便重新处理
  Future<void> _checkIncompleteArticles() async {
    logger.i("[网页解析][检查] ▶ 开始检查不完整文章");

    if (_currentProcessingArticleIds.length >= _maxConcurrentProcessing) {
      logger.d("[网页解析][检查] 当前已达到最大并行处理数量，稍后再检查");
      return;
    }

    // 检查已完成但内容不完整的文章
    int resetCount = 0;
    final completedArticles = ArticleRepository.findByStatus(ArticleStatus.completed);

    for (var article in completedArticles) {
      if (!_isArticleContentComplete(article)) {
        logger.w("[网页解析][检查] 发现不完整文章 #${article.id}，重置为webContentFetched");
        await _updateArticleStatus(article.id, ArticleStatus.webContentFetched);
        resetCount++;
      }
    }

    // 检查webContentFetched状态但基础内容缺失的文章
    final webContentFetchedArticles = ArticleRepository.findByStatus(ArticleStatus.webContentFetched);

    for (var article in webContentFetchedArticles) {
      if (_isBasicContentMissing(article)) {
        logger.w("[网页解析][检查] 发现基础内容缺失 #${article.id}，重置为pending");
        await _updateArticleStatus(article.id, ArticleStatus.pending);
        resetCount++;
      }
    }

    logger.i("[网页解析][检查] ◀ 检查完成，重置了 $resetCount 篇文章状态");
  }

  /// 检查文章内容是否完整（所有AI处理字段都已填充）
  bool _isArticleContentComplete(ArticleModel article) {
    bool titleComplete = article.aiTitle != null && article.aiTitle!.isNotEmpty;
    bool summaryComplete = article.aiContent != null && article.aiContent!.isNotEmpty;
    bool markdownComplete = article.aiMarkdownContent != null && article.aiMarkdownContent!.isNotEmpty;
    bool coverComplete =
        article.coverImageUrl == null ||
        article.coverImageUrl!.isEmpty ||
        (article.coverImage != null && article.coverImage!.isNotEmpty);

    final isComplete = titleComplete && summaryComplete && markdownComplete && coverComplete;

    if (!isComplete) {
      logger.d(
        "[网页解析][检查] 文章 #${article.id} 内容不完整: "
        "标题=$titleComplete, "
        "摘要=$summaryComplete, "
        "Markdown=$markdownComplete, "
        "图片完整=$coverComplete",
      );
    }

    return isComplete;
  }

  /// 检查文章的基础内容是否缺失
  bool _isBasicContentMissing(ArticleModel article) {
    return article.title.isNullOrEmpty || article.content.isNullOrEmpty || article.htmlContent.isNullOrEmpty;
  }

  /// 保存当前处理中文章的状态
  void _saveCurrentArticleState() {
    if (_currentProcessingArticleIds.isEmpty) return;

    logger.i("[网页解析][状态] ▶ 保存 ${_currentProcessingArticleIds.length} 个处理中文章的状态");

    for (final articleId in _currentProcessingArticleIds.toList()) {
      try {
        final article = ArticleRepository.find(articleId);
        if (article != null) {
          final targetStatus =
              article.status == ArticleStatus.webContentFetched
                  ? ArticleStatus
                      .webContentFetched // 保持当前状态
                  : ArticleStatus.pending; // 回退到待处理

          _updateArticleStatus(article.id, targetStatus);
          logger.d("[网页解析][状态] 文章 #$articleId 状态已设为 $targetStatus");
        }
      } catch (e) {
        logger.e("[网页解析][状态] 保存文章 #$articleId 状态失败: $e");
      }
    }

    logger.i("[网页解析][状态] ◀ 保存处理中文章状态完成");
  }

  // ====================== 文章创建与更新 ======================

  /// 创建新文章
  Future<ArticleModel> _createNewArticle(String url, String comment) async {
    logger.d("[网页解析][创建] ▶ 开始创建新文章: $url");

    final data = _prepareNewArticleData(url, comment);
    final articleModel = ArticleRepository.createArticleModel(data);

    final id = await ArticleRepository.create(articleModel);
    if (id <= 0) {
      throw Exception("创建文章记录失败");
    }

    final savedArticle = ArticleRepository.find(id);
    if (savedArticle == null) {
      throw Exception("无法找到刚创建的文章: $id");
    }

    logger.d("[网页解析][创建] ◀ 新文章创建成功: #${savedArticle.id}");
    return savedArticle;
  }

  /// 重置现有文章以更新内容
  Future<ArticleModel> _resetExistingArticle(int articleId, String comment) async {
    logger.d("[网页解析][更新] ▶ 开始重置文章: #$articleId");

    final article = ArticleRepository.find(articleId);
    if (article == null) {
      throw Exception("找不到要更新的文章: $articleId");
    }

    // 只重置AI相关字段，保留原始内容
    article.comment = comment;
    _resetArticleAiFields(article);
    article.status = ArticleStatus.pending;
    article.updatedAt = DateTime.now().toUtc();

    await ArticleRepository.update(article);
    logger.d("[网页解析][更新] ◀ 文章重置成功: #$articleId");

    return article;
  }

  /// 准备新文章的数据
  Map<String, dynamic> _prepareNewArticleData(String url, String comment) {
    final now = DateTime.now().toUtc();

    return {
      'title': '正在加载...',
      'url': url,
      'comment': comment,
      'pubDate': now,
      'createdAt': now,
      'updatedAt': now,
      'status': ArticleStatus.pending,
      // 加入初始化的AI字段
      ..._initEmptyAiFields(),
    };
  }

  /// 初始化空的AI字段
  Map<String, String> _initEmptyAiFields() {
    return {
      'aiTitle': '',
      'aiContent': '',
      'aiMarkdownContent': '',
      'content': '',
      'htmlContent': '',
      'coverImage': '',
      'coverImageUrl': '',
    };
  }

  /// 重置文章的AI字段
  void _resetArticleAiFields(ArticleModel article) {
    article.aiTitle = '';
    article.aiContent = '';
    article.aiMarkdownContent = '';
    article.coverImage = '';
  }

  // ====================== 错误处理 ======================

  /// 统一处理错误
  Future<void> _handleProcessingError(int articleId, dynamic error, String stage) async {
    logger.e("[网页解析][错误] ▶ $stage 错误 #$articleId: $error");

    try {
      await _markArticleAsFailed(articleId, "处理失败: $error");
      logger.w("[网页解析][错误] ◀ 文章已标记为错误 #$articleId");
    } catch (e) {
      logger.e("[网页解析][错误] 无法标记文章错误状态 #$articleId: $e");
    }
  }

  /// 将文章标记为失败状态
  Future<void> _markArticleAsFailed(int articleId, String errorMessage) async {
    final article = ArticleRepository.find(articleId);
    if (article == null) {
      logger.e("[网页解析][错误] 无法找到文章 #$articleId");
      return;
    }

    article.status = ArticleStatus.error;
    article.aiContent = errorMessage;
    article.updatedAt = DateTime.now().toUtc();

    await ArticleRepository.update(article);
  }

  /// 验证保存网页输入参数
  Future<void> _validateSaveWebpageInput(String url, bool isUpdate, int articleID) async {
    if (url.isNullOrEmpty) {
      throw Exception("URL不能为空");
    }

    if (!isUpdate && await ArticleRepository.isArticleExists(url)) {
      throw Exception("网页已存在，无法重复添加");
    }

    if (isUpdate && articleID <= 0) {
      throw Exception("文章ID无效，无法更新");
    }
  }

  // ====================== 辅助方法 ======================

  /// 更新文章状态
  Future<void> _updateArticleStatus(int articleId, String status) async {
    logger.d("[网页解析][状态] 更新文章 #$articleId 状态: $status");
    await ArticleRepository.updateField(articleId, ArticleFieldName.status, status);
  }

  /// 通知UI更新
  void _notifyUI(int articleId) {
    try {
      if (Get.isRegistered<ArticlesController>()) {
        Get.find<ArticlesController>().updateArticle(articleId);
        logger.d("[网页解析][UI] 已通知UI更新文章 #$articleId");
      }
    } catch (e) {
      // 控制器不存在时静默处理
    }
  }
}
