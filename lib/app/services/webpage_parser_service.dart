import 'dart:async';
import 'dart:collection';
import 'dart:io';
import 'package:flutter/services.dart';
import 'package:flutter_inappwebview/flutter_inappwebview.dart';

import 'package:daily_satori/app/services/ai_service/ai_service.dart';
import 'package:daily_satori/app/services/logger_service.dart';
import 'package:daily_satori/app/repositories/article_repository.dart';
import 'package:daily_satori/app/models/article_model.dart';
import 'package:daily_satori/app/objectbox/article.dart';
import 'package:daily_satori/app/repositories/image_repository.dart';
import 'package:daily_satori/app/repositories/screenshot_repository.dart';
import 'package:daily_satori/app/repositories/tag_repository.dart';
import 'package:daily_satori/app/services/http_service.dart';
import 'package:daily_satori/app/services/file_service.dart';

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

  // 当前正在处理的文章ID集合
  final Set<int> _processingArticles = <int>{};

  // 最大并发处理数量
  static const int _maxConcurrent = 5;

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
  ///
  /// [url] 网页URL
  /// [comment] 用户备注
  /// [isUpdate] 是否为更新操作
  /// [articleID] 更新时的文章ID
  Future<ArticleModel> saveWebpage({
    required String url,
    required String comment,
    bool isUpdate = false,
    int articleID = 0,
  }) async {
    logger.i("[WebpageParserService] 开始保存网页基本信息: $url");

    // 检查文章是否已存在
    if (!isUpdate) {
      if (await ArticleRepository.isArticleExists(url)) {
        throw Exception('网页已存在');
      }
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
      status: 'pending', // 标记为待处理状态
    );

    // 将文章ID添加到处理队列
    _addToProcessingQueue(articleModel.id);

    return articleModel;
  }

  /// 将文章ID添加到处理队列
  void _addToProcessingQueue(int articleId) {
    if (!_processingQueue.contains(articleId) && !_processingArticles.contains(articleId)) {
      _processingQueue.add(articleId);
      logger.i("[WebpageParserService] 已将文章 #$articleId 添加到处理队列");

      // 尝试立即处理队列
      _processQueue();
    }
  }

  /// 检查数据库中待处理的文章
  Future<void> _checkPendingArticles() async {
    try {
      // 查询数据库中所有标记为'pending'状态的文章
      final pendingArticles = await _findArticlesByStatus('pending');

      if (pendingArticles.isEmpty) {
        logger.d("[WebpageParserService] 没有待处理的文章");
        return;
      }

      logger.i("[WebpageParserService] 发现 ${pendingArticles.length} 篇待处理文章");

      // 将待处理文章添加到队列
      for (final article in pendingArticles) {
        if (!_processingQueue.contains(article.id) && !_processingArticles.contains(article.id)) {
          _processingQueue.add(article.id);
        }
      }

      // 处理队列
      _processQueue();
    } catch (e) {
      logger.e("[WebpageParserService] 检查待处理文章失败: $e");
    }
  }

  /// 通过状态查找文章
  Future<List<Article>> _findArticlesByStatus(String status) async {
    try {
      // 使用仓库方法查询指定状态的文章
      final List<ArticleModel> articles = ArticleRepository.findByStatus(status);
      // 转换为Article实体列表
      return articles.map((model) => model.entity).toList();
    } catch (e, stackTrace) {
      logger.e("[WebpageParserService] 查找$status状态文章失败: $e\n$stackTrace");
      return [];
    }
  }

  /// 处理队列
  Future<void> _processQueue() async {
    // 如果队列为空，直接返回
    if (_processingQueue.isEmpty) return;

    // 如果当前处理的文章数量已达到最大并发数，直接返回
    if (_processingArticles.length >= _maxConcurrent) return;

    // 计算可以新增处理的文章数量
    final availableSlots = _maxConcurrent - _processingArticles.length;

    // 从队列中取出文章ID进行处理
    for (int i = 0; i < availableSlots && _processingQueue.isNotEmpty; i++) {
      final articleId = _processingQueue.removeFirst();

      // 标记为正在处理
      _processingArticles.add(articleId);

      // 获取文章实例
      final article = ArticleRepository.find(articleId);
      if (article == null) {
        logger.e("[WebpageParserService] 无法找到文章 #$articleId");
        _processingArticles.remove(articleId);
        continue;
      }

      // 异步处理文章
      _processWebpageAsync(article)
          .then((_) {
            // 处理完成后从正在处理集合中移除
            _processingArticles.remove(articleId);

            // 继续处理队列
            _processQueue();
          })
          .catchError((e) {
            logger.e("[WebpageParserService] 处理文章 #$articleId 失败: $e");
            _processingArticles.remove(articleId);

            // 继续处理队列
            _processQueue();
          });
    }

    logger.i("[WebpageParserService] 当前正在处理 ${_processingArticles.length} 篇文章，队列中有 ${_processingQueue.length} 篇文章待处理");
  }

  /// 异步处理网页内容
  Future<void> _processWebpageAsync(ArticleModel articleModel) async {
    logger.i("[WebpageParserService] 开始处理文章 #${articleModel.id}: ${articleModel.url}");

    try {
      // 更新文章状态为处理中
      _updateArticleStatus(articleModel, 'processing');

      // 获取网页内容
      final webpageData = await _fetchWebpageContent(articleModel.url);

      // 并行处理各项任务
      final titleFuture = _processAiTitle(webpageData.title);
      final contentFuture = _processAiContent(webpageData.textContent);
      final imagesFuture = _processImages(webpageData.imageUrls);

      final aiTitle = await titleFuture;
      final aiContentResult = await contentFuture;
      final images = await imagesFuture;

      // 更新文章数据
      articleModel.title = webpageData.title;
      articleModel.aiTitle = aiTitle;
      articleModel.content = webpageData.textContent;
      articleModel.aiContent = aiContentResult.$1;
      articleModel.htmlContent = webpageData.htmlContent;
      _updateArticleStatus(articleModel, 'completed'); // 标记为处理完成
      articleModel.updatedAt = DateTime.now().toUtc();

      // 保存到数据库
      await ArticleRepository.update(articleModel);

      // 保存关联数据
      await _saveTags(articleModel, aiContentResult.$2);
      await _saveImages(articleModel, images);
      await _saveScreenshots(articleModel, webpageData.screenshots);

      // 保存到数据库，确保图片，截图，标签关联到文章
      await ArticleRepository.update(articleModel);

      logger.i("[WebpageParserService] 文章 #${articleModel.id} 处理完成");
    } catch (e, stackTrace) {
      logger.e("[WebpageParserService] 文章 #${articleModel.id} 处理失败: $e");
      logger.e(stackTrace);

      // 标记处理错误信息
      articleModel.aiContent = "处理失败：$e";
      _updateArticleStatus(articleModel, 'error'); // 标记为处理失败
      articleModel.updatedAt = DateTime.now().toUtc();
      await ArticleRepository.update(articleModel);
    }
  }

  /// 更新文章状态
  void _updateArticleStatus(ArticleModel articleModel, String status) {
    // 使用extraData来存储状态信息
    articleModel.setExtraData('status', status);
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

      // 创建一个新的ArticleModel实例
      final data = {
        'title': '正在加载...',
        'aiTitle': '',
        'content': '',
        'aiContent': '',
        'htmlContent': '',
        'url': url,
        'pubDate': DateTime.now().toUtc(),
        'createdAt': DateTime.now().toUtc(),
        'updatedAt': DateTime.now().toUtc(),
        'comment': comment,
        'extraData': {'status': status}, // 使用extraData存储状态
      };

      // 创建文章模型
      final articleModel = ArticleRepository.createArticleModel(data);

      // 保存到数据库
      final id = await ArticleRepository.create(articleModel);
      if (id <= 0) {
        throw Exception("创建文章失败，无法获取有效ID");
      }

      // 重新获取保存后的文章，确保有正确的ID
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

  /// 无头浏览器模式获取网页内容
  Future<WebpageData> _fetchWebpageContent(String? url) async {
    if (url == null || url.isEmpty) {
      logger.e("[WebpageParserService] URL为空，无法获取内容");
      return WebpageData.empty();
    }

    logger.i("[WebpageParserService] 开始获取网页内容: $url");

    // 创建无头浏览器
    final headlessWebView = HeadlessInAppWebView(
      initialUrlRequest: URLRequest(url: WebUri(url)),
      initialSettings: InAppWebViewSettings(
        javaScriptEnabled: true,
        useShouldInterceptFetchRequest: true,
        mediaPlaybackRequiresUserGesture: false,
        allowsInlineMediaPlayback: true,
        iframeAllow: "camera; microphone",
        iframeAllowFullscreen: true,
      ),
      onConsoleMessage: (controller, consoleMessage) {
        logger.d("[HeadlessWebView] ${consoleMessage.message}");
      },
    );

    String title = '';
    String excerpt = '';
    String htmlContent = '';
    String textContent = '';
    String publishedTime = '';
    List<String> imageUrls = [];
    List<String> screenshots = [];

    try {
      // 运行无头浏览器
      await headlessWebView.run();

      // 获取控制器
      final controller = headlessWebView.webViewController!;

      // 等待页面加载完成
      await Future.delayed(const Duration(seconds: 3));

      // 注入解析脚本
      await controller.injectJavascriptFileFromAsset(assetFilePath: "assets/js/Readability.js");
      await controller.injectJavascriptFileFromAsset(assetFilePath: "assets/js/parse_content.js");

      // 执行解析
      final result = await controller.evaluateJavascript(source: "parseContent()");

      if (result != null) {
        // 转换为Map<String, dynamic>类型
        final resultMap = result as Map<dynamic, dynamic>;
        title = resultMap['title']?.toString() ?? '';
        excerpt = resultMap['excerpt']?.toString() ?? '';
        htmlContent = resultMap['htmlContent']?.toString() ?? '';
        textContent = resultMap['textContent']?.toString() ?? '';
        publishedTime = resultMap['publishedTime']?.toString() ?? '';

        if (resultMap['imageUrls'] != null && resultMap['imageUrls'] is List) {
          imageUrls = (resultMap['imageUrls'] as List).map((e) => e.toString()).toList();
        }
      }

      // 捕获网页截图
      screenshots = await _captureScreenshots(controller);
    } catch (e) {
      logger.e("[WebpageParserService] 获取网页内容失败: $e");
    } finally {
      // 关闭无头浏览器
      await headlessWebView.dispose();
    }

    return WebpageData(
      title: title,
      excerpt: excerpt,
      htmlContent: htmlContent,
      textContent: textContent,
      publishedTime: publishedTime,
      imageUrls: imageUrls,
      screenshots: screenshots,
    );
  }

  /// 捕获网页截图
  Future<List<String>> _captureScreenshots(InAppWebViewController controller) async {
    try {
      // 这里调用现有的截图功能
      final screenshot = await controller.takeScreenshot();
      if (screenshot != null) {
        final filePath = await _saveScreenshot(screenshot);
        if (filePath.isNotEmpty) {
          return [filePath];
        }
      }
    } catch (e) {
      logger.e("[WebpageParserService] 截图失败: $e");
    }
    return [];
  }

  /// 保存截图
  Future<String> _saveScreenshot(Uint8List screenshotData) async {
    try {
      // 使用File服务保存图片
      final filePath = FileService.i.getScreenshotPath();
      final file = File(filePath);
      await file.writeAsBytes(screenshotData);
      return filePath;
    } catch (e) {
      logger.e("[WebpageParserService] 保存截图失败: $e");
      return '';
    }
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

      // 删除数据库中原有的标签记录
      // TagRepository.deleteByArticleId(articleModel.id);

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
      // 清除数据库中原有的图片记录
      articleModel.images.clear();

      // 删除数据库中原有的图片记录
      // ImageRepository.deleteByArticleId(articleModel.id);

      // 添加新图片
      for (var result in results) {
        try {
          // 创建图片模型并保存
          final imageData = {'url': result.imageUrl, 'path': result.imagePath, 'articleId': articleModel.id};
          final imageModel = ImageRepository.createWithData(imageData, articleModel);
          await ImageRepository.create(imageModel);
        } catch (e) {
          logger.e("[WebpageParserService] 保存单个图片失败: $e");
          continue;
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

      // 删除数据库中原有的截图记录
      // ScreenshotRepository.deleteByArticleId(articleModel.id);

      // 添加新截图
      for (var path in screenshotPaths) {
        try {
          // 创建截图模型并保存
          final screenshotData = {'path': path, 'articleId': articleModel.id};
          final screenshotModel = ScreenshotRepository.createWithData(screenshotData, articleModel);
          await ScreenshotRepository.create(screenshotModel);
        } catch (e) {
          logger.e("[WebpageParserService] 保存单个截图失败: $e");
          continue;
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
