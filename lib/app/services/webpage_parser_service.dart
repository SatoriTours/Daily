import 'dart:async';
import 'package:daily_satori/app/utils/utils.dart';
import 'package:flutter/widgets.dart';
import 'package:daily_satori/app/components/webview/headless_webview.dart';
import 'package:daily_satori/app/modules/articles/controllers/articles_controller.dart';

import 'package:daily_satori/app/services/ai_service/ai_article_processor.dart';
import 'package:daily_satori/app/services/logger_service.dart';
import 'package:daily_satori/app/services/state/article_state_service.dart';
import 'package:daily_satori/app/objectbox/article.dart';
import 'package:daily_satori/app/repositories/article_repository.dart';
// 清理未使用的依赖（AI 具体处理已在 AiArticleProcessor 内）
import 'package:daily_satori/app/utils/string_extensions.dart';
import 'package:get/get.dart';
import 'package:daily_satori/app/modules/article_detail/controllers/article_detail_controller.dart';

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
    logger.i("[网页解析][API] ▶ 保存网页请求: URL=$url, 更新=$isUpdate, ID=$articleID");

    try {
      // 步骤1: 验证输入并处理文章初始化
      final article = await _processArticleInitialization(url, comment, isUpdate, articleID);

      // 步骤2: 获取并保存网页内容
      final fetched = await _processWebContentFetch(article);

      // 步骤3: 处理AI任务（仅在抓取成功时），异步执行
      if (fetched) {
        _processAiTasks(article);
      }

      return article;
    } catch (e, stackTrace) {
      logger.e("[网页解析][API] 处理失败: $e");
      logger.e(stackTrace.toString());

      if (isUpdate && articleID > 0) {
        await _markArticleAsFailed(articleID, "处理失败: $e");
        final article = ArticleRepository.d.findModel(articleID);
        if (article != null) {
          return article;
        }
      }

      // 返回已有文章（若有），避免抛错到 UI
      final fallback = ArticleRepository.d.findModel(articleID);
      if (fallback != null) return fallback;
      throw Exception("保存网页失败: $e");
    }
  }

  // ====================== 内部处理方法 ======================

  /// 处理文章初始化阶段：验证URL、创建或更新文章
  Future<ArticleModel> _processArticleInitialization(String url, String comment, bool isUpdate, int articleID) async {
    logger.i("[网页解析][初始化] ▶ 开始文章初始化 isUpdate=$isUpdate, articleID=$articleID");

    // 验证URL是否为空
    if (url.isNullOrEmpty) {
      throw Exception("URL不能为空");
    }

    // 检查URL是否已存在
    final existingArticle = await ArticleRepository.d.findByUrl(url);

    // 处理已存在的情况
    if (existingArticle != null) {
      if (!isUpdate) {
        // 新增模式但URL已存在，抛出错误
        throw Exception("网页已存在，无法重复添加");
      } else {
        // 更新模式：如果提供的articleID与已存在的不一致，使用已存在的
        if (articleID > 0 && articleID != existingArticle.id) {
          logger.w("[网页解析][初始化] 提供的ID($articleID)与URL对应的文章ID(${existingArticle.id})不一致，使用已存在的文章");
        }
        // 使用已存在文章的ID进行重置
        final article = await _resetExistingArticle(existingArticle.id, comment);
        logger.i("[网页解析][初始化] 重置已存在文章 #${existingArticle.id}");
        return article;
      }
    }

    // URL不存在的情况
    if (isUpdate && articleID > 0) {
      // 更新模式但URL不存在，说明要更新的文章URL已改变，重置该文章
      final article = await _resetExistingArticle(articleID, comment);
      logger.i("[网页解析][初始化] 重置现有文章(URL已变更) #$articleID");
      return article;
    }

    // 创建新文章
    final article = await _createNewArticle(url, comment);
    logger.i("[网页解析][初始化] 创建新文章 #${article.id}");
    logger.i("[网页解析][初始化] ◀ 文章初始化完成: #${article.id}");
    return article;
  }

  /// 处理网页内容获取阶段：获取标题、内容和封面图
  Future<bool> _processWebContentFetch(ArticleModel article) async {
    final articleId = article.id;
    logger.i("[网页解析][内容获取] ▶ 开始获取网页内容: #$articleId, URL: ${article.url}");

    try {
      // 获取网页内容
      final headlessWebView = HeadlessWebView();
      final result = await headlessWebView.loadAndParseUrl(article.url!);

      // 验证内容
      if (result.title.isEmpty) {
        throw Exception("网页标题为空");
      }

      if (result.htmlContent.isEmpty || result.htmlContent.length < AiArticleConstants.minHtmlLength) {
        throw Exception("HTML内容为空或过短(${result.htmlContent.length}字节)");
      }

      if (result.textContent.isEmpty || result.textContent.length < AiArticleConstants.minTextLength) {
        throw Exception("文本内容为空或过短(${result.textContent.length}字节)");
      }

      // 保存内容到文章
      article.title = result.title;
      article.content = result.textContent;
      article.htmlContent = result.htmlContent;
      article.coverImageUrl = result.coverImageUrl;
      article.updatedAt = DateTime.now().toUtc();
      article.status = ArticleStatus.webContentFetched;

      await ArticleRepository.d.updateModel(article);

      logger.i("[网页解析][内容获取] ◀ 网页内容获取成功: #$articleId");
      // 抓取阶段完成后也通知 UI，一方面展示“处理中”，另一方面让详情页先看到最新抓取内容
      _notifyUI(articleId);
      return true;
    } catch (e) {
      logger.e("[网页解析][内容获取] 获取失败: #$articleId, $e");
      await _markArticleAsFailed(articleId, "网页内容获取失败: $e");
      return false;
    }
  }

  /// 处理AI任务阶段：处理标题、摘要、Markdown和图片
  Future<void> _processAiTasks(ArticleModel article) async {
    final articleId = article.id;
    // 重新获取最新的文章数据
    final updatedArticle = ArticleRepository.d.findModel(articleId);
    if (updatedArticle == null) {
      throw Exception("无法找到文章: $articleId");
    }

    logger.i("[网页解析][AI处理] ▶ 开始AI处理: #$articleId");

    try {
      // 创建任务列表
      await AiArticleProcessor.i.processAll(updatedArticle);

      // 更新文章状态为完成
      await ArticleRepository.d.updateField(articleId, ArticleFieldName.status, ArticleStatus.completed);

      logger.i("[网页解析][API] ◀ 处理完成: #$articleId");
      _notifyUI(articleId);

      logger.i("[网页解析][AI处理] ◀ AI处理完成: #$articleId");
    } catch (e) {
      logger.e("[网页解析][AI处理] 处理失败: #$articleId, $e");
      await _markArticleAsFailed(articleId, "AI处理失败: $e");
      // 不再抛出异常，避免未捕获的 Future 错误
    }
  }

  // ====================== AI处理相关方法 ======================

  // 旧 AI 处理方法已抽取到 AiArticleProcessor

  // ====================== 文章创建与更新 ======================

  /// 创建新文章
  Future<ArticleModel> _createNewArticle(String url, String comment) async {
    logger.d("[网页解析][创建] ▶ 开始创建新文章: $url");

    final now = DateTime.now().toUtc();
    final article = Article(
      url: url,
      title: '正在加载...',
      comment: comment,
      pubDate: now,
      createdAt: now,
      updatedAt: now,
      status: ArticleStatus.pending,
    );

    final articleModel = ArticleModel(article);
    final id = await ArticleRepository.d.save(articleModel);
    final savedModel = ArticleRepository.d.findModel(id);

    if (savedModel == null || savedModel.entity.id <= 0) {
      throw Exception("创建文章记录失败");
    }

    logger.d("[网页解析][创建] ◀ 新文章创建成功: #${savedModel.entity.id}");
    return savedModel;
  }

  /// 重置现有文章以更新内容
  Future<ArticleModel> _resetExistingArticle(int articleId, String comment) async {
    logger.d("[网页解析][更新] ▶ 开始重置文章: #$articleId");

    final article = ArticleRepository.d.findModel(articleId);
    if (article == null) {
      throw Exception("找不到要更新的文章: $articleId");
    }

    // 只重置AI相关字段，保留原始内容
    article.comment = comment;
    _resetArticleAiFields(article);
    article.status = ArticleStatus.pending;
    article.updatedAt = DateTime.now().toUtc();

    await ArticleRepository.d.updateModel(article);
    logger.d("[网页解析][更新] ◀ 文章重置成功: #$articleId");

    return article;
  }

  /// 重置文章的AI字段
  void _resetArticleAiFields(ArticleModel article) {
    article.aiTitle = '';
    article.aiContent = '';
    article.aiMarkdownContent = '';
    article.coverImage = '';
  }

  // ====================== 错误处理 ======================

  /// 将文章标记为失败状态
  Future<void> _markArticleAsFailed(int articleId, String errorMessage) async {
    final article = ArticleRepository.d.findModel(articleId);
    if (article == null) {
      logger.e("[网页解析][错误] 无法找到文章 #$articleId");
      return;
    }

    article.status = ArticleStatus.error;
    article.aiContent = errorMessage;
    article.updatedAt = DateTime.now().toUtc();

    await ArticleRepository.d.updateModel(article);
  }

  // ====================== 辅助方法 ======================

  /// 通知UI更新
  void _notifyUI(int articleId) {
    try {
      // 验证articleId的有效性
      if (articleId <= 0) {
        logger.w("[网页解析][UI] 无效的articleId: $articleId，跳过UI通知");
        return;
      }

      final article = ArticleRepository.d.findModel(articleId);
      if (article == null) {
        logger.w("[网页解析][UI] 未找到文章 #$articleId，跳过UI通知");
        return;
      }

      // 通知文章控制器更新
      if (Get.isRegistered<ArticlesController>()) {
        Get.find<ArticlesController>().updateArticle(articleId);
        logger.d("[网页解析][UI] 已通知UI更新文章 #$articleId");
      }

      // 通知全局状态服务文章已更新
      if (Get.isRegistered<ArticleStateService>()) {
        Get.find<ArticleStateService>().notifyArticleUpdated(article);
        logger.d("[网页解析][UI] 已通知ArticleStateService更新文章 #$articleId");
      }

      // 若详情页正在展示该文章，直接更新控制器中的文章数据
      if (Get.isRegistered<ArticleDetailController>()) {
        final c = Get.find<ArticleDetailController>();
        if (c.articleModel.id == articleId) {
          logger.d("[网页解析][UI] 检测到详情页正在显示文章 #$articleId");

          // 使用 SchedulerBinding 确保更新在下一帧执行，避免时序问题
          WidgetsBinding.instance.addPostFrameCallback((_) {
            // 直接更新控制器中的文章模型和响应式引用
            c.articleModel = article;
            c.article.value = article;
            // 刷新标签显示
            c.loadTags();
            logger.d("[网页解析][UI] 已在下一帧更新详情页控制器 #$articleId，状态: ${article.status}");
          });
        }
      }
    } catch (e) {
      // 控制器不存在时静默处理
      logger.e("[网页解析][UI] 通知UI更新失败: $e");
    }
  }
}
