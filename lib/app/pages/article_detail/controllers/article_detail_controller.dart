import 'package:daily_satori/app_exports.dart';

class ArticleDetailController extends BaseController {
  // ========== 构造函数 ==========
  ArticleDetailController(super._appStateService, this._articleStateService);

  /// 当前文章模型
  late ArticleModel articleModel;

  /// 响应式文章引用（用于与列表共享引用时触发视图刷新）
  final Rxn<ArticleModel> article = Rxn<ArticleModel>();

  /// 文章标签字符串,以逗号分隔
  final tags = ''.obs;

  /// 状态服务
  final ArticleStateService _articleStateService;

  @override
  void onInit() {
    super.onInit();
    _loadArticle();
    _initActiveArticleListener();
    loadTags();
  }

  /// 初始化活跃文章监听器
  void _initActiveArticleListener() {
    // 监听活跃文章变化
    ever(_articleStateService.activeArticle, (activeArticle) {
      if (activeArticle != null && activeArticle.id == articleModel.id) {
        logger.d("[ArticleDetail] 检测到活跃文章更新: ${activeArticle.id}, 状态: ${activeArticle.status}");
        articleModel = activeArticle;
        article.value = activeArticle;
        loadTags();
      }
    });

    // 监听文章更新事件流
    ever(_articleStateService.articleUpdateEvent, (event) {
      if (event.affectsArticle(articleModel.id)) {
        logger.d("[ArticleDetail] 检测到文章事件: $event");

        switch (event.type) {
          case ArticleEventType.updated:
            if (event.article != null) {
              articleModel = event.article!;
              article.value = event.article!;
              loadTags();
            }
            break;
          case ArticleEventType.deleted:
            // 如果文章被删除，返回上一页
            logger.i("[ArticleDetail] 文章已被删除，返回列表");
            Get.back();
            break;
          case ArticleEventType.created:
          case ArticleEventType.none:
            // 不需要处理
            break;
        }
      }
    });
  }

  void _loadArticle() {
    final argument = Get.arguments;

    if (argument is ArticleModel) {
      // 从数据库重新获取最新状态
      articleModel = ArticleRepository.i.findModel(argument.id) ?? argument;
    } else if (argument is int) {
      // 通过ID查找文章
      final articleRef = ArticleRepository.i.findModel(argument);
      if (articleRef == null) {
        throw ArgumentError('Article not found with ID: $argument');
      }
      articleModel = articleRef;
    } else {
      throw ArgumentError('Invalid argument type: ${argument.runtimeType}');
    }

    article.value = articleModel;
    _articleStateService.setActiveArticle(articleModel);
  }

  /// 加载并格式化文章标签
  void loadTags() {
    tags.value = articleModel.tags.map((tag) => tag.name).join(', ');
  }

  /// 删除当前文章
  Future<void> deleteArticle() async {
    final articleId = articleModel.id;
    await ArticleRepository.i.deleteArticle(articleId);
    // 通知文章删除
    _articleStateService.notifyArticleDeleted(articleId);
    // 清除活跃文章状态
    _articleStateService.clearActiveArticle();
  }

  /// 生成文章的Markdown内容
  Future<void> generateMarkdownContent() async {
    // 检查HTML内容是否存在
    if (articleModel.htmlContent == null || articleModel.htmlContent!.isEmpty) {
      logger.i("无法生成Markdown：HTML内容为空");
      return;
    }

    // 检查是否已经生成过Markdown内容
    if (articleModel.aiMarkdownContent != null && articleModel.aiMarkdownContent!.isNotEmpty) {
      logger.i("Markdown内容已存在，跳过生成");
      return;
    }

    await safeExecute(
      () async {
        logger.i("开始生成Markdown内容");

        // 使用AI服务将HTML转换为Markdown
        final markdown = await AiService.i.convertHtmlToMarkdown(
          articleModel.htmlContent!,
          title: articleModel.title ?? articleModel.aiTitle,
          updatedAt: articleModel.updatedAt,
        );

        if (markdown.isEmpty) {
          throw Exception("Markdown内容生成失败");
        }

        // 保存Markdown内容到文章模型
        articleModel.aiMarkdownContent = markdown;
        ArticleRepository.i.updateModel(articleModel);
        // 刷新本地与列表视图
        article.refresh();
        // 通知全局状态服务文章已更新
        _articleStateService.notifyArticleUpdated(articleModel);

        logger.i("Markdown内容生成并保存成功");
        return markdown;
      },
      loadingMessage: "正在生成Markdown内容...",
      errorMessage: "Markdown内容生成失败",
      onSuccess: (_) => showSuccess('保存成功'),
    );
  }

  /// 获取文章内容图片列表(不含主图)
  List<String> getArticleImages() {
    final images = _getValidImagePaths(articleModel.images);
    return images.length > 1 ? images.sublist(1) : [];
  }

  /// 获取文章截图列表（已废弃：screenshots 字段已被移除）
  @Deprecated('screenshots 字段已被移除，此方法将返回空列表')
  List<String> getArticleScreenshots() {
    return [];
  }

  /// 获取有效的图片路径列表
  List<String> _getValidImagePaths(List<dynamic> items) {
    return items
        .where((item) => item.path != null && item.path!.isNotEmpty)
        .map((item) => item.path! as String)
        .toList();
  }

  /// 删除文章图片
  Future<void> deleteImage(String imagePath) async {
    articleModel.images.removeWhere((image) => image.path == imagePath);
    ArticleRepository.i.updateModel(articleModel);
    article.refresh();
    // 通知全局状态服务文章已更新
    _articleStateService.notifyArticleUpdated(articleModel);
  }
}
