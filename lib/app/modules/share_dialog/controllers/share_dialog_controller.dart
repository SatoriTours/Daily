import 'package:daily_satori/app/services/ai_service.dart';
import 'package:daily_satori/app/services/article_service.dart';
import 'package:daily_satori/app/services/http_service.dart';
import 'package:daily_satori/global.dart';
import 'package:flutter/material.dart';
import 'package:flutter_inappwebview/flutter_inappwebview.dart';
import 'package:get/get.dart';

class ShareDialogController extends GetxController {
  String? shareURL = isProduction ? null : 'https://www.163.com/dy/article/JE339SGF051492T3.html?clickfrom=w_lb_1_big';

  InAppWebViewController? webViewController;
  TextEditingController commentController = TextEditingController();
  final webLoadProgress = 0.0.obs;
  final saveContentStep = 0.obs;

  Future<void> saveArticleInfo(String url, String title, String excerpt, String htmlContent, String textContent,
      String publishedTime, String imageUrl) async {
    logger.i("title => $title, imageUrl => $imageUrl, publishedTime => $publishedTime");

    final aiTitle = await AiService.instance.translate(title.trim());
    final aiContent = await AiService.instance.summarize(textContent.trim());

    saveContentStep.value = 2;
    final imagePath = await HttpService.instance.downloadImage(imageUrl);

    await ArticleService.instance.saveArticle({
      'title': title,
      'ai_title': aiTitle,
      'content': textContent,
      'ai_content': aiContent,
      'html_content': htmlContent,
      'url': url,
      'image_url': imageUrl,
      'image_path': imagePath,
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
    saveContentStep.value = 1;
  }

  void showProcessDialog() {
    Get.defaultDialog(
      title: "操作提示",
      content: Container(
        padding: EdgeInsets.only(left: 10), // 离左边10px
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start, // 文字左对齐
          children: [
            Obx(() => Text(
                  saveContentStep.value < 1 ? "1. 获取网页内容..." : "1. 网页内容获取完成",
                  style: TextStyle(
                      fontWeight: saveContentStep.value >= 0 ? FontWeight.bold : FontWeight.normal,
                      color: saveContentStep.value >= 0 ? Colors.blue : Colors.grey), // 根据 saveContentStep 动态改变颜色
                )),
            Obx(() => Text(
                  saveContentStep.value < 2 ? "2. AI分析网页..." : "2. AI分析完成",
                  style: TextStyle(
                      fontWeight: saveContentStep.value >= 1 ? FontWeight.bold : FontWeight.normal,
                      color: saveContentStep.value >= 1 ? Colors.blue : Colors.grey), // 根据 saveContentStep 动态改变颜色
                )),
            Obx(() => Text(
                  saveContentStep.value < 3 ? "3. 保存到app..." : "3. 数据保存成功",
                  style: TextStyle(
                      fontWeight: saveContentStep.value >= 2 ? FontWeight.bold : FontWeight.normal,
                      color: saveContentStep.value >= 2 ? Colors.blue : Colors.grey), // 根据 saveContentStep 动态改变颜色
                )),
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
}
