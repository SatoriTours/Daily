import 'package:daily_satori/app/helpers/my_base_controller.dart';
import 'package:daily_satori/app/services/db_service.dart';
import 'package:daily_satori/global.dart';
import 'package:drift/drift.dart';
import 'package:get/get.dart';

import 'package:daily_satori/app/databases/database.dart';
import 'package:daily_satori/app/services/article_service.dart';
import 'package:share_plus/share_plus.dart';

class ArticleDetailController extends MyBaseController {
  AppDatabase get db => DBService.i.db;

  late Article article;
  final tags = ''.obs;

  @override
  void onInit() {
    super.onInit();
    article = Get.arguments;
    loadTags();
  }

  Future<void> loadTags() async {
    final results = db.select(db.tags).join([innerJoin(db.articleTags, db.articleTags.tagId.equalsExp(db.tags.id))])
      ..where(db.articleTags.articleId.equals(article.id));

    tags.value = (await results.get()).map((e) => "#${e.readTable(db.tags).title}").join(', ');
  }

  Future<void> deleteArticle() async {
    await ArticleService.i.deleteArticle(article.id);
  }

  Future<List<ArticleImage>> getArticleImages() async {
    final images = await (db.select(db.articleImages)..where((row) => row.article.equals(article.id))).get();
    return images;
  }

  Future<List<ArticleScreenshot>> getArticleScreenshoots() async {
    final screenshots = await (db.select(db.articleScreenshots)
          ..where((row) => row.article.equals(article.id))
          ..orderBy([(tbl) => OrderingTerm.asc(tbl.id)]))
        .get();
    return screenshots;
  }

  Future<void> shareScreenshots() async {
    List<XFile> files;
    if (article.screenshotPath != null && article.screenshotPath!.isNotEmpty) {
      files = [XFile(article.screenshotPath!)];
    } else {
      final screenshots = await getArticleScreenshoots();
      screenshots.removeWhere((s) => s.imagePath == null);
      if (screenshots.isEmpty) {
        successNotice("没有网页截图可以分享");
        return;
      }
      files = screenshots.map((s) => XFile(s.imagePath!)).toList();
    }
    final result = await Share.shareXFiles(files, text: '网页截图');
    if (result.status == ShareResultStatus.success) {
      logger.i("分享网页截图成功");
    }
  }
}
