import 'package:daily_satori/app/modules/articles/controllers/articles_controller.dart';
import 'package:daily_satori/app/routes/app_pages.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import 'package:drift/drift.dart' as drift;
import 'package:get/get.dart';

import 'package:daily_satori/app/compontents/dream_webview/dream_webview_controller.dart';
import 'package:daily_satori/app/databases/database.dart';
import 'package:daily_satori/app/services/ai_service.dart';
import 'package:daily_satori/app/services/article_service.dart';
import 'package:daily_satori/app/services/db_service.dart';
import 'package:daily_satori/app/services/http_service.dart';
import 'package:daily_satori/global.dart';

class ShareDialogController extends GetxController {
  String? shareURL = isProduction ? null : 'https://m.hupu.com/bbs/628444253';
  bool isUpdate = false;

  DreamWebViewController? webViewController;
  TextEditingController commentController = TextEditingController();
  final webLoadProgress = 0.0.obs;
  AppDatabase get db => DBService.i.db;

  Future<void> saveArticleInfo(String url, String title, String excerpt, String htmlContent, String textContent,
      String publishedTime, List<String> imagesUrl) async {
    logger.i("[saveArticleInfo] title => $title, imagesUrl => $imagesUrl, publishedTime => $publishedTime");

    if (await _checkArticleExists(url)) return;

    List<dynamic> results = await Future.wait([
      _aiTitleTask(title),
      _aiContentTask(textContent),
      _imageDownTask(imagesUrl.first),
      _screenshotTask(),
    ]);

    var article = _createArticleMap(
      url: url,
      title: title,
      aiTitle: results[0].toString(),
      textContent: textContent,
      aiContent: results[1].toString(),
      htmlContent: htmlContent,
      imageUrl: imagesUrl.first,
      imagePath: results[2].toString(),
      // screenshotPath: results[3],
      publishedTime: publishedTime,
    );

    final newArticle = await _saveOrUpdateArticle(url, article);

    // 保存文章的图片
    if (newArticle != null && imagesUrl.length >= 2) {
      await db.articleImages.deleteWhere((tbl) => tbl.article.equals(newArticle.id));
      await Future.wait(imagesUrl.skip(1).map((imageUrl) async {
        await _downloadAndSaveImage(newArticle.id, imageUrl);
      }));
      logger.i("网页相关图片保存完成 ${newArticle.id}");
    }

    // 保存文章的截图
    List<String> screenshotPaths = List.from(results[3]);
    if (newArticle != null && screenshotPaths.isNotEmpty) {
      await db.articleScreenshoots.deleteWhere((tbl) => tbl.article.equals(newArticle.id));
      await Future.wait(screenshotPaths.map((imagePath) async {
        await db.into(db.articleScreenshoots).insert(ArticleScreenshootsCompanion(
              article: drift.Value(newArticle.id),
              imagePath: drift.Value(imagePath),
            ));
      }));
      logger.i("网页截图保存完成 ${newArticle.id}");
    }

    _closeDialog();
  }

  Future<bool> _checkArticleExists(String url) async {
    bool isArticleExists = await ArticleService.i.isArticleExists(url);
    if (!isUpdate && isArticleExists) {
      _showSnackbar('网页已存在 $url, 无法保存');
      return true;
    }
    if (isUpdate && !isArticleExists) {
      _showSnackbar('网页不存在 $url, 无法更新');
      return true;
    }
    return false;
  }

  Future<void> _downloadAndSaveImage(int articleID, String url) async {
    try {
      // 下载图片
      String imagePath = await HttpService.i.downloadImage(url);

      // 更新数据库
      await db.into(db.articleImages).insert(ArticleImagesCompanion(
            article: drift.Value(articleID),
            imageUrl: drift.Value(url),
            imagePath: drift.Value(imagePath),
          ));
      logger.i("图片下载并保存到数据库: $url => imagePath");
    } catch (e) {
      logger.e("下载或保存图片失败: $url, 错误: $e");
    }
  }

  Future<String> _aiTitleTask(String title) async {
    var aiTitle = await AiService.i.translate(title.trim());
    return aiTitle.length >= 50 ? await AiService.i.summarizeOneLine(aiTitle) : aiTitle;
  }

  Future<String> _aiContentTask(String textContent) async {
    return await AiService.i.summarize(textContent.trim());
  }

  Future<String> _imageDownTask(String imageUrl) async {
    return await HttpService.i.downloadImage(imageUrl);
  }

  Future<List<String>> _screenshotTask() async {
    return await webViewController!.captureFulScreenshot();
  }

  ArticlesCompanion _createArticleMap(
      {required String url,
      required String title,
      required String aiTitle,
      required String textContent,
      required String aiContent,
      required String htmlContent,
      required String imageUrl,
      required String imagePath,
      // required String screenshotPath,
      required String publishedTime}) {
    return ArticlesCompanion(
      title: drift.Value(title),
      aiTitle: drift.Value(aiTitle),
      content: drift.Value(textContent),
      aiContent: drift.Value(aiContent),
      htmlContent: drift.Value(htmlContent),
      url: drift.Value(url),
      imageUrl: drift.Value(imageUrl),
      imagePath: drift.Value(imagePath),
      screenshotPath: drift.Value(''), // 清空screenshotPath, 使用新的表存储的多张图
      pubDate: drift.Value(DateTime.tryParse(publishedTime)?.toUtc() ?? DateTime.now().toUtc()),
      comment: drift.Value(commentController.text),
    );
  }

  Future<Article?> _saveOrUpdateArticle(String url, ArticlesCompanion article) async {
    final Article? newArticle;
    if (isUpdate) {
      logger.i("[更新文章] aiTitle => ${article.aiTitle}, imagePath => ${article.imagePath}");
      newArticle = await ArticleService.i.updateArticle(article);
      if (newArticle != null) {
        Get.find<ArticlesController>().updateArticleFromList(newArticle);
      }
    } else {
      logger.i("[新增文章] aiTitle => ${article.aiTitle}, imagePath => ${article.imagePath}");
      newArticle = await ArticleService.i.saveArticle(article);
    }
    return newArticle;
  }

  void _showSnackbar(String message) {
    Get.close();
    Get.snackbar('提示', message, snackPosition: SnackPosition.top, backgroundColor: Colors.green);
  }

  void _closeDialog() {
    Get.close();
    if (isUpdate) {
      Get.offAllNamed(Routes.ARTICLES);
    } else if (isProduction) {
      SystemNavigator.pop();
    }
  }

  Future<void> parseWebContent() async {
    await webViewController?.injectJavascriptFileFromAsset(assetFilePath: "assets/js/Readability.js");
    await webViewController?.injectJavascriptFileFromAsset(assetFilePath: "assets/js/parse_content.js");
    await webViewController?.evaluateJavascript(source: "parseContent()");
  }

  void showProcessDialog() {
    Get.defaultDialog(
      title: "操作提示",
      content: Container(
        padding: EdgeInsets.only(left: 10),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [_buildStepText("正在用AI对网页进行分析...")],
        ),
      ),
      confirm: TextButton(
        onPressed: () {
          logger.i("关闭对话框");
          Get.close();
        },
        child: Text("确定"),
      ),
    );
  }

  Widget _buildStepText(String tips) {
    return Text(
      tips,
      style: TextStyle(fontWeight: FontWeight.bold, color: Colors.blue),
    );
  }
}
