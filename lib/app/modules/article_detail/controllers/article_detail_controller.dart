import 'package:share_plus/share_plus.dart';
import 'package:daily_satori/app_exports.dart';
import 'package:daily_satori/app/utils/string_extensions.dart';
import 'package:daily_satori/app/modules/articles/controllers/articles_controller.dart';

class ArticleDetailController extends BaseGetXController {
  /// 当前文章模型
  late ArticleModel articleModel;

  /// 响应式文章引用（用于与列表共享引用时触发视图刷新）
  final Rxn<ArticleModel> article = Rxn<ArticleModel>();

  /// 文章标签字符串,以逗号分隔
  final tags = ''.obs;

  /// 状态服务
  late final ArticleStateService _articleStateService;

  @override
  void onInit() {
    super.onInit();
    _articleStateService = Get.find<ArticleStateService>();
    _loadArticle();
    _initStateServices();
    loadTags();
  }

  void _initStateServices() {
    // 只监听活跃文章变化 - 这将捕获所有状态更新
    // notifyArticleUpdated 会同时更新 activeArticle，所以不需要再监听 articleUpdates
    ever(_articleStateService.activeArticle, (activeArticle) {
      if (activeArticle != null && activeArticle.id == articleModel.id) {
        logger.d("[ArticleDetail] 检测到活跃文章更新: ${activeArticle.id}, 状态: ${activeArticle.status}");
        articleModel = activeArticle;
        article.value = activeArticle;
        loadTags();
      }
    });
  }

  void _loadArticle() {
    final argument = Get.arguments;

    if (argument is ArticleModel) {
      // 从数据库重新获取最新状态
      articleModel = ArticleRepository.find(argument.id) ?? argument;
    } else if (argument is int) {
      // 通过ID查找文章，优先从列表控制器获取引用
      final articleRef = Get.isRegistered<ArticlesController>()
          ? Get.find<ArticlesController>().getRef(argument)
          : ArticleRepository.find(argument);

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
    tags.value = articleModel.tags.map((tag) => "#${tag.name}").join(', ');
  }

  /// 删除当前文章
  Future<void> deleteArticle() async {
    await ArticleRepository.deleteArticle(articleModel.id);
    // 清除活跃文章状态
    _articleStateService.clearActiveArticle();
  }

  /// 生成文章的Markdown内容
  Future<void> generateMarkdownContent() async {
    // 检查HTML内容是否存在
    if (articleModel.htmlContent.isNullOrEmpty) {
      logger.i("无法生成Markdown：HTML内容为空");
      return;
    }

    // 检查是否已经生成过Markdown内容
    if (articleModel.aiMarkdownContent.isNotNullOrEmpty) {
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
        await ArticleRepository.update(articleModel);
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

  /// 获取文章截图列表
  List<String> getArticleScreenshots() {
    return _getValidImagePaths(articleModel.screenshots);
  }

  /// 获取有效的图片路径列表
  List<String> _getValidImagePaths(List<dynamic> items) {
    return items
        .where((item) => item.path != null && item.path!.isNotEmpty)
        .map((item) => item.path! as String)
        .toList();
  }

  /// 分享文章截图
  Future<void> shareScreenshots() async {
    final screenshots = getArticleScreenshots();
    if (screenshots.isEmpty) {
      UIUtils.showSuccess("没有网页截图可以分享");
      return;
    }

    try {
      await SharePlus.instance.share(ShareParams(files: screenshots.map((path) => XFile(path)).toList(), text: '网页截图'));
      logger.i("分享网页截图完成");
    } catch (e) {
      logger.e("分享失败: $e");
    }
  }

  /// 删除文章图片
  Future<void> deleteImage(String imagePath) async {
    articleModel.images.removeWhere((image) => image.path == imagePath);
    await ArticleRepository.update(articleModel);
    article.refresh();
    // 通知全局状态服务文章已更新
    _articleStateService.notifyArticleUpdated(articleModel);
  }
}
