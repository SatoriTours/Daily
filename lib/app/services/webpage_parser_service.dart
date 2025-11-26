import 'dart:async';
import 'package:daily_satori/app/data/index.dart';
import 'package:flutter/widgets.dart';
import 'package:daily_satori/app/components/webview/headless_webview.dart';
import 'package:daily_satori/app/pages/articles/controllers/articles_controller.dart';

import 'package:daily_satori/app/services/ai_service/ai_article_processor.dart';
import 'package:daily_satori/app/services/logger_service.dart';
import 'package:daily_satori/app/services/state/article_state_service.dart';
import 'package:daily_satori/app/objectbox/article.dart';
// 清理未使用的依赖（AI 具体处理已在 AiArticleProcessor 内）
import 'package:daily_satori/app/utils/string_extensions.dart';
import 'package:get/get.dart';
import 'package:daily_satori/app/pages/article_detail/controllers/article_detail_controller.dart';

/// 网页解析服务 - 负责网页内容获取、AI处理和持久化
class WebpageParserService {
  // ====================== 单例实现 ======================
  WebpageParserService._privateConstructor();
  static final WebpageParserService _instance = WebpageParserService._privateConstructor();
  static WebpageParserService get i => _instance;

  // ====================== 公共API ======================

  /// 保存网页（对外API）
  ///
  /// 用于创建新文章或更新现有文章，并处理网页内容
  Future<ArticleModel> saveWebpage({
    required String url,
    required String comment,
    bool isUpdate = false,
    int articleID = 0,
  }) async {
    logger.i("[保存网页] 开始 - URL=$url, 更新=$isUpdate, ID=$articleID");

    try {
      // 步骤1: 初始化文章
      final article = await _processArticleInitialization(url, comment, isUpdate, articleID);

      // 步骤2: 获取网页内容
      final fetched = await _processWebContentFetch(article);

      // 步骤3: AI处理（异步执行）
      if (fetched) {
        _processAiTasks(article);
      }

      logger.i("[保存网页] 完成 - 文章ID=${article.id}");
      return article;
    } catch (e, stackTrace) {
      logger.e("[保存网页] 失败 - $e");
      logger.e(stackTrace.toString());

      if (isUpdate && articleID > 0) {
        await _markArticleAsFailed(articleID, "处理失败: $e");
        final article = ArticleRepository.i.findModel(articleID);
        if (article != null) return article;
      }

      final fallback = ArticleRepository.i.findModel(articleID);
      if (fallback != null) return fallback;

      rethrow;
    }
  }

  // ====================== 内部处理方法 ======================

  /// 处理文章初始化阶段：验证URL、创建或更新文章
  Future<ArticleModel> _processArticleInitialization(String url, String comment, bool isUpdate, int articleID) async {
    logger.i("[初始化文章] 开始");

    // 验证URL
    if (url.isNullOrEmpty) {
      throw Exception("URL不能为空");
    }

    final existingArticle = ArticleRepository.i.findByUrl(url);

    // 情况1: URL已存在
    if (existingArticle != null) {
      if (!isUpdate) {
        throw Exception("网页已存在，无法重复添加");
      }

      // 更新模式 - 重置已存在的文章
      final article = _resetExistingArticle(existingArticle.id, comment);
      logger.i("[初始化文章] 成功 - 重置已存在文章ID=${existingArticle.id}");
      return article;
    }

    // 情况2: 更新模式但URL已变更
    if (isUpdate && articleID > 0) {
      final article = _resetExistingArticle(articleID, comment);
      logger.i("[初始化文章] 成功 - 重置文章(URL已变)ID=$articleID");
      return article;
    }

    // 情况3: 创建新文章
    final article = _createNewArticle(url, comment);
    logger.i("[初始化文章] 成功 - 创建新文章ID=${article.id}");
    return article;
  }

  /// 处理网页内容获取阶段：获取标题、内容和封面图
  Future<bool> _processWebContentFetch(ArticleModel article) async {
    final articleId = article.id;
    logger.i("[获取网页内容] 开始 - 文章ID=$articleId");

    try {
      final headlessWebView = HeadlessWebView();
      final result = await headlessWebView.loadAndParseUrl(article.url!);

      // 验证内容有效性
      if (result.title.isEmpty) {
        throw Exception("网页标题为空");
      }

      if (result.htmlContent.isEmpty || result.htmlContent.length < AiArticleConstants.minHtmlLength) {
        throw Exception("HTML内容为空或过短(${result.htmlContent.length}字节)");
      }

      if (result.textContent.isEmpty || result.textContent.length < AiArticleConstants.minTextLength) {
        throw Exception("文本内容为空或过短(${result.textContent.length}字节)");
      }

      // 保存内容
      article.title = result.title;
      article.content = result.textContent;
      article.htmlContent = result.htmlContent;
      article.coverImageUrl = result.coverImageUrl;
      article.updatedAt = DateTime.now().toUtc();
      article.status = ArticleStatus.webContentFetched;

      ArticleRepository.i.updateModel(article);
      _notifyUI(articleId);

      logger.i("[获取网页内容] 成功 - 文章ID=$articleId");
      return true;
    } catch (e) {
      logger.e("[获取网页内容] 失败 - 文章ID=$articleId, 错误: $e");
      await _markArticleAsFailed(articleId, "网页内容获取失败: $e");
      return false;
    }
  }

  /// 处理AI任务阶段：处理标题、摘要、Markdown和图片
  Future<void> _processAiTasks(ArticleModel article) async {
    logger.i("[AI处理] 开始 - 文章ID=${article.id}");

    try {
      // 重新获取最新文章数据
      final updatedArticle = ArticleRepository.i.findModel(article.id);
      if (updatedArticle == null) {
        throw Exception("无法找到文章: ${article.id}");
      }

      await AiArticleProcessor.i.processAll(updatedArticle);

      // 更新状态为完成
      ArticleRepository.i.updateField(article.id, ArticleFieldName.status, ArticleStatus.completed);
      _notifyUI(article.id);

      logger.i("[AI处理] 成功 - 文章ID=${article.id}");
    } catch (e) {
      logger.e("[AI处理] 失败 - 文章ID=${article.id}, 错误: $e");
      await _markArticleAsFailed(article.id, "AI处理失败: $e");
    }
  }

  // ====================== AI处理相关方法 ======================

  // 旧 AI 处理方法已抽取到 AiArticleProcessor

  // ====================== 文章创建与更新 ======================

  /// 创建新文章
  ArticleModel _createNewArticle(String url, String comment) {
    final now = DateTime.now().toUtc();
    final article = Article(
      url: url,
      title: '正在加载...',
      comment: comment,
      pubDate: now,
      createdAt: now,
      updatedAt: now,
      status: ArticleStatus.pending.value,
    );

    final articleModel = ArticleModel(article);
    final id = ArticleRepository.i.save(articleModel);
    final savedModel = ArticleRepository.i.findModel(id);

    if (savedModel == null || savedModel.entity.id <= 0) {
      throw Exception("创建文章记录失败");
    }

    return savedModel;
  }

  /// 重置现有文章以更新内容
  ArticleModel _resetExistingArticle(int articleId, String comment) {
    final article = ArticleRepository.i.findModel(articleId);
    if (article == null) {
      throw Exception("找不到要更新的文章: $articleId");
    }

    // 重置AI字段和状态
    article.comment = comment;
    article.aiTitle = '';
    article.aiContent = '';
    article.aiMarkdownContent = '';
    article.coverImage = '';
    article.status = ArticleStatus.pending;
    article.updatedAt = DateTime.now().toUtc();

    ArticleRepository.i.updateModel(article);
    return article;
  }

  // ====================== 错误处理 ======================

  /// 将文章标记为失败状态
  Future<void> _markArticleAsFailed(int articleId, String errorMessage) async {
    final article = ArticleRepository.i.findModel(articleId);
    if (article == null) {
      logger.e("[错误处理] 无法找到文章ID=$articleId");
      return;
    }

    article.status = ArticleStatus.error;
    article.aiContent = errorMessage;
    article.updatedAt = DateTime.now().toUtc();

    ArticleRepository.i.updateModel(article);
  }

  // ====================== 辅助方法 ======================

  /// 通知UI更新
  void _notifyUI(int articleId) {
    if (articleId <= 0) return;

    final article = ArticleRepository.i.findModel(articleId);
    if (article == null) return;

    try {
      // 通知文章列表更新
      if (Get.isRegistered<ArticlesController>()) {
        Get.find<ArticlesController>().updateArticle(articleId);
      }

      // 通知全局状态服务
      if (Get.isRegistered<ArticleStateService>()) {
        Get.find<ArticleStateService>().notifyArticleUpdated(article);
      }

      // 更新详情页（如果正在显示该文章）
      if (Get.isRegistered<ArticleDetailController>()) {
        final controller = Get.find<ArticleDetailController>();
        if (controller.articleModel.id == articleId) {
          WidgetsBinding.instance.addPostFrameCallback((_) {
            controller.articleModel = article;
            controller.article.value = article;
            controller.loadTags();
          });
        }
      }
    } catch (e) {
      logger.e("[UI通知] 失败 - $e");
    }
  }
}
