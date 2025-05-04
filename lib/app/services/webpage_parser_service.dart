import 'dart:async';
import 'package:daily_satori/app/components/webview/headless_webview.dart';
import 'package:daily_satori/app/modules/articles/controllers/articles_controller.dart';

import 'package:daily_satori/app/services/ai_service/ai_service.dart';
import 'package:daily_satori/app/services/logger_service.dart';
import 'package:daily_satori/app/repositories/article_repository.dart';
import 'package:daily_satori/app/repositories/tag_repository.dart';
import 'package:daily_satori/app/services/http_service.dart';
import 'package:daily_satori/app/utils/string_extensions.dart';
import 'package:daily_satori/global.dart';
import 'package:get/get.dart';

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
      await _processWebContentFetch(article);

      // 步骤3: 处理AI任务
      await _processAiTasks(article);

      // 更新文章状态为完成
      await ArticleRepository.updateField(article.id, ArticleFieldName.status, ArticleStatus.completed);

      logger.i("[网页解析][API] ◀ 处理完成: #${article.id}");
      _notifyUI(article.id);
      return article;
    } catch (e, stackTrace) {
      logger.e("[网页解析][API] 处理失败: $e");
      logger.e(stackTrace.toString());

      if (isUpdate && articleID > 0) {
        await _markArticleAsFailed(articleID, "处理失败: $e");
        final article = ArticleRepository.find(articleID);
        if (article != null) {
          return article;
        }
      }

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
    final existingArticle = await ArticleRepository.findByUrl(url);

    // 处理已存在的情况
    if (existingArticle != null) {
      if (!isUpdate) {
        throw Exception("网页已存在，无法重复添加");
      } else if (articleID <= 0) {
        throw Exception("文章ID无效，无法更新");
      }
    }

    // 创建或更新文章
    final ArticleModel article;

    if (isUpdate && articleID > 0) {
      // 如果是更新，重置现有文章
      article = await _resetExistingArticle(articleID, comment);
      logger.i("[网页解析][初始化] 重置现有文章 #$articleID");
    } else {
      // 创建新文章
      article = await _createNewArticle(url, comment);
      logger.i("[网页解析][初始化] 创建新文章 #${article.id}");
    }

    logger.i("[网页解析][初始化] ◀ 文章初始化完成: #${article.id}");
    return article;
  }

  /// 处理网页内容获取阶段：获取标题、内容和封面图
  Future<void> _processWebContentFetch(ArticleModel article) async {
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

      if (result.htmlContent.isEmpty || result.htmlContent.length < 100) {
        throw Exception("HTML内容为空或过短(${result.htmlContent.length}字节)");
      }

      if (result.textContent.isEmpty || result.textContent.length < 50) {
        throw Exception("文本内容为空或过短(${result.textContent.length}字节)");
      }

      // 保存内容到文章
      article.title = result.title;
      article.content = result.textContent;
      article.htmlContent = result.htmlContent;
      article.coverImageUrl = result.coverImageUrl;
      article.updatedAt = DateTime.now().toUtc();
      article.status = ArticleStatus.webContentFetched;

      await ArticleRepository.update(article);

      logger.i("[网页解析][内容获取] ◀ 网页内容获取成功: #$articleId");
    } catch (e) {
      logger.e("[网页解析][内容获取] 获取失败: #$articleId, $e");
      await _markArticleAsFailed(articleId, "网页内容获取失败: $e");
      throw Exception("获取网页内容失败: $e");
    }
  }

  /// 处理AI任务阶段：处理标题、摘要、Markdown和图片
  Future<void> _processAiTasks(ArticleModel article) async {
    final articleId = article.id;
    // 重新获取最新的文章数据
    final updatedArticle = ArticleRepository.find(articleId);
    if (updatedArticle == null) {
      throw Exception("无法找到文章: $articleId");
    }

    logger.i("[网页解析][AI处理] ▶ 开始AI处理: #$articleId");

    try {
      // 创建任务列表
      final List<Future<void>> tasks = [
        _processTitle(updatedArticle),
        _processSummary(updatedArticle),
        _processMarkdown(updatedArticle),
        _processImage(updatedArticle),
      ];

      // 并行执行所有AI处理任务
      await Future.wait(tasks);

      logger.i("[网页解析][AI处理] ◀ AI处理完成: #$articleId");
    } catch (e) {
      logger.e("[网页解析][AI处理] 处理失败: #$articleId, $e");
      await _markArticleAsFailed(articleId, "AI处理失败: $e");
      throw Exception("AI处理失败: $e");
    }
  }

  // ====================== AI处理相关方法 ======================

  /// 处理标题：翻译和概括
  Future<void> _processTitle(ArticleModel article) async {
    final articleId = article.id;
    final title = article.title ?? '';

    if (title.isEmpty) {
      logger.w("[网页解析][AI:标题] 标题为空，跳过处理 #$articleId");
      return;
    }

    logger.i("[网页解析][AI:标题] ▶ 开始处理标题 #$articleId");

    var aiTitle = title;
    try {
      if (!StringUtils.isChinese((title))) {
        aiTitle = await AiService.i.translate(title.trim());
      }

      // 如果标题太长，进行概括
      if (aiTitle.length >= 50) {
        logger.d("[网页解析][AI:标题] 标题太长，进行概括 #$articleId");
        aiTitle = await AiService.i.summarizeOneLine(aiTitle);
      }

      if (aiTitle.isEmpty) {
        logger.w("[网页解析][AI:标题] 处理结果为空 #$articleId");
        return;
      }

      await ArticleRepository.updateField(articleId, ArticleFieldName.aiTitle, aiTitle);
      logger.i("[网页解析][AI:标题] ◀ 标题处理成功 #$articleId");
    } catch (e) {
      logger.e("[网页解析][AI:标题] 处理失败 #$articleId: $e");
      throw Exception("处理标题失败: $e");
    }
  }

  /// 处理摘要和标签
  Future<void> _processSummary(ArticleModel article) async {
    final articleId = article.id;
    final content = article.content ?? '';

    if (content.isEmpty) {
      logger.w("[网页解析][AI:摘要] 内容为空，跳过处理 #$articleId");
      return;
    }

    logger.i("[网页解析][AI:摘要] ▶ 开始处理摘要 #$articleId");

    try {
      final (summary, tags) = await AiService.i.summarize(content.trim());

      if (summary.isEmpty) {
        logger.w("[网页解析][AI:摘要] 处理结果为空 #$articleId");
        return;
      }

      await ArticleRepository.updateField(articleId, ArticleFieldName.aiContent, summary);
      await _saveTags(article, tags);

      logger.i("[网页解析][AI:摘要] ◀ 摘要处理成功，提取了 ${tags.length} 个标签 #$articleId");
    } catch (e) {
      logger.e("[网页解析][AI:摘要] 处理失败 #$articleId: $e");
      throw Exception("处理摘要失败: $e");
    }
  }

  /// 处理Markdown转换
  Future<void> _processMarkdown(ArticleModel article) async {
    final articleId = article.id;
    final htmlContent = article.htmlContent ?? '';

    if (htmlContent.isEmpty) {
      logger.w("[网页解析][AI:Markdown] HTML内容为空，跳过处理 #$articleId");
      return;
    }

    logger.i("[网页解析][AI:Markdown] ▶ 开始处理Markdown #$articleId");

    try {
      final markdown = await AiService.i.convertHtmlToMarkdown(htmlContent);

      if (markdown.isEmpty) {
        logger.w("[网页解析][AI:Markdown] 处理结果为空 #$articleId");
        return;
      }

      await ArticleRepository.updateField(articleId, ArticleFieldName.aiMarkdownContent, markdown);
      logger.i("[网页解析][AI:Markdown] ◀ Markdown处理成功 #$articleId");
    } catch (e) {
      logger.e("[网页解析][AI:Markdown] 处理失败 #$articleId: $e");
      throw Exception("处理Markdown失败: $e");
    }
  }

  /// 处理图片下载
  Future<void> _processImage(ArticleModel article) async {
    final articleId = article.id;
    final imageUrl = article.coverImageUrl ?? '';

    if (imageUrl.isEmpty) {
      logger.w("[网页解析][AI:图片] 图片URL为空，跳过处理 #$articleId");
      return;
    }

    logger.i("[网页解析][AI:图片] ▶ 开始处理图片 #$articleId: $imageUrl");

    try {
      final imagePath = await HttpService.i.downloadImage(imageUrl);

      if (imagePath.isNotEmpty) {
        await ArticleRepository.updateField(articleId, ArticleFieldName.coverImage, imagePath);
        logger.i("[网页解析][AI:图片] ◀ 图片处理成功 #$articleId");
      } else {
        logger.w("[网页解析][AI:图片] 图片下载结果为空 #$articleId");
      }
    } catch (e) {
      logger.e("[网页解析][AI:图片] 处理失败 #$articleId: $e");
      throw Exception("处理图片失败: $e");
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

  // ====================== 辅助方法 ======================

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
