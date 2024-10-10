import 'package:daily_satori/app/helper/flutter_inappwebview_screenshot.dart';
import 'package:daily_satori/app/services/ai_service.dart';
import 'package:daily_satori/app/services/article_service.dart';
import 'package:daily_satori/app/services/http_service.dart';
import 'package:daily_satori/global.dart';
import 'package:flutter/material.dart';
import 'package:flutter_inappwebview/flutter_inappwebview.dart';
import 'package:get/get.dart';

class ShareDialogController extends GetxController {
  String? shareURL = isProduction ? null : 'https://m.163.com/news/article/JE4I60T100019K82.html';

  InAppWebViewController? webViewController;
  TextEditingController commentController = TextEditingController();
  final webLoadProgress = 0.0.obs;
  final saveContentStep = 0.obs;

  Future<void> saveArticleInfo(String url, String title, String excerpt, String htmlContent, String textContent,
      String publishedTime, String imageUrl) async {
    logger.i("title => $title");
    // logger.i("title => $title, imageUrl => $imageUrl, publishedTime => $publishedTime");
    if (await ArticleService.instance.articleExists(url)) {
      Get.snackbar('提示', '网页已存在', snackPosition: SnackPosition.top, backgroundColor: Colors.green);
      Get.close();
      return;
    }
    saveContentStep.value = 1;
    final aiTitle = await AiService.instance.translate(title.trim());
    final aiContent = await AiService.instance.summarize(textContent.trim());

    saveContentStep.value = 2;
    final imagePath = await HttpService.instance.downloadImage(imageUrl);
    final screenshotPath = await captureFullPageScreenshot(webViewController!);

    await ArticleService.instance.saveArticle({
      'title': title,
      'ai_title': aiTitle,
      'content': textContent,
      'ai_content': aiContent,
      'html_content': htmlContent,
      'url': url,
      'image_url': imageUrl,
      'image_path': imagePath,
      'screenshot_path': screenshotPath,
      'pub_date': publishedTime,
      'comment': commentController.text,
    });
    saveContentStep.value = 3;
    // Get.close();
    // SystemNavigator.pop();
  }

  Future<void> parseWebContent() async {
    saveContentStep.value = 0;
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
          children: [
            Obx(() => _buildStepText(0, '1. 解析网页...', '1. 网页解析完成')),
            Obx(() => _buildStepText(1, '1. AI分析网页...', '1. AI分析完成')),
            Obx(() => _buildStepText(2, '3. 保存到app...', '3. 数据保存成功')),
          ],
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

  Widget _buildStepText(int step, String processingTips, String completedTips) {
    return Text(
      saveContentStep.value == step ? processingTips : completedTips,
      style: TextStyle(
          fontWeight: saveContentStep.value == step ? FontWeight.bold : FontWeight.normal,
          color: saveContentStep.value >= step ? Colors.blue : Colors.grey), // 根据 saveContentStep 动态改变颜色
    );
  }
}
