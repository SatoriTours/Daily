import 'package:flutter/material.dart';
import 'package:get/get.dart';
import 'package:daily_satori/app/styles/index.dart';
import 'package:daily_satori/app/extensions/i18n_extension.dart';

/// UI工具类
class UIUtils {
  // 私有构造函数，防止实例化
  UIUtils._();

  /// 显示成功提示（统一样式）
  static void showSuccess(String content, {String title = 'message.success_title'}) {
    showSnackBar(title, content, isError: false);
  }

  /// 显示错误提示（统一样式）
  static void showError(String content, {String title = 'message.error_title'}) {
    showSnackBar(title, content, isError: true);
  }

  /// 显示通用提示条
  static void showSnackBar(String title, String message, {bool isError = false, Duration? duration}) {
    final context = Get.context;
    final bg = context != null
        ? SnackbarStyles.getBackgroundColor(context, isError: isError)
        : (isError ? Colors.red.withAlpha(204) : Colors.green.withAlpha(204));
    Get.snackbar(
      title.t,
      message.t,
      snackPosition: SnackPosition.top,
      backgroundColor: bg,
      colorText: context != null ? SnackbarStyles.getTextColor(context) : Colors.white,
      duration: duration ?? SnackbarStyles.getDuration(),
      margin: SnackbarStyles.getMargin(),
      borderRadius: SnackbarStyles.getBorderRadius(),
      icon: SnackbarStyles.getIcon(isError: isError),
    );
  }
}