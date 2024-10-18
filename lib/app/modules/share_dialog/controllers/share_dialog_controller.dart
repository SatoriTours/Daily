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

    bool isArticleExists = await ArticleService.instance.articleExists(url);

    if (isUpdate == false && isArticleExists == true) {
      logger.i("网页已存在 $url, 无法保存");
      Get.close();
      Get.snackbar('提示', '网页已存在 $url, 无法保存', snackPosition: SnackPosition.top, backgroundColor: Colors.green);
      return;
    }

    if (isUpdate == true && isArticleExists == false) {
      logger.i("网页不存在 $url, 无法更新");
      Get.close();
      Get.snackbar('提示', '网页不存在 $url, 无法更新', snackPosition: SnackPosition.top, backgroundColor: Colors.green);
      return;
    }

    Future<String> aiTitleTask() async {
      var aiTitle = await AiService.instance.translate(title.trim());
      if (aiTitle.length >= 50) {
        aiTitle = await AiService.instance.summarizeOneLine(aiTitle);
      }
      return aiTitle;
    }

    Future<String> aiContentTask() async {
      return await AiService.instance.summarize(textContent.trim());
    }

    Future<String> imageDownTask() async {
      return await HttpService.instance.downloadImage(imageUrl);
    }

    Future<String> screenshotTask() async {
      return await webViewController!.captureFulScreenshot();
    }

    List<String> results = await Future.wait([
      aiTitleTask(),
      aiContentTask(),
      imageDownTask(),
      screenshotTask(),
    ]);

    String aiTitle = results[0];
    String aiContent = results[1];
    String imagePath = results[2];
    String screenshotPath = results[3];
    if (imagePath == "") {
      // 如果网页没有图片, 就用截图代替
      imagePath = screenshotPath;
    }

    var article = {
      'title': title,
      'ai_title': aiTitle,
      'content': textContent,
      'ai_content': aiContent,
      'html_content': htmlContent,
      'url': url,
      'image_url': imageUrl,
      'image_path': imagePath,
      'screenshot_path': screenshotPath,
      'pub_date': DateTime.tryParse(publishedTime)?.toUtc().toIso8601String() ?? nowToString(),
      'comment': commentController.text,
    };

    if (isUpdate) {
      logger.i("[更新文章] aiTitle => $aiTitle, imagePath => $imagePath, screenshotPath => $screenshotPath");
      await ArticleService.instance.updateArticle(url, article);
      ArticleDetailController articleDetailController = Get.find<ArticleDetailController>();
      articleDetailController.refreshArticle();
    } else {
      logger.i("[新增文章] aiTitle => $aiTitle, imagePath => $imagePath, screenshotPath => $screenshotPath");
      await ArticleService.instance.saveArticle(article);
    }

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
        padding: EdgeInsets.only(left: 10), // 离左边10px
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start, // 文字左对齐
          children: [_buildStepText("正在用AI对网页进行分析...")],
        ),
      ),
      confirm: TextButton(
        onPressed: () {
          logger.i("关闭对话框");
          Get.close(); // 关闭对话框
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
