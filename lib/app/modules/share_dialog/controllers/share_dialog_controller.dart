import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:get/get.dart';

import 'package:daily_satori/app/components/dream_webview/dream_webview_controller.dart';
import 'package:daily_satori/app/modules/articles/controllers/articles_controller.dart';
import 'package:daily_satori/app/repositories/article_repository.dart';
import 'package:daily_satori/app/repositories/image_repository.dart';
import 'package:daily_satori/app/repositories/screenshot_repository.dart';
import 'package:daily_satori/app/routes/app_pages.dart';
import 'package:daily_satori/app/services/ai_service/ai_service.dart';
import 'package:daily_satori/app/services/http_service.dart';
import 'package:daily_satori/app/services/logger_service.dart';
import 'package:daily_satori/app/services/tags_service.dart';
import 'package:daily_satori/global.dart';

/// 分享对话框控制器
/// 管理网页内容的加载、解析和保存
class ShareDialogController extends GetxController {
  static const platform = MethodChannel('android/back/desktop');

  // 状态变量
  final RxString shareURL = ''.obs;
  final RxBool isUpdate = false.obs;
  final RxBool needBackToApp = false.obs;
  final RxInt articleID = 0.obs;
  final RxDouble webLoadProgress = 0.0.obs;
  final RxBool isLoading = false.obs;
  final RxString errorMessage = ''.obs;

  // 控制器
  DreamWebViewController? webViewController;
  final TextEditingController commentController = TextEditingController();

  @override
  void onInit() {
    super.onInit();
    _initDefaultValues();
  }

  @override
  void onClose() {
    commentController.dispose();
    super.onClose();
  }

  /// 初始化默认值
  void _initDefaultValues() {
    if (!isProduction) {
      shareURL.value = 'https://x.com/435hz/status/1868127842279842221';
    }
  }

  /// 显示加载状态
  void showLoading() {
    isLoading.value = true;
  }

  /// 隐藏加载状态
  void hideLoading() {
    isLoading.value = false;
  }

  /// 设置错误信息
  void setError(String message) {
    errorMessage.value = message;
  }

  /// 清除错误信息
  void clearError() {
    errorMessage.value = '';
  }

  /// 更新URL
  void updateShareURL(String? url) {
    if (url != null && url.isNotEmpty) {
      shareURL.value = url;
      logger.i("接收到分享的链接 $url");
    }
  }

  /// 更新是否为更新模式
  void updateIsUpdate(bool? value) {
    if (value != null) {
      isUpdate.value = value;
      logger.i("收到更新参数 $value");
    }
  }

  /// 更新文章ID
  void updateArticleID(int? id) {
    if (id != null) {
      articleID.value = id;
      logger.i("收到文章ID参数 $id");
    }
  }

  /// 更新是否需要返回应用
  void updateNeedBackToApp(bool? value) {
    if (value != null) {
      needBackToApp.value = value;
      logger.i("收到返回应用参数 $value");
    }
  }

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
      showLoading();

      // 并行处理AI任务、图片下载和截图
      List<dynamic> results = await Future.wait([
        _processAiTitle(title),
        _processAiContent(textContent),
        _processImages(imageUrls),
        _captureScreenshots(),
      ]);

      // 创建并保存文章
      final articleModel = await _saveArticleData(
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
      Get.find<ArticlesController>().updateArticle(articleModel.id);
      _completeProcess();
    } catch (e) {
      logger.e("保存文章失败: $e");
      _showMessage("保存失败: $e");
    } finally {
      hideLoading();
    }
  }

  /// 解析网页内容
  Future<void> parseWebContent() async {
    if (webViewController == null) {
      _showMessage("WebView 未初始化");
      return;
    }

    try {
      showLoading();

      await webViewController?.injectJavascriptFileFromAsset(assetFilePath: "assets/js/Readability.js");
      await webViewController?.injectJavascriptFileFromAsset(assetFilePath: "assets/js/parse_content.js");
      await webViewController?.evaluateJavascript(source: "parseContent()");
    } catch (e) {
      logger.e("解析网页内容失败: $e");
      _showMessage("解析失败，请重试");
    } finally {
      hideLoading();
    }
  }

  /// 显示处理对话框
  void showProcessDialog() {
    Get.defaultDialog(
      title: "操作提示",
      content: Container(
        padding: const EdgeInsets.only(left: 10),
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [_buildStepText("正在用AI对网页进行分析...")]),
      ),
      confirm: TextButton(
        onPressed: () {
          logger.i("关闭对话框");
          Get.back();
        },
        child: const Text("确定"),
      ),
    );
  }

  /// 点击取消按钮
  void clickChannelBtn() {
    if (!needBackToApp.value) {
      Get.back();
    } else {
      _backToPreviousApp();
    }
  }

  /// WebView创建完成
  void onWebViewCreated(DreamWebViewController controller) {
    webViewController = controller;

    // 添加内容解析处理器
    controller.addJavaScriptHandler(
      handlerName: "getPageContent",
      callback: (args) {
        List<String> images = List.from(args[6]);

        saveArticleInfo(
          args[0].toString().trim(), // url
          args[1].toString().trim(), // title
          args[2].toString().trim(), // excerpt
          args[3].toString().trim(), // htmlContent
          args[4].toString().trim(), // textContent
          args[5].toString().trim(), // publishedTime
          images, // imagesUrl
        );
      },
    );
  }

  /// 保存按钮点击
  void onSaveButtonPressed() {
    if (webViewController == null) {
      _showMessage("WebView 未初始化");
      return;
    }

    if (!isProduction) {
      webViewController?.evaluateJavascript(source: "testNode()");
    }

    showProcessDialog();
    parseWebContent();
  }

  /// 更新网页加载进度
  void updateWebLoadProgress(double progress) {
    webLoadProgress.value = progress;
  }

  /// 检查文章是否已存在
  Future<bool> _checkArticleExists(String url) async {
    if (!isUpdate.value) {
      if (await ArticleRepository.isArticleExists(url)) {
        _showMessage('网页已存在 $url');
        return true;
      }
    }
    if (isUpdate.value && articleID.value <= 0) {
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
  Future<ArticleModel> _saveArticleData({
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
    // 创建文章模型
    final articleModel = _createArticleModel(
      url: url,
      title: title,
      aiTitle: aiTitle,
      textContent: textContent,
      aiContent: aiContent,
      htmlContent: htmlContent,
      publishedTime: publishedTime,
    );

    // 保存文章模型
    await articleModel.save();

    // 保存关联数据
    await Future.wait([
      _saveTags(articleModel, tags),
      _saveImages(articleModel, images),
      _saveScreenshots(articleModel, screenshots),
    ]);

    return articleModel;
  }

  /// 创建文章模型
  ArticleModel _createArticleModel({
    required String url,
    required String title,
    required String aiTitle,
    required String textContent,
    required String aiContent,
    required String htmlContent,
    required String publishedTime,
  }) {
    // 使用ArticleRepository创建新的文章模型
    final article = ArticleRepository.find(articleID.value);

    // 创建新文章或更新现有文章
    if (article != null && isUpdate.value) {
      // 更新现有文章的属性
      article.title = title;
      article.aiTitle = aiTitle;
      article.content = textContent;
      article.aiContent = aiContent;
      article.htmlContent = htmlContent;
      article.url = url;
      article.updatedAt = DateTime.now().toUtc();
      article.comment = commentController.text;
      return article;
    } else {
      // 创建一个新的ArticleModel实例
      // 首先尝试通过URL查找文章，确保不存在重复
      final existingArticleByUrl = ArticleRepository.where(keyword: url).firstOrNull;
      if (existingArticleByUrl != null) {
        return existingArticleByUrl;
      }

      // 使用ArticleRepository创建新文章
      // 从仓储层中抽取出一个新的方法，用于创建文章并返回ArticleModel
      final data = {
        'title': title,
        'aiTitle': aiTitle,
        'content': textContent,
        'aiContent': aiContent,
        'htmlContent': htmlContent,
        'url': url,
        'pubDate': DateTime.tryParse(publishedTime)?.toUtc() ?? DateTime.now().toUtc(),
        'createdAt': DateTime.now().toUtc(),
        'updatedAt': DateTime.now().toUtc(),
        'comment': commentController.text,
      };

      // 创建文章模型
      final articleModel = ArticleRepository.createArticleModel(data);

      if (isUpdate.value && articleID.value > 0) {
        // 如果是更新模式，使用指定的ID
        ArticleRepository.updateArticleId(articleModel, articleID.value);
      }

      // 保存到数据库
      ArticleRepository.create(articleModel);

      return articleModel;
    }
  }

  /// 保存标签
  Future<void> _saveTags(ArticleModel articleModel, List<String> tagNames) async {
    try {
      logger.i("[ShareDialogController] 开始保存标签: $tagNames");

      // 清除原有标签 - 使用仓储层操作
      articleModel.tags.clear();

      // 添加新标签
      for (var tagName in tagNames) {
        // 使用TagsService添加标签
        await TagsService.i.addTagToArticle(articleModel, tagName);
      }

      // 保存更新
      await ArticleRepository.update(articleModel);

      // 重新加载标签服务
      TagsService.i.reload();

      logger.i("[ShareDialogController] 标签保存完成 ${articleModel.id}");
    } catch (e) {
      logger.e("[保存标签] 失败: $e");
    }
  }

  /// 保存图片
  Future<void> _saveImages(ArticleModel articleModel, List<ImageDownloadResult> results) async {
    try {
      logger.i("[ShareDialogController] 开始保存图片: ${results.length}");

      // 清除原有图片 - 使用仓储层操作
      articleModel.images.clear();

      // 添加新图片
      for (var result in results) {
        // 使用ImageRepository创建新图片
        final imageData = {'url': result.imageUrl, 'path': result.imagePath, 'articleId': articleModel.id};

        // 创建图片模型并保存
        final imageModel = ImageRepository.createWithData(imageData, articleModel);
        await ImageRepository.create(imageModel);
      }

      // 保存文章以更新关联
      await ArticleRepository.update(articleModel);

      logger.i("网页相关图片保存完成 ${articleModel.id}");
    } catch (e) {
      logger.e("[保存图片] 失败: $e");
    }
  }

  /// 保存截图
  Future<void> _saveScreenshots(ArticleModel articleModel, List<String> screenshotPaths) async {
    try {
      logger.i("[ShareDialogController] 开始保存截图: ${screenshotPaths.length}");

      // 清除原有截图 - 使用仓储层操作
      articleModel.screenshots.clear();

      // 添加新截图
      for (var path in screenshotPaths) {
        // 使用ScreenshotRepository创建新截图
        final screenshotData = {'path': path, 'articleId': articleModel.id};

        // 创建截图模型并保存
        final screenshotModel = ScreenshotRepository.createWithData(screenshotData, articleModel);
        await ScreenshotRepository.create(screenshotModel);
      }

      // 保存文章以更新关联
      await ArticleRepository.update(articleModel);

      logger.i("网页截图保存完成 ${articleModel.id}");
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
    Get.back(); // 关闭处理对话框

    if (!needBackToApp.value) {
      if (!isUpdate.value) {
        await Get.find<ArticlesController>().reloadArticles();
      }
      Get.offAllNamed(Routes.ARTICLES);
    } else {
      _backToPreviousApp();
    }
  }

  /// 显示提示信息
  void _showMessage(String message) {
    Get.back(); // 关闭当前对话框
    successNotice(message);
  }

  /// 构建步骤文本
  Widget _buildStepText(String tips) {
    return Text(tips, style: const TextStyle(fontWeight: FontWeight.bold, color: Colors.blue));
  }
}

/// 图片下载结果类
class ImageDownloadResult {
  final String imageUrl;
  final String imagePath;

  ImageDownloadResult(this.imageUrl, this.imagePath);
}
