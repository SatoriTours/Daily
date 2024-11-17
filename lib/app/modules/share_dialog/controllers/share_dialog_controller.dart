import 'package:daily_satori/app/helpers/my_base_controller.dart';
import 'package:daily_satori/app/modules/articles/controllers/articles_controller.dart';
import 'package:daily_satori/app/routes/app_pages.dart';
import 'package:daily_satori/app/services/tags_service.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import 'package:drift/drift.dart' as drift;
import 'package:get/get.dart';

import 'package:daily_satori/app/compontents/dream_webview/dream_webview_controller.dart';
import 'package:daily_satori/app/databases/database.dart';
import 'package:daily_satori/app/services/ai_service/ai_service.dart';
import 'package:daily_satori/app/services/article_service.dart';
import 'package:daily_satori/app/services/db_service.dart';
import 'package:daily_satori/app/services/http_service.dart';
import 'package:daily_satori/global.dart';

part 'part.article.dart';
part 'part.tags.dart';
part 'part.images.dart';
part 'part.screenshot.dart';

class ShareDialogController extends MyBaseController {
  String? shareURL = isProduction ? null : 'https://1024.day/d/3072';
  bool isUpdate = false;
  int articleID = -1;

  DreamWebViewController? webViewController;
  TextEditingController commentController = TextEditingController();
  final webLoadProgress = 0.0.obs;
  AppDatabase get db => DBService.i.db;

  Future<void> saveArticleInfo(String url, String title, String excerpt, String htmlContent, String textContent,
      String publishedTime, List<String> imageUrls) async {
    logger.i(
        "[saveArticleInfo] title => ${getSubstring(title)}, url => $url, imagesUrl => $imageUrls, publishedTime => $publishedTime");

    if (await _checkArticleExists(url)) return;

    List<dynamic> results = await Future.wait([
      _aiTitleTask(title),
      _aiContentTask(textContent),
      _imageDownTask(imageUrls.first),
      _screenshotTask(),
    ]);

    var article = _createArticleMap(
      // url: url,
      url: shareURL ?? url,
      title: title,
      aiTitle: results[0].toString(),
      textContent: textContent,
      aiContent: results[1].$1,
      htmlContent: htmlContent,
      imageUrl: imageUrls.first,
      imagePath: results[2].toString(),
      // screenshotPath: results[3],
      publishedTime: publishedTime,
    );

    final newArticle = await _saveOrUpdateArticle(url, article);

    _saveTags(newArticle, results[1].$2);
    _saveImages(newArticle, imageUrls);
    _saveScreenshots(newArticle, List.from(results[3]));

    _closeDialog();
  }

  Future<String> _aiTitleTask(String title) async {
    var aiTitle = await AiService.i.translate(title.trim());
    return aiTitle.length >= 50 ? await AiService.i.summarizeOneLine(aiTitle) : aiTitle;
  }

  Future<(String, List<String>)> _aiContentTask(String textContent) async {
    return await AiService.i.summarize(textContent.trim());
  }

  Future<List<String>> _screenshotTask() async {
    return await webViewController!.captureFulScreenshot();
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
    } else {
      Get.offAllNamed(Routes.ARTICLES); // 非生产环境, 返回文章列表页
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
