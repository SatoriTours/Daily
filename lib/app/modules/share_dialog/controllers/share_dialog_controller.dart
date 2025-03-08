import 'package:daily_satori/app/components/dream_webview/dream_webview_controller.dart';
import 'package:flutter/material.dart' as material;
import 'package:flutter/services.dart';

import 'package:get/get.dart';

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

/// 分享对话框控制器
class ShareDialogController extends BaseController {
  static const platform = MethodChannel('android/back/desktop');

  // 状态变量
  String? shareURL = isProduction ? null : 'https://x.com/435hz/status/1868127842279842221';
  bool isUpdate = false;
  bool needBackToApp = false; // 是否需要返回分享过来的应用
  int articleID = 0;

  // 控制器
  DreamWebViewController? webViewController;
  material.TextEditingController commentController = material.TextEditingController();
  final webLoadProgress = 0.0.obs;

  // 数据访问对象
  final articleBox = ObjectboxService.i.box<Article>();
  final tagBox = ObjectboxService.i.box<Tag>();
  final imageBox = ObjectboxService.i.box<Image>();
  final screenshotBox = ObjectboxService.i.box<Screenshot>();

  /// 保存文章信息
  Future<void> saveArticleInfo(
    String url,
    String title,
    String excerpt,
    String htmlContent,
    String textContent,
    String publishedTime,
    List<String> imageUrls,
  ) async {
    logger.i("[saveArticleInfo] title => ${getSubstring(title)}, url => $url, imagesUrl => $imageUrls");

    if (await _checkArticleExists(url)) return;

    try {
      // 并行处理AI任务、图片下载和截图
      List<dynamic> results = await Future.wait([
        _processAiTitle(title),
        _processAiContent(textContent),
        _processImages(imageUrls),
        _captureScreenshots(),
      ]);

      // 创建并保存文章
      final article = await _saveArticleData(
        url: url,
        title: title,
        aiTitle: results[0],
        textContent: textContent,
        aiContent: results[1].$1,
        htmlContent: htmlContent,
        publishedTime: publishedTime,
        tags: results[1].$2,
        images: results[2],
        screenshots: results[3],
      );

      // 通知文章列表更新
      Get.find<ArticlesController>().updateArticle(article.id);
      _completeProcess();
    } catch (e) {
      logger.e("保存文章失败: $e");
      _showMessage("保存失败: $e");
    }
  }

  /// 解析网页内容
  Future<void> parseWebContent() async {
    try {
      await webViewController?.injectJavascriptFileFromAsset(assetFilePath: "assets/js/Readability.js");
      await webViewController?.injectJavascriptFileFromAsset(assetFilePath: "assets/js/parse_content.js");
      await webViewController?.evaluateJavascript(source: "parseContent()");
    } catch (e) {
      logger.e("解析网页内容失败: $e");
      _showMessage("解析失败，请重试");
    }
  }

  /// 显示处理对话框
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

  /// 点击取消按钮
  void clickChannelBtn() {
    if (!needBackToApp) {
      Get.back();
    } else {
      _backToPreviousApp();
    }
  }

  // 私有方法

  /// 检查文章是否已存在
  Future<bool> _checkArticleExists(String url) async {
    if (!isUpdate) {
      if (await ArticleService.i.isArticleExists(url)) {
        _showMessage('网页已存在 $url');
        return true;
      }
    }
    if (isUpdate && articleID <= 0) {
      _showMessage('网页不存在 $url, 无法更新');
      return true;
    }
    return false;
  }

  /// 处理AI标题
  Future<String> _processAiTitle(String title) async {
    try {
      var aiTitle = await AiService.i.translate(title.trim());
      return aiTitle.length >= 50 ? await AiService.i.summarizeOneLine(aiTitle) : aiTitle;
    } catch (e) {
      logger.e("[AI标题] 失败: $e");
      return '';
    }
  }

  /// 处理AI内容
  Future<(String, List<String>)> _processAiContent(String textContent) async {
    try {
      return await AiService.i.summarize(textContent.trim());
    } catch (e) {
      logger.e("[AI内容] 失败: $e");
      return ('', const <String>[]);
    }
  }

  /// 处理图片
  Future<List<ImageDownloadResult>> _processImages(List<String> imageUrls) async {
    try {
      final imageResults = await Future.wait(
        imageUrls.map((imageUrl) async {
          return ImageDownloadResult(imageUrl, await HttpService.i.downloadImage(imageUrl));
        }),
      );
      return imageResults.where((result) => result.imagePath.isNotEmpty).toList();
    } catch (e) {
      logger.e("[下载图片] 失败: $e");
      return <ImageDownloadResult>[];
    }
  }

  /// 捕获截图
  Future<List<String>> _captureScreenshots() async {
    try {
      if (webViewController == null) return [];
      return await webViewController!.captureFulScreenshot();
    } catch (e) {
      logger.e("[截图] 失败: $e");
      return <String>[];
    }
  }

  /// 保存文章数据
  Future<Article> _saveArticleData({
    required String url,
    required String title,
    required String aiTitle,
    required String textContent,
    required String aiContent,
    required String htmlContent,
    required String publishedTime,
    required List<String> tags,
    required List<ImageDownloadResult> images,
    required List<String> screenshots,
  }) async {
    // 创建文章
    var article = _createArticle(
      url: url,
      title: title,
      aiTitle: aiTitle,
      textContent: textContent,
      aiContent: aiContent,
      htmlContent: htmlContent,
      publishedTime: publishedTime,
    );

    // 保存文章
    article = await _saveOrUpdateArticle(article);

    // 保存关联数据
    await Future.wait([_saveTags(article, tags), _saveImages(article, images), _saveScreenshots(article, screenshots)]);

    return article;
  }

  /// 创建文章
  Article _createArticle({
    required String url,
    required String title,
    required String aiTitle,
    required String textContent,
    required String aiContent,
    required String htmlContent,
    required String publishedTime,
  }) {
    return Article(
      title: title,
      aiTitle: aiTitle,
      content: textContent,
      aiContent: aiContent,
      htmlContent: htmlContent,
      url: url,
      pubDate: DateTime.tryParse(publishedTime)?.toUtc() ?? DateTime.now().toUtc(),
      createdAt: DateTime.now().toUtc(),
      updatedAt: DateTime.now().toUtc(),
      comment: commentController.text,
    );
  }

  /// 保存或更新文章
  Future<Article> _saveOrUpdateArticle(Article article) async {
    article.id = articleID;
    articleBox.put(article);
    return article;
  }

  /// 保存标签
  Future<void> _saveTags(Article article, List<String> tags) async {
    try {
      logger.i("[ShareDialogController] 开始保存标签: $tags");

      // 清除文章现有标签
      article.tags.removeWhere((tag) => true);
      articleBox.put(article);

      // 获取或创建标签
      for (var tagTitle in tags) {
        // 查找已存在的标签
        var tag = tagBox.query(Tag_.name.equals(tagTitle)).build().findFirst();

        // 如果标签不存在,创建新标签
        if (tag == null) {
          tag = Tag(name: tagTitle);
          tagBox.put(tag);
        }

        // 添加标签到文章
        article.tags.add(tag);
      }

      // 保存文章
      articleBox.put(article);

      TagsService.i.reload();
      logger.i("[ShareDialogController] 标签保存完成 ${article.id}");
    } catch (e) {
      logger.e("[保存标签] 失败: $e");
    }
  }

  /// 保存图片
  Future<void> _saveImages(Article article, List<ImageDownloadResult> results) async {
    try {
      logger.i("[ShareDialogController] 开始保存图片: ${results.length}");
      article.images.removeWhere((image) => true);
      articleBox.put(article);

      for (var result in results) {
        final image = Image(url: result.imageUrl, path: result.imagePath);
        image.article.target = article;
        imageBox.put(image);
        logger.i("保存到数据库: ${result.imageUrl} => ${result.imagePath}");
      }

      logger.i("网页相关图片保存完成 ${article.id}");
    } catch (e) {
      logger.e("[保存图片] 失败: $e");
    }
  }

  /// 保存截图
  Future<void> _saveScreenshots(Article article, List<String> screenshotPaths) async {
    try {
      logger.i("[ShareDialogController] 开始保存截图: ${screenshotPaths.length}");

      article.screenshots.removeWhere((screenshot) => true);
      articleBox.put(article);

      // 保存新的截图
      for (var imagePath in screenshotPaths) {
        final screenshot = Screenshot(path: imagePath);
        screenshot.article.target = article;
        screenshotBox.put(screenshot);
      }

      logger.i("网页截图保存完成 ${article.id}");
    } catch (e) {
      logger.e("[保存截图] 失败: $e");
    }
  }

  /// 返回到之前的应用
  Future<void> _backToPreviousApp() async {
    try {
      Get.back();
      await platform.invokeMethod('backDesktop');
    } on PlatformException catch (e) {
      logger.e("通信失败: ${e.toString()}");
      await SystemNavigator.pop();
    }
  }

  /// 完成处理流程
  Future<void> _completeProcess() async {
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

  /// 显示提示信息
  void _showMessage(String message) {
    Get.close();
    successNotice(message);
  }

  /// 构建步骤文本
  material.Widget _buildStepText(String tips) {
    return material.Text(
      tips,
      style: material.TextStyle(fontWeight: material.FontWeight.bold, color: material.Colors.blue),
    );
  }
}

/// 图片下载结果类
class ImageDownloadResult {
  final String imageUrl;
  final String imagePath;

  ImageDownloadResult(this.imageUrl, this.imagePath);
}
