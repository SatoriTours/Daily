import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import 'package:drift/drift.dart' as drift;
import 'package:get/get.dart';

import 'package:daily_satori/app/compontents/dream_webview/dream_webview_controller.dart';
import 'package:daily_satori/app/databases/database.dart';
import 'package:daily_satori/app/modules/article_detail/controllers/article_detail_controller.dart';
import 'package:daily_satori/app/services/ai_service.dart';
import 'package:daily_satori/app/services/article_service.dart';
import 'package:daily_satori/app/services/http_service.dart';
import 'package:daily_satori/global.dart';

class ShareDialogController extends GetxController {
  String? shareURL = isProduction ? null : 'https://m.hupu.com/bbs/628444253';
  bool isUpdate = false;

  DreamWebViewController? webViewController;
  TextEditingController commentController = TextEditingController();
  final webLoadProgress = 0.0.obs;

  Future<void> saveArticleInfo(String url, String title, String excerpt, String htmlContent, String textContent,
      String publishedTime, String imageUrl) async {
    logger.i("[saveArticleInfo] title => $title, imageUrl => $imageUrl, publishedTime => $publishedTime");

    if (await _checkArticleExists(url)) return;

    List<String> results = await Future.wait([
      _aiTitleTask(title),
      _aiContentTask(textContent),
      _imageDownTask(imageUrl),
      _screenshotTask(),
    ]);

    var article = _createArticleMap(
      url: url,
      title: title,
      aiTitle: results[0],
      textContent: textContent,
      aiContent: results[1],
      htmlContent: htmlContent,
      imageUrl: imageUrl,
      imagePath: results[2],
      screenshotPath: results[3],
      publishedTime: publishedTime,
    );

    await _saveOrUpdateArticle(url, article);
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

  Future<String> _screenshotTask() async {
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
      required String screenshotPath,
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
      screenshotPath: drift.Value(screenshotPath),
      pubDate: drift.Value(DateTime.tryParse(publishedTime)?.toUtc() ?? DateTime.now().toUtc()),
      comment: drift.Value(commentController.text),
    );
  }

  Future<void> _saveOrUpdateArticle(String url, ArticlesCompanion article) async {
    if (isUpdate) {
      logger.i("[更新文章] aiTitle => ${article.aiTitle}, imagePath => ${article.imagePath}");
      await ArticleService.i.updateArticle(article);
      Get.find<ArticleDetailController>().refreshArticle();
    } else {
      logger.i("[新增文章] aiTitle => ${article.aiTitle}, imagePath => ${article.imagePath}");
      await ArticleService.i.saveArticle(article);
    }
  }

  void _showSnackbar(String message) {
    Get.close();
    Get.snackbar('提示', message, snackPosition: SnackPosition.top, backgroundColor: Colors.green);
  }

  void _closeDialog() {
    Get.close();
    if (isUpdate) {
      Get.back();
    }
    if (isProduction) {
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
