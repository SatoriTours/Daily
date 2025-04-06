import 'dart:async';
import 'package:daily_satori/app/repositories/article_repository.dart';
import 'package:flutter/services.dart';
import 'package:flutter/material.dart';
import 'package:get/get.dart';

import 'package:daily_satori/app/routes/app_pages.dart';
import 'package:daily_satori/app/modules/articles/controllers/articles_controller.dart';
import 'package:daily_satori/app/services/logger_service.dart';
import 'package:daily_satori/app/services/webpage_parser_service.dart';

/// 分享对话框控制器
/// 管理网页内容的保存和更新
class ShareDialogController extends GetxController {
  static const platform = MethodChannel('android/back/desktop');

  // 状态变量
  final RxString shareURL = ''.obs;
  final RxBool isUpdate = false.obs;

  final RxString errorMessage = ''.obs;
  final RxInt saveProgress = 0.obs; // 0: 未开始，1: 获取页面，2: 处理内容，3: 完成
  final RxString progressMessage = ''.obs;
  final RxBool processingComplete = false.obs;

  final RxInt articleID = 0.obs;
  final RxString articleTitle = ''.obs;

  // 控制器
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
  void _initDefaultValues() async {
    // 从路由参数中获取初始值
    final Map<String, dynamic> args = Get.arguments ?? {};

    // 初始化文章ID - 优先级最高，决定是新增还是更新模式

    if (args.containsKey('articleID') && args['articleID'] != null) {
      articleID.value = args['articleID'];
      isUpdate.value = true;
      logger.i("初始化文章ID: ${articleID.value}, 模式: 更新");

      await _loadArticleInfo();
    } else {
      isUpdate.value = false;
      logger.i("模式: 新增");
    }

    // 初始化分享URL
    if (args.containsKey('shareURL') && args['shareURL'] != null) {
      shareURL.value = args['shareURL'];
      logger.i("初始化分享链接: ${shareURL.value}");
    }
  }

  /// 加载文章信息
  Future<void> _loadArticleInfo() async {
    if (articleID.value <= 0) return;

    final article = ArticleRepository.find(articleID.value);
    if (article != null) {
      articleTitle.value = article.title ?? '未命名文章';
      if (shareURL.value.isEmpty) {
        shareURL.value = article.url ?? '';
      }
      commentController.text = article.comment ?? '';
      logger.i("加载文章信息成功: ${article.title}");
    }
  }

  /// 设置错误信息
  void setError(String message) {
    errorMessage.value = message;
  }

  /// 清除错误信息
  void clearError() {
    errorMessage.value = '';
  }

  /// 更新处理进度
  void updateProgress(int progress, String message) {
    saveProgress.value = progress;
    progressMessage.value = message;
  }

  /// 保存按钮点击
  Future<void> onSaveButtonPressed() async {
    // 检查URL是否有效
    if (shareURL.value.isEmpty) {
      setError("链接为空，无法保存");
      return;
    }

    // 先清除错误信息
    clearError();

    _showProgressDialog();

    // 直接设置为内容分析中状态
    updateProgress(1, "内容分析中");

    // 调用网页解析服务保存网页基本信息
    await WebpageParserService.i.saveWebpage(
      url: shareURL.value,
      comment: commentController.text,
      isUpdate: isUpdate.value,
      articleID: articleID.value,
    );
  }

  /// 显示进度对话框
  void _showProgressDialog() {
    // 确保之前的对话框已关闭
    if (Get.isDialogOpen ?? false) {
      Get.back();
    }

    Get.dialog(
      Dialog(shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16))),
      barrierDismissible: false,
    );
  }

  /// 获取短URL显示
  String getShortUrl(String url) {
    if (url.isEmpty) return '';

    // 尝试从URL中提取域名部分
    Uri? uri;
    try {
      uri = Uri.parse(url);
    } catch (_) {
      // 如果解析失败，使用原始URL
    }

    if (uri != null && uri.host.isNotEmpty) {
      return uri.host;
    }

    // 如果URL太长，只显示前30个字符
    if (url.length > 30) {
      return '${url.substring(0, 30)}...';
    }
    return url;
  }

  /// 点击取消按钮
  void clickChannelBtn() {
    if (!isUpdate.value) {
      _navigateToHome();
    } else {
      _backToPreviousApp();
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

  /// 导航至首页
  void _navigateToHome() {
    Get.find<ArticlesController>().reloadArticles();
    Get.offAllNamed(Routes.home);
  }
}
