import 'package:daily_satori/app/compontents/dream_webview/dream_webview_controller.dart';
import 'package:daily_satori/app/modules/article_detail/controllers/article_detail_controller.dart';
import 'package:daily_satori/app/services/ai_service.dart';
import 'package:daily_satori/app/services/article_service.dart';
import 'package:daily_satori/app/services/http_service.dart';
import 'package:daily_satori/global.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:get/get.dart';

class ShareDialogController extends GetxController {
  String? shareURL = isProduction ? null : 'https://github.com/rails/rails';
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

    String imagePath = results[2].isEmpty ? results[3] : results[2];
    var article =
        _createArticleMap(url, title, results[0], textContent, results[1], htmlContent, imagePath, publishedTime);

    await _saveOrUpdateArticle(url, article);
    _closeDialog();
  }

  Future<bool> _checkArticleExists(String url) async {
    bool isArticleExists = await ArticleService.instance.articleExists(url);
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
    var aiTitle = await AiService.instance.translate(title.trim());
    return aiTitle.length >= 50 ? await AiService.instance.summarizeOneLine(aiTitle) : aiTitle;
  }

  Future<String> _aiContentTask(String textContent) async {
    return await AiService.instance.summarize(textContent.trim());
  }

  Future<String> _imageDownTask(String imageUrl) async {
    return await HttpService.instance.downloadImage(imageUrl);
  }

  Future<String> _screenshotTask() async {
    return await webViewController!.captureFulScreenshot();
  }

  Map<String, dynamic> _createArticleMap(String url, String title, String aiTitle, String textContent, String aiContent,
      String htmlContent, String imagePath, String publishedTime) {
    return {
      'title': title,
      'ai_title': aiTitle,
      'content': textContent,
      'ai_content': aiContent,
      'html_content': htmlContent,
      'url': url,
      'image_url': imagePath,
      'image_path': imagePath,
      'screenshot_path': imagePath,
      'pub_date': DateTime.tryParse(publishedTime)?.toUtc().toIso8601String() ?? nowToString(),
      'comment': commentController.text,
    };
  }

  Future<void> _saveOrUpdateArticle(String url, Map<String, dynamic> article) async {
    if (isUpdate) {
      logger.i("[更新文章] aiTitle => ${article['ai_title']}, imagePath => ${article['image_path']}");
      await ArticleService.instance.updateArticle(url, article);
      Get.find<ArticleDetailController>().refreshArticle();
    } else {
      logger.i("[新增文章] aiTitle => ${article['ai_title']}, imagePath => ${article['image_path']}");
      await ArticleService.instance.saveArticle(article);
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
