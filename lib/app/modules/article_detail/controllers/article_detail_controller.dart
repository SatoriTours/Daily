import 'package:get/get.dart';
import 'package:share_plus/share_plus.dart';

import 'package:daily_satori/app/helpers/my_base_controller.dart';
import 'package:daily_satori/app/objectbox/article.dart';
import 'package:daily_satori/app/objectbox/tag.dart';
import 'package:daily_satori/app/services/article_service.dart';
import 'package:daily_satori/app/services/logger_service.dart';
import 'package:daily_satori/app/services/objectbox_service.dart';
import 'package:daily_satori/global.dart';

class ArticleDetailController extends MyBaseController {
  late Article article;
  final tags = ''.obs;

  final articleBox = ObjectboxService.i.box<Article>();
  final tagBox = ObjectboxService.i.box<Tag>();

  @override
  void onInit() {
    super.onInit();
    article = Get.arguments;
    loadTags();
  }

  Future<void> loadTags() async {
    final tagList = article.tags.map((tag) => "#${tag.name}").toList();
    tags.value = tagList.join(', ');
  }

  Future<void> deleteArticle() async {
    await ArticleService.i.deleteArticle(article.id);
  }

  List<String> getArticleImages() {
    final images = article.images
        .where((image) => image.path != null && image.path!.isNotEmpty)
        .map((image) => image.path!)
        .toList();
    if (images.isNotEmpty) {
      images.removeAt(0); // 移除第一张图片,因为它是主图
    }
    return images;
  }

  List<String> getArticleScreenshots() {
    final screenshots = article.screenshots
        .where((screenshot) =>
            screenshot.path != null && screenshot.path!.isNotEmpty)
        .map((screenshot) => screenshot.path!)
        .toList();
    return screenshots;
  }

  Future<void> shareScreenshots() async {
    List<XFile> files;
    final screenshots = getArticleScreenshots();

    if (screenshots.isEmpty) {
      successNotice("没有网页截图可以分享");
      return;
    }

    files = screenshots.map((path) => XFile(path)).toList();
    final result = await Share.shareXFiles(files, text: '网页截图');

    if (result.status == ShareResultStatus.success) {
      logger.i("分享网页截图成功");
    }
  }
}
