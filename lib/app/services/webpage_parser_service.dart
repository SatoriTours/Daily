import 'dart:async';
import 'package:daily_satori/app/components/webview/headless_webview.dart';
import 'package:daily_satori/app/modules/articles/controllers/articles_controller.dart';

import 'package:daily_satori/app/services/ai_service/ai_service.dart';
import 'package:daily_satori/app/services/logger_service.dart';
import 'package:daily_satori/app/repositories/article_repository.dart';
import 'package:daily_satori/app/models/article_model.dart';
import 'package:daily_satori/app/repositories/tag_repository.dart';
import 'package:daily_satori/app/services/http_service.dart';
import 'package:get/get.dart';

/// 网页解析服务
///
/// 负责解析网页内容，提取有用信息，并生成AI摘要
/// 使用简单的定时器机制自动处理pending状态的文章
class WebpageParserService {
  // 单例模式实现
  WebpageParserService._privateConstructor();
  static final WebpageParserService _instance = WebpageParserService._privateConstructor();
  static WebpageParserService get i => _instance;

  // 当前正在处理的文章ID
  int? _currentProcessingArticleId;

  // 定时器
  Timer? _processingTimer;

  // 服务是否初始化标志
  bool _isInitialized = false;

  /// 初始化服务
  Future<void> init() async {
    if (_isInitialized) return;

    logger.i("[WebpageParserService] 初始化中...");

    // 启动定时器，每30秒检查一次待处理的文章
    _processingTimer = Timer.periodic(const Duration(seconds: 30), (_) => _checkAndProcessPendingArticle());

    // 立即执行一次检查
    _checkAndProcessPendingArticle();

    _isInitialized = true;
    logger.i("[WebpageParserService] 初始化完成");
  }

  /// 释放资源
  void dispose() {
    _processingTimer?.cancel();
    _processingTimer = null;
    _isInitialized = false;
    logger.i("[WebpageParserService] 已释放资源");
  }

  /// 保存网页内容（只保存URL和基本信息）
  ///
  /// 创建或更新文章记录，保存基本信息，并将状态设置为pending
  Future<ArticleModel> saveWebpage({
    required String url,
    required String comment,
    bool isUpdate = false,
    int articleID = 0,
  }) async {
    logger.i("[WebpageParserService] 开始保存网页基本信息: $url");

    // 检查文章是否已存在
    if (!isUpdate && await ArticleRepository.isArticleExists(url)) {
      throw Exception('网页已存在');
    }

    if (isUpdate && articleID <= 0) {
      throw Exception('网页不存在，无法更新');
    }

    // 创建或更新文章，标记为待处理状态
    final articleModel = await _createInitialArticleModel(
      url: url,
      comment: comment,
      isUpdate: isUpdate,
      articleID: articleID,
      status: 'pending',
    );

    // 立即检查并处理待处理文章
    _checkAndProcessPendingArticle();

    return articleModel;
  }

  /// 检查并处理待处理的文章
  ///
  /// 查找最近的一个需要处理的文章，如果没有正在处理的文章，则开始处理
  Future<void> _checkAndProcessPendingArticle() async {
    // 如果当前有文章正在处理，直接返回
    if (_currentProcessingArticleId != null) {
      logger.d("[WebpageParserService] 当前有文章正在处理，ID: $_currentProcessingArticleId");
      return;
    }

    try {
      // 查找最近的一个需要处理的文章
      final pendingArticle = ArticleRepository.findLastPending();

      if (pendingArticle == null) {
        logger.d("[WebpageParserService] 没有待处理的文章");
        return;
      }

      // 开始处理文章
      _processArticle(pendingArticle);
    } catch (e) {
      logger.e("[WebpageParserService] 检查待处理文章失败: $e");
    }
  }

  /// 处理文章
  ///
  /// 处理单篇文章，包括获取网页内容、生成AI摘要等
  Future<void> _processArticle(ArticleModel article) async {
    final articleId = article.id;

    // 设置当前处理中的文章ID
    _currentProcessingArticleId = articleId;

    logger.i("[WebpageParserService] 开始处理文章 #$articleId: ${article.url}");

    try {
      // 更新文章状态为处理中
      await _updateArticleStatus(article, 'processing');
      _notifyArticleUpdated(articleId);

      // 获取网页内容
      final webpageData = await _fetchWebpageContent(article.url);

      // 并行处理各项任务
      final results = await Future.wait([
        _processAiTitle(webpageData.title),
        _processAiContent(webpageData.textContent),
        _processImage(webpageData.coverImageUrl),
        _processMarkdown(webpageData.htmlContent),
      ]);

      // 处理结果
      final aiTitle = results[0] as String;
      final aiContentResult = results[1] as (String, List<String>);
      final image = results[2] as ImageDownloadResult;
      final markdown = results[3] as String;

      // 更新文章数据
      await _updateArticleWithProcessedData(
        articleModel: article,
        webpageData: webpageData,
        aiTitle: aiTitle,
        aiContent: aiContentResult.$1,
        tags: aiContentResult.$2,
        image: image,
        markdown: markdown,
      );

      logger.i("[WebpageParserService] 文章 #$articleId 处理完成");

      // 通知UI更新
      _notifyArticleUpdated(articleId);
    } catch (e, stackTrace) {
      logger.e("[WebpageParserService] 文章 #$articleId 处理失败: $e");
      logger.e(stackTrace);

      // 标记处理错误信息
      article.aiContent = "处理失败：$e";
      await _updateArticleStatus(article, 'error');
      article.updatedAt = DateTime.now().toUtc();
      await ArticleRepository.update(article);
    } finally {
      // 清除当前处理中的文章ID
      _currentProcessingArticleId = null;

      // 检查是否有其他待处理文章
      Future.delayed(const Duration(seconds: 1), _checkAndProcessPendingArticle);
    }
  }

  /// 通知文章更新
  void _notifyArticleUpdated(int articleId) {
    try {
      Get.find<ArticlesController>().updateArticle(articleId);
    } catch (e) {
      // 静默处理，如果控制器不存在，不影响处理流程
      logger.d("[WebpageParserService] 通知文章更新失败: $e");
    }
  }

  /// 更新文章数据
  ///
  /// 使用处理结果更新文章的各项信息，包括标题、内容、摘要等
  Future<void> _updateArticleWithProcessedData({
    required ArticleModel articleModel,
    required WebpageData webpageData,
    required String aiTitle,
    required String aiContent,
    required List<String> tags,
    required ImageDownloadResult image,
    required String markdown,
  }) async {
    // 更新文章基本数据
    articleModel.title = webpageData.title;
    articleModel.aiTitle = aiTitle;
    articleModel.content = webpageData.textContent;
    articleModel.aiContent = aiContent;
    articleModel.htmlContent = webpageData.htmlContent;
    articleModel.aiMarkdownContent = markdown;
    articleModel.coverImage = image.imagePath;
    articleModel.coverImageUrl = image.imageUrl;
    articleModel.updatedAt = DateTime.now().toUtc();
    articleModel.status = 'completed';

    // 更新状态为已完成
    // await _updateArticleStatus(articleModel, 'completed');

    // 保存到数据库
    // await ArticleRepository.update(articleModel);

    // 保存关联数据
    await _saveTags(articleModel, tags);

    // 再次保存文章，确保关联数据正确
    await ArticleRepository.update(articleModel);
  }

  /// 更新文章处理状态
  Future<void> _updateArticleStatus(ArticleModel articleModel, String status) async {
    articleModel.setStatus(status);
    await articleModel.save();
  }

  /// 创建初始文章模型
  ///
  /// 创建或更新文章的初始状态，只包含基本信息
  Future<ArticleModel> _createInitialArticleModel({
    required String url,
    required String comment,
    required bool isUpdate,
    required int articleID,
    required String status,
  }) async {
    try {
      // 如果是更新模式且有指定ID
      if (isUpdate && articleID > 0) {
        final article = ArticleRepository.find(articleID);
        if (article != null) {
          article.comment = comment;
          await _updateArticleStatus(article, status);
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
        'status': status,
      };

      final articleModel = ArticleRepository.createArticleModel(data);
      final id = await ArticleRepository.create(articleModel);

      if (id <= 0) {
        throw Exception("创建文章失败，无法获取有效ID");
      }

      final savedArticle = ArticleRepository.find(id);
      if (savedArticle == null) {
        throw Exception("无法找到刚刚创建的文章: $id");
      }

      logger.i("[WebpageParserService] 创建初始文章成功: $id");
      return savedArticle;
    } catch (e) {
      logger.e("[WebpageParserService] 创建初始文章失败: $e");
      rethrow;
    }
  }

  /// 获取网页内容
  ///
  /// 使用Headless WebView加载并解析网页内容
  Future<WebpageData> _fetchWebpageContent(String? url) async {
    if (url == null || url.isEmpty) {
      logger.e("[WebpageParserService] URL为空，无法获取内容");
      return WebpageData.empty();
    }

    logger.i("[WebpageParserService] 开始获取网页内容: $url");

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
  }

  /// 处理AI标题
  ///
  /// 使用AI服务翻译或总结标题
  Future<String> _processAiTitle(String title) async {
    try {
      var aiTitle = await AiService.i.translate(title.trim());
      return aiTitle.length >= 50 ? await AiService.i.summarizeOneLine(aiTitle) : aiTitle;
    } catch (e) {
      logger.e("[WebpageParserService] AI标题处理失败: $e");
      return '';
    }
  }

  /// 处理AI内容
  ///
  /// 使用AI服务总结文章内容并生成标签
  Future<(String, List<String>)> _processAiContent(String textContent) async {
    try {
      return await AiService.i.summarize(textContent.trim());
    } catch (e) {
      logger.e("[WebpageParserService] AI内容处理失败: $e");
      return ('', const <String>[]);
    }
  }

  /// 处理Markdown
  ///
  /// 将HTML内容转换为Markdown格式
  Future<String> _processMarkdown(String htmlContent) async {
    return await AiService.i.convertHtmlToMarkdown(htmlContent);
  }

  /// 处理图片
  ///
  /// 下载并保存封面图片
  Future<ImageDownloadResult> _processImage(String imageUrl) async {
    try {
      return ImageDownloadResult(imageUrl, await HttpService.i.downloadImage(imageUrl));
    } catch (e) {
      logger.e("[WebpageParserService] 图片处理失败: $e");
      return ImageDownloadResult('', '');
    }
  }

  /// 保存标签
  ///
  /// 为文章添加标签
  Future<void> _saveTags(ArticleModel articleModel, List<String> tagNames) async {
    try {
      logger.i("[WebpageParserService] 开始保存标签: $tagNames");

      // 清除原有标签
      articleModel.tags.clear();

      // 添加新标签
      for (var tagName in tagNames) {
        await TagRepository.addTagToArticle(articleModel, tagName);
      }

      logger.i("[WebpageParserService] 标签保存完成 ${articleModel.id}");
    } catch (e) {
      logger.e("[WebpageParserService] 保存标签失败: $e");
    }
  }
}

/// 图片下载结果类
class ImageDownloadResult {
  final String imageUrl;
  final String imagePath;

  ImageDownloadResult(this.imageUrl, this.imagePath);
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
