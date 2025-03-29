import 'package:share_plus/share_plus.dart';
import 'package:daily_satori/app_exports.dart';
import 'package:daily_satori/app/utils/string_extensions.dart';

class ArticleDetailController extends BaseController {
  /// 当前文章模型
  late ArticleModel articleModel;

  /// 文章标签字符串,以逗号分隔
  final tags = ''.obs;

  @override
  void onInit() {
    super.onInit();
    // 获取传入的参数
    final argument = Get.arguments;
    if (argument is ArticleModel) {
      articleModel = argument;
    } else if (argument is int) {
      // 如果参数是ID，则根据ID查找文章
      final foundArticleModel = ArticleRepository.find(argument);
      if (foundArticleModel != null) {
        articleModel = foundArticleModel;
      } else {
        throw ArgumentError('Article not found with ID: $argument');
      }
    } else {
      throw ArgumentError('Invalid argument type: ${argument.runtimeType}');
    }

    loadTags();
  }

  /// 加载并格式化文章标签
  Future<void> loadTags() async {
    tags.value = articleModel.tags.map((tag) => "#${tag.name}").join(', ');
  }

  /// 删除当前文章
  Future<void> deleteArticle() async {
    await ArticleRepository.deleteArticle(articleModel.id);
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

    logger.i("开始生成Markdown内容");

    // 使用AI服务将HTML转换为Markdown
    final markdown = await AiService.i.convertHtmlToMarkdown(
      articleModel.htmlContent!,
      title: articleModel.title ?? articleModel.aiTitle,
      updatedAt: articleModel.updatedAt,
    );

    if (markdown.isEmpty) {
      logger.e("Markdown内容生成失败");
      return;
    }

    // 保存Markdown内容到文章模型
    articleModel.aiMarkdownContent = markdown;
    await ArticleRepository.update(articleModel);

    logger.i("Markdown内容生成并保存成功");
  }

  /// 获取文章内容图片列表(不含主图)
  List<String> getArticleImages() {
    final images = _getValidImagePaths(articleModel.images);
    if (images.isNotEmpty) {
      images.removeAt(0); // 移除主图
    }
    return images;
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
      successNotice("没有网页截图可以分享");
      return;
    }

    final files = screenshots.map((path) => XFile(path)).toList();
    final result = await Share.shareXFiles(files, text: '网页截图');

    if (result.status == ShareResultStatus.success) {
      logger.i("分享网页截图成功");
    }
  }

  /// 删除文章图片
  Future<void> deleteImage(String imagePath) async {
    articleModel.images.removeWhere((image) => image.path == imagePath);
    await ArticleRepository.update(articleModel);
  }
}
