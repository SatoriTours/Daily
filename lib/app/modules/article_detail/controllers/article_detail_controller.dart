import 'package:get/get.dart';
import 'package:share_plus/share_plus.dart';

import 'package:daily_satori/app/services/article_service.dart';
import 'package:daily_satori/app/services/logger_service.dart';
import 'package:daily_satori/app/models/article_model.dart';
import 'package:daily_satori/global.dart';

class ArticleDetailController extends BaseController {
  /// 当前文章模型
  late ArticleModel articleModel;

  /// 文章标签字符串,以逗号分隔
  final tags = ''.obs;

  @override
  void onInit() {
    super.onInit();
    // 获取传入的ArticleModel
    final argument = Get.arguments;
    if (argument is ArticleModel) {
      articleModel = argument;
    } else {
      throw ArgumentError('Invalid argument type: ${argument.runtimeType}');
    }

    loadTags();
  }

  /// 加载并格式化文章标签
  Future<void> loadTags() async {
    tags.value = articleModel.entity!.tags.map((tag) => "#${tag.name}").join(', ');
  }

  /// 删除当前文章
  Future<void> deleteArticle() async {
    await ArticleService.i.deleteArticle(articleModel.id);
  }

  /// 获取文章内容图片列表(不含主图)
  List<String> getArticleImages() {
    final images = _getValidImagePaths(articleModel.entity!.images);
    if (images.isNotEmpty) {
      images.removeAt(0); // 移除主图
    }
    return images;
  }

  /// 获取文章截图列表
  List<String> getArticleScreenshots() {
    return _getValidImagePaths(articleModel.entity!.screenshots);
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
    articleModel.entity!.images.removeWhere((image) => image.path == imagePath);
    await ArticleService.i.updateArticle(articleModel.id, articleModel.entity!);
  }

  /// 获取文章主图路径
  String getHeaderImagePath() {
    return articleModel.headerImagePath;
  }

  /// 检查是否应该显示头部图片
  bool shouldShowHeaderImage() {
    return articleModel.shouldShowHeaderImage;
  }
}
