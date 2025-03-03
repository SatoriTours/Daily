import 'package:flutter/material.dart';

import 'package:get/get.dart';

import 'package:daily_satori/app/styles/app_theme.dart';
import 'package:daily_satori/app/styles/font_style.dart';

/// UI工具类
class UIUtils {
  // 私有构造函数，防止实例化
  UIUtils._();

  /// 显示成功提示
  static void showSuccess(String content, {String title = '提示'}) {
    Get.snackbar(title, content, snackPosition: SnackPosition.top, backgroundColor: Colors.green);
  }

  /// 显示错误提示
  static void showError(String content, {String title = '错误'}) {
    Get.snackbar(title, content, snackPosition: SnackPosition.top, backgroundColor: Colors.red);
  }

  /// 显示全屏加载提示
  static void showLoading({String tips = '', Color barrierColor = Colors.transparent}) {
    final context = Get.context;
    final textTheme = context != null ? AppTheme.getTextTheme(context) : null;

    Get.dialog(
      PopScope(
        child: Center(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              const CircularProgressIndicator(),
              const SizedBox(height: 16),
              Text(tips, style: textTheme?.bodyMedium ?? MyFontStyle.loadingTipsStyle),
            ],
          ),
        ),
      ),
      barrierDismissible: false,
      barrierColor: barrierColor,
    );
  }

  /// 显示确认对话框
  static Future<void> showConfirmation(
    String title,
    String message, {
    String confirmText = '同意',
    String cancelText = '取消',
    Function()? onConfirmed,
    Function()? onCanceled,
  }) async {
    await Get.dialog<bool>(
      AlertDialog(
        title: Text(title),
        content: Text(message),
        actions: [
          TextButton(
            onPressed: () {
              Get.close();
              onCanceled?.call();
            },
            child: Text(cancelText),
          ),
          TextButton(
            onPressed: () {
              Get.close();
              onConfirmed?.call();
            },
            child: Text(confirmText),
          ),
        ],
      ),
    );
  }
}
