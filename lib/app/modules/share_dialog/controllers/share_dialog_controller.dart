import 'dart:async';
import 'package:flutter/services.dart';
import 'package:flutter/material.dart';
import 'package:get/get.dart';

import 'package:daily_satori/app/routes/app_pages.dart';
import 'package:daily_satori/app/modules/articles/controllers/articles_controller.dart';
import 'package:daily_satori/app/services/logger_service.dart';
import 'package:daily_satori/app/services/webpage_parser_service.dart';
import 'package:daily_satori/global.dart';

/// 分享对话框控制器
/// 管理网页内容的保存
class ShareDialogController extends GetxController {
  static const platform = MethodChannel('android/back/desktop');

  // 状态变量
  final RxString shareURL = 'https://x.com/435hz/status/1868127842279842221'.obs;
  final RxBool isUpdate = false.obs;
  final RxBool needBackToApp = false.obs;
  final RxInt articleID = 0.obs;
  final RxBool isLoading = false.obs;
  final RxString errorMessage = ''.obs;
  final RxInt saveProgress = 0.obs; // 0: 未开始，1: 获取页面，2: 处理内容，3: 完成
  final RxString progressMessage = ''.obs;
  final RxBool processingComplete = false.obs;
  final RxInt articleId = 0.obs;

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
  void _initDefaultValues() {
    // 从路由参数中获取初始值
    final Map<String, dynamic> args = Get.arguments ?? {};

    // 初始化分享URL
    if (args.containsKey('url') && args['url'] != null) {
      shareURL.value = args['url'];
      logger.i("初始化分享链接: ${shareURL.value}");
    } else {
      shareURL.value = '';
    }

    // 初始化是否为更新模式
    if (args.containsKey('isUpdate') && args['isUpdate'] != null) {
      isUpdate.value = args['isUpdate'];
      logger.i("初始化更新模式: ${isUpdate.value}");
    }

    // 初始化文章ID
    if (args.containsKey('articleID') && args['articleID'] != null) {
      articleID.value = args['articleID'];
      logger.i("初始化文章ID: ${articleID.value}");
    }

    // 初始化是否需要返回应用
    if (args.containsKey('needBackToApp') && args['needBackToApp'] != null) {
      needBackToApp.value = args['needBackToApp'];
      logger.i("初始化返回应用标志: ${needBackToApp.value}");
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

  /// 更新处理进度
  void updateProgress(int progress, String message) {
    saveProgress.value = progress;
    progressMessage.value = message;
  }

  /// 获取文章状态
  Future<ArticleModel?> _getArticleStatus(int articleId) async {
    try {
      return WebpageParserService.i.getArticleStatus(articleId);
    } catch (e) {
      logger.e("获取文章状态失败: $e");
      return null;
    }
  }

  /// 监听文章状态变化
  void _startArticleStatusListener(int articleId) {
    // 保存文章ID以便于跟踪
    this.articleId.value = articleId;

    // 设置统一的处理中状态
    updateProgress(1, "内容分析中");

    // 创建一个定时器每秒检查一次文章状态
    Timer.periodic(const Duration(seconds: 1), (timer) async {
      if (processingComplete.value) {
        timer.cancel();
        return;
      }

      final article = await _getArticleStatus(articleId);
      if (article == null) return;

      // 只关注最终状态
      if (article.status == 'completed') {
        updateProgress(3, "处理完成");
        processingComplete.value = true;
        timer.cancel();
        // 延迟一秒后进行完成处理
        await Future.delayed(const Duration(seconds: 1));
        _navigateToHome();
      } else if (article.status == 'error') {
        updateProgress(0, "处理失败: ${article.aiContent}");
        processingComplete.value = true;
        timer.cancel();
      }
    });
  }

  /// 保存按钮点击
  Future<void> onSaveButtonPressed() async {
    // 检查URL是否有效
    if (shareURL.value.isEmpty) {
      UIUtils.showError("链接为空，无法保存");
      return;
    }

    // 先显示加载状态
    isLoading.value = true;

    try {
      // 显示进度对话框
      _showProgressDialog();

      // 直接设置为内容分析中状态
      updateProgress(1, "内容分析中");

      // 调用网页解析服务保存网页基本信息
      final article = await WebpageParserService.i.saveWebpage(
        url: shareURL.value,
        comment: commentController.text,
        isUpdate: isUpdate.value,
        articleID: articleID.value,
      );

      // 开始监听文章状态
      _startArticleStatusListener(article.id);
    } catch (e, stackTrace) {
      logger.e("保存网页失败: $e\n堆栈信息: $stackTrace");

      // 关闭进度对话框
      if (Get.isDialogOpen ?? false) {
        Get.back();
      }

      // 显示错误提示
      UIUtils.showError("保存失败: $e");
    } finally {
      // 隐藏加载状态
      isLoading.value = false;
    }
  }

  /// 显示进度对话框
  void _showProgressDialog() {
    // 确保之前的对话框已关闭
    if (Get.isDialogOpen ?? false) {
      Get.back();
    }

    Get.dialog(
      Dialog(
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
        child: ProgressDialogContent(controller: this),
      ),
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
    if (!needBackToApp.value) {
      Get.back();
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
    if (!needBackToApp.value) {
      if (!isUpdate.value) {
        Get.find<ArticlesController>().reloadArticles();
      }
      Get.offAllNamed(Routes.home);
    } else {
      _backToPreviousApp();
    }
  }
}

/// 进度对话框内容
class ProgressDialogContent extends StatefulWidget {
  final ShareDialogController controller;

  const ProgressDialogContent({super.key, required this.controller});

  @override
  State<ProgressDialogContent> createState() => _ProgressDialogContentState();
}

class _ProgressDialogContentState extends State<ProgressDialogContent> with SingleTickerProviderStateMixin {
  late AnimationController _animationController;

  @override
  void initState() {
    super.initState();
    _animationController = AnimationController(vsync: this, duration: const Duration(seconds: 1))..repeat();
  }

  @override
  void dispose() {
    _animationController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Container(
      padding: const EdgeInsets.all(20),
      width: 220,
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Obx(() {
            final progress = widget.controller.saveProgress.value;
            if (progress == 0) {
              return Icon(Icons.error_outline, size: 46, color: Colors.red);
            } else if (progress == 3) {
              return Icon(Icons.check_circle, size: 46, color: Colors.green);
            } else {
              // 动画沙漏
              return RotationTransition(
                turns: _animationController,
                child: Icon(Icons.hourglass_top, size: 46, color: theme.colorScheme.primary),
              );
            }
          }),
          const SizedBox(height: 12),
          Obx(
            () => Text(
              widget.controller.saveProgress.value == 0 ? widget.controller.progressMessage.value : "内容分析中",
              textAlign: TextAlign.center,
              style: const TextStyle(fontSize: 15, fontWeight: FontWeight.w500),
            ),
          ),
          Obx(() {
            final progress = widget.controller.saveProgress.value;
            if (progress == 0) {
              return Padding(
                padding: const EdgeInsets.only(top: 12),
                child: TextButton(
                  style: TextButton.styleFrom(padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 6)),
                  onPressed: () => Get.back(),
                  child: const Text("关闭"),
                ),
              );
            } else {
              return const SizedBox(height: 8);
            }
          }),
        ],
      ),
    );
  }
}
