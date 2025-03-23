import 'dart:async';
import 'dart:collection';
import 'package:daily_satori/app/components/webview/headless_webview.dart';
import 'package:daily_satori/app/modules/articles/controllers/articles_controller.dart';

import 'package:daily_satori/app/services/ai_service/ai_service.dart';
import 'package:daily_satori/app/services/logger_service.dart';
import 'package:daily_satori/app/repositories/article_repository.dart';
import 'package:daily_satori/app/models/article_model.dart';
import 'package:daily_satori/app/objectbox/article.dart';
import 'package:daily_satori/app/repositories/image_repository.dart';
import 'package:daily_satori/app/repositories/screenshot_repository.dart';
import 'package:daily_satori/app/repositories/tag_repository.dart';
import 'package:daily_satori/app/services/http_service.dart';
import 'package:get/get.dart';

/// 网页解析服务
///
/// 负责解析网页内容并提取有用信息
class WebpageParserService {
  // 单例模式
  WebpageParserService._privateConstructor();
  static final WebpageParserService _instance = WebpageParserService._privateConstructor();
  static WebpageParserService get i => _instance;

  // 文章处理队列
  final Queue<int> _processingQueue = Queue<int>();

  // 当前正在处理的文章ID
  int? _currentProcessingArticle;

  // 定时器
  Timer? _processingTimer;

  // 服务是否初始化
  bool _isInitialized = false;

  /// 初始化服务
  Future<void> init() async {
    if (_isInitialized) return;

    logger.i("[WebpageParserService] 初始化中...");

    // 启动定时器，每30秒检查一次待处理的文章
    _processingTimer = Timer.periodic(const Duration(seconds: 30), (_) => _checkPendingArticles());

    // 立即执行一次检查
    _checkPendingArticles();

    _isInitialized = true;
    logger.i("[WebpageParserService] 初始化完成");
  }

  /// 释放资源
  void dispose() {
    _processingTimer?.cancel();
    _processingTimer = null;
    _isInitialized = false;
  }

  /// 保存网页内容（只保存URL和基本信息）
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

    // 将文章ID添加到处理队列
    _addToProcessingQueue(articleModel.id);

    return articleModel;
  }

  /// 将文章ID添加到处理队列
  void _addToProcessingQueue(int articleId) {
    // 检查文章ID是否已在队列中或正在处理
    if (!_processingQueue.contains(articleId) && _currentProcessingArticle != articleId) {
      _processingQueue.add(articleId);
      logger.i("[WebpageParserService] 已将文章 #$articleId 添加到处理队列");

      // 尝试立即处理队列
      _processQueue();
    } else {
      logger.d("[WebpageParserService] 文章 #$articleId 已在队列中或正在处理，不重复添加");
    }
  }

  /// 检查数据库中待处理的文章
  Future<void> _checkPendingArticles() async {
    try {
      final pendingArticles = await _findAllPending();

      if (pendingArticles.isEmpty) {
        logger.d("[WebpageParserService] 没有待处理的文章");
        return;
      }

      logger.i("[WebpageParserService] 发现 ${pendingArticles.length} 篇待处理文章");

      // 将待处理文章添加到队列
      for (final article in pendingArticles) {
        if (!_processingQueue.contains(article.id) && _currentProcessingArticle != article.id) {
          _processingQueue.add(article.id);
        }
      }

      _processQueue();
    } catch (e) {
      logger.e("[WebpageParserService] 检查待处理文章失败: $e");
    }
  }

  /// 通过状态查找文章
  Future<List<Article>> _findAllPending() async {
    try {
      final List<ArticleModel> articles = ArticleRepository.findAllPending();
      return articles.map((model) => model.entity).toList();
    } catch (e, stackTrace) {
      logger.e("[WebpageParserService] 查找需要处理的文章失败: $e\n$stackTrace");
      return [];
    }
  }

  /// 处理队列
  Future<void> _processQueue() async {
    // 如果队列为空或当前有文章正在处理，直接返回
    if (_processingQueue.isEmpty || _currentProcessingArticle != null) return;

    // 从队列中取出一篇文章进行处理
    final articleId = _processingQueue.removeFirst();
    _currentProcessingArticle = articleId;

    // 获取文章实例
    final article = ArticleRepository.find(articleId);
    if (article == null) {
      logger.e("[WebpageParserService] 无法找到文章 #$articleId");
      _finishProcessing();
      return;
    }

    // 异步处理文章
    try {
      await _processWebpageAsync(article);
    } catch (e) {
      logger.e("[WebpageParserService] 处理文章 #$articleId 失败: $e");
    } finally {
      _finishProcessing();
    }

    logger.i("[WebpageParserService] 当前正在处理文章 #$articleId，队列中还有 ${_processingQueue.length} 篇文章待处理");
  }

  /// 完成当前文章处理，继续处理队列
  void _finishProcessing() {
    _currentProcessingArticle = null;
    _processQueue();
  }

  /// 异步处理网页内容
  Future<void> _processWebpageAsync(ArticleModel articleModel) async {
    final articleId = articleModel.id;
    logger.i("[WebpageParserService] 开始处理文章 #$articleId: ${articleModel.url}");

    try {
      // 更新文章状态为处理中
      _updateArticleStatus(articleModel, 'processing');

      // 获取网页内容
      final webpageData = await _fetchWebpageContent(articleModel.url);

      // 并行处理各项任务
      final results = await Future.wait([
        _processAiTitle(webpageData.title),
        _processAiContent(webpageData.textContent),
        _processImages(webpageData.imageUrls),
      ]);

      final aiTitle = results[0] as String;
      final aiContentResult = results[1] as (String, List<String>);
      final images = results[2] as List<ImageDownloadResult>;

      // 更新文章数据
      await _updateArticleWithProcessedData(
        articleModel: articleModel,
        webpageData: webpageData,
        aiTitle: aiTitle,
        aiContent: aiContentResult.$1,
        tags: aiContentResult.$2,
        images: images,
      );

      logger.i("[WebpageParserService] 文章 #$articleId 处理完成");

      // 更新文章列表
      Get.find<ArticlesController>().updateArticle(articleId);
    } catch (e, stackTrace) {
      logger.e("[WebpageParserService] 文章 #$articleId 处理失败: $e");
      logger.e(stackTrace);

      // 标记处理错误信息
      articleModel.aiContent = "处理失败：$e";
      _updateArticleStatus(articleModel, 'error');
      articleModel.updatedAt = DateTime.now().toUtc();
      await ArticleRepository.update(articleModel);
    }
  }

  /// 更新文章数据
  Future<void> _updateArticleWithProcessedData({
    required ArticleModel articleModel,
    required WebpageData webpageData,
    required String aiTitle,
    required String aiContent,
    required List<String> tags,
    required List<ImageDownloadResult> images,
  }) async {
    // 更新文章基本数据
    articleModel.title = webpageData.title;
    articleModel.aiTitle = aiTitle;
    articleModel.content = webpageData.textContent;
    articleModel.aiContent = aiContent;
    articleModel.htmlContent = webpageData.htmlContent;
    _updateArticleStatus(articleModel, 'completed');
    articleModel.updatedAt = DateTime.now().toUtc();

    // 保存到数据库
    await ArticleRepository.update(articleModel);

    // 保存关联数据
    await Future.wait([
      _saveTags(articleModel, tags),
      _saveImages(articleModel, images),
      _saveScreenshots(articleModel, webpageData.screenshots),
    ]);

    // 再次保存文章，确保关联数据正确
    await ArticleRepository.update(articleModel);
  }

  /// 更新文章处理状态
  void _updateArticleStatus(ArticleModel articleModel, String status) {
    articleModel.setStatus(status);
    articleModel.save();
  }

  /// 创建初始文章模型
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
          _updateArticleStatus(article, status);
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
      imageUrls: result.imageUrls,
      screenshots: result.screenshots,
    );
  }

  /// 处理AI标题
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
  Future<(String, List<String>)> _processAiContent(String textContent) async {
    try {
      return await AiService.i.summarize(textContent.trim());
    } catch (e) {
      logger.e("[WebpageParserService] AI内容处理失败: $e");
      return ('', const <String>[]);
    }
  }

  /// 处理图片
  Future<List<ImageDownloadResult>> _processImages(List<String> imageUrls) async {
    try {
      final imageResults = await Future.wait(
        imageUrls.map((imageUrl) async {
          return ImageDownloadResult(imageUrl, await HttpService.i.downloadImage(imageUrl));
        }),
      );
      return imageResults.where((result) => result.imagePath.isNotEmpty).toList();
    } catch (e) {
      logger.e("[WebpageParserService] 图片处理失败: $e");
      return <ImageDownloadResult>[];
    }
  }

  /// 保存标签
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

  /// 保存图片
  Future<void> _saveImages(ArticleModel articleModel, List<ImageDownloadResult> results) async {
    try {
      logger.i("[WebpageParserService] 开始保存图片: ${results.length}");

      // 清除原有图片
      articleModel.images.clear();

      // 添加新图片
      for (var result in results) {
        try {
          final imageData = {'url': result.imageUrl, 'path': result.imagePath, 'articleId': articleModel.id};
          final imageModel = ImageRepository.createWithData(imageData, articleModel);
          await ImageRepository.create(imageModel);
        } catch (e) {
          logger.e("[WebpageParserService] 保存单个图片失败: $e");
        }
      }

      logger.i("[WebpageParserService] 图片保存完成 ${articleModel.id}");
    } catch (e) {
      logger.e("[WebpageParserService] 保存图片失败: $e");
    }
  }

  /// 保存截图
  Future<void> _saveScreenshots(ArticleModel articleModel, List<String> screenshotPaths) async {
    try {
      logger.i("[WebpageParserService] 开始保存截图: ${screenshotPaths.length}");

      // 清除原有截图
      articleModel.screenshots.clear();

      // 添加新截图
      for (var path in screenshotPaths) {
        try {
          final screenshotData = {'path': path, 'articleId': articleModel.id};
          final screenshotModel = ScreenshotRepository.createWithData(screenshotData, articleModel);
          await ScreenshotRepository.create(screenshotModel);
        } catch (e) {
          logger.e("[WebpageParserService] 保存单个截图失败: $e");
        }
      }

      logger.i("[WebpageParserService] 截图保存完成 ${articleModel.id}");
    } catch (e) {
      logger.e("[WebpageParserService] 保存截图失败: $e");
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
  final List<String> imageUrls;
  final List<String> screenshots;

  WebpageData({
    required this.title,
    required this.excerpt,
    required this.htmlContent,
    required this.textContent,
    required this.publishedTime,
    required this.imageUrls,
    required this.screenshots,
  });

  /// 创建空的网页数据
  factory WebpageData.empty() {
    return WebpageData(
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
