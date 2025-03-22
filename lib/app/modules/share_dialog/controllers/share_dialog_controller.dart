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
  final RxString shareURL = ''.obs;
  final RxBool isUpdate = false.obs;
  final RxBool needBackToApp = false.obs;
  final RxInt articleID = 0.obs;
  final RxBool isLoading = false.obs;
  final RxString errorMessage = ''.obs;

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

  /// 保存按钮点击
  void onSaveButtonPressed() async {
    // 检查URL是否有效
    if (shareURL.value.isEmpty) {
      _showMessage("链接为空，无法保存");
      return;
    }

    try {
      showLoading();

      // 显示保存中提示
      _showSavingDialog();

      // 调用网页解析服务保存网页基本信息
      final articleModel = await WebpageParserService.i.saveWebpage(
        url: shareURL.value,
        comment: commentController.text,
        isUpdate: isUpdate.value,
        articleID: articleID.value,
      );

      // 通知文章列表更新
      Get.find<ArticlesController>().updateArticle(articleModel.id);

      // 完成处理
      _completeProcess();

      // 显示成功消息
      _showSuccessMessage();
    } catch (e, stackTrace) {
      logger.e("保存网页失败: $e\n堆栈信息: $stackTrace");
      _showMessage("保存失败: $e");
    } finally {
      hideLoading();
    }
  }

  /// 显示保存对话框
  void _showSavingDialog() {
    Get.defaultDialog(
      title: "保存中",
      content: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text("正在保存链接: ${getShortUrl(shareURL.value)}"),
          const SizedBox(height: 10),
          const CircularProgressIndicator(),
        ],
      ),
      barrierDismissible: false,
    );
  }

  /// 显示成功消息
  void _showSuccessMessage() {
    Get.snackbar(
      "成功",
      "网页已保存，正在后台处理内容",
      snackPosition: SnackPosition.bottom,
      backgroundColor: Colors.green,
      colorText: Colors.white,
      margin: const EdgeInsets.all(10),
      duration: const Duration(seconds: 2),
    );
  }

  /// 获取短URL显示
  String getShortUrl(String url) {
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

  /// 完成处理流程
  Future<void> _completeProcess() async {
    // 关闭保存对话框
    if (Get.isDialogOpen ?? false) {
      Get.back();
    }

    // 根据需要返回到应用或主页
    if (!needBackToApp.value) {
      if (!isUpdate.value) {
        await Get.find<ArticlesController>().reloadArticles();
      }
      Get.offAllNamed(Routes.HOME);
    } else {
      _backToPreviousApp();
    }
  }

  /// 显示提示信息
  void _showMessage(String message) {
    // 如果对话框正在显示，则关闭它
    if (Get.isDialogOpen ?? false) {
      Get.back();
    }

    Get.snackbar(
      "提示",
      message,
      snackPosition: SnackPosition.bottom,
      backgroundColor: Colors.black87,
      colorText: Colors.white,
      margin: const EdgeInsets.all(10),
    );
  }
}
