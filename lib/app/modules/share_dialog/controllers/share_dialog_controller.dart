import 'package:flutter/material.dart' as material;
import 'package:flutter/services.dart';

import 'package:get/get.dart';

import 'package:daily_satori/app/compontents/dream_webview/dream_webview_controller.dart';
import 'package:daily_satori/app/helpers/my_base_controller.dart';
import 'package:daily_satori/app/modules/articles/controllers/articles_controller.dart';
import 'package:daily_satori/app/objectbox/article.dart';
import 'package:daily_satori/app/objectbox/image.dart';
import 'package:daily_satori/app/objectbox/screenshot.dart';
import 'package:daily_satori/app/objectbox/tag.dart';
import 'package:daily_satori/app/routes/app_pages.dart';
import 'package:daily_satori/app/services/ai_service/ai_service.dart';
import 'package:daily_satori/app/services/article_service.dart';
import 'package:daily_satori/app/services/http_service.dart';
import 'package:daily_satori/app/services/logger_service.dart';
import 'package:daily_satori/app/services/objectbox_service.dart';
import 'package:daily_satori/app/services/tags_service.dart';
import 'package:daily_satori/global.dart';
import 'package:daily_satori/objectbox.g.dart';

part 'part.article.dart';
part 'part.tags.dart';
part 'part.images.dart';
part 'part.screenshot.dart';

class ShareDialogController extends MyBaseController {
  static const platform = MethodChannel('android/back/desktop');

  String? shareURL = isProduction ? null : 'https://x.com/435hz/status/1868127842279842221';
  bool isUpdate = false;
  bool needBackToApp = false; // 是否需要返回分享过来的应用
  int articleID = 0;

  DreamWebViewController? webViewController;
  material.TextEditingController commentController = material.TextEditingController();
  final webLoadProgress = 0.0.obs;

  final articleBox = ObjectboxService.i.box<Article>();
  final tagBox = ObjectboxService.i.box<Tag>();
  final imageBox = ObjectboxService.i.box<Image>();
  final screenshotBox = ObjectboxService.i.box<Screenshot>();

  Future<void> saveArticleInfo(String url, String title, String excerpt, String htmlContent, String textContent,
      String publishedTime, List<String> imageUrls) async {
    logger.i(
        "[saveArticleInfo] title => ${getSubstring(title)}, url => $url, imagesUrl => $imageUrls, publishedTime => $publishedTime");

    if (await _checkArticleExists(url)) return;

    List<dynamic> results = await Future.wait([
      _aiTitleTask(title).catchError((e) {
        logger.e("[AI标题] 失败: $e");
        return '';
      }),
      _aiContentTask(textContent).catchError((e) {
        logger.e("[AI内容] 失败: $e");
        return ('', const <String>[]);
      }),
      _downloadImages(imageUrls).catchError((e) {
        logger.e("[下载图片] 失败: $e");
        return <ImageDownloadResult>[];
      }),
      _screenshotTask().catchError((e) {
        logger.e("[截图] 失败: $e");
        return <String>[];
      }),
    ]);

    final String summary = results[1].$1;
    final List<String> tags = results[1].$2;

    var article = _createArticle(
      url: url,
      title: title,
      aiTitle: results[0].toString(),
      textContent: textContent,
      aiContent: summary,
      htmlContent: htmlContent,
      publishedTime: publishedTime,
    );

    final newArticle = await _saveOrUpdateArticle(article);
    await Future.wait([
      _saveTags(newArticle, tags).catchError((e) => logger.e("[保存标签] 失败: $e")),
      _saveImages(newArticle, results[2]).catchError((e) => logger.e("[保存图片] 失败: $e")),
      _saveScreenshots(newArticle, List.from(results[3])).catchError((e) => logger.e("[保存截图] 失败: $e")),
    ]);

    Get.find<ArticlesController>().updateArticleInList(newArticle.id);

    saveCompleted();
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
    successNotice(message);
  }

  Future<void> saveCompleted() async {
    Get.close();
    if (!needBackToApp) {
      if (!isUpdate) {
        await Get.find<ArticlesController>().reloadArticles();
      }
      Get.offAllNamed(Routes.ARTICLES);
    } else {
      _backToPreviousApp();
    }
  }

  void clickChannelBtn() {
    if (!needBackToApp) {
      Get.back();
    } else {
      _backToPreviousApp();
    }
  }

  Future<void> _backToPreviousApp() async {
    try {
      Get.back();
      await platform.invokeMethod('backDesktop');
    } on PlatformException catch (e) {
      logger.e("通信失败: ${e.toString()}");
      await SystemNavigator.pop();
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
      content: material.Container(
        padding: material.EdgeInsets.only(left: 10),
        child: material.Column(
          crossAxisAlignment: material.CrossAxisAlignment.start,
          children: [_buildStepText("正在用AI对网页进行分析...")],
        ),
      ),
      confirm: material.TextButton(
        onPressed: () {
          logger.i("关闭对话框");
          Get.close();
        },
        child: material.Text("确定"),
      ),
    );
  }

  material.Widget _buildStepText(String tips) {
    return material.Text(
      tips,
      style: material.TextStyle(fontWeight: material.FontWeight.bold, color: material.Colors.blue),
    );
  }
}
