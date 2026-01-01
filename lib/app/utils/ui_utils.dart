import 'package:flutter/material.dart';
import 'package:daily_satori/app/styles/index.dart';
import 'package:daily_satori/app/utils/i18n_extension.dart';
import 'package:daily_satori/app/navigation/app_navigation.dart';

/// UI工具类
class UIUtils {
  // 私有构造函数，防止实例化
  UIUtils._();

  /// 显示成功提示（统一样式）
  static void showSuccess(
    String content, {
    String title = 'message.success_title',
    bool isTop = false,
    BuildContext? context,
  }) {
    showSnackBar(title, content, isError: false, isTop: isTop, context: context);
  }

  /// 显示错误提示（统一样式）
  static void showError(
    String content, {
    String title = 'message.error_title',
    bool isTop = false,
    BuildContext? context,
  }) {
    showSnackBar(title, content, isError: true, isTop: isTop, context: context);
  }

  /// 显示通用提示条
  static void showSnackBar(
    String title,
    String message, {
    bool isError = false,
    bool isTop = false,
    Duration? duration,
    BuildContext? context,
  }) {
    final ctx = context ?? AppNavigation.navigatorKey.currentContext;
    if (ctx == null) return;

    final bg = SnackbarStyles.getBackgroundColor(ctx, isError: isError);
    final textColor = SnackbarStyles.getTextColor(ctx);

    EdgeInsets margin = SnackbarStyles.getMargin();
    if (isTop) {
      final size = MediaQuery.of(ctx).size;
      margin = margin.copyWith(bottom: size.height - 160);
    }

    ScaffoldMessenger.of(ctx).showSnackBar(
      SnackBar(
        content: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                SnackbarStyles.getIcon(isError: isError),
                const SizedBox(width: 8),
                Expanded(
                  child: Text(
                    title.t,
                    style: TextStyle(color: textColor, fontWeight: FontWeight.bold),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 4),
            Text(message.t, style: TextStyle(color: textColor)),
          ],
        ),
        backgroundColor: bg,
        duration: duration ?? SnackbarStyles.getDuration(),
        margin: margin,
        behavior: SnackBarBehavior.floating,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(SnackbarStyles.getBorderRadius())),
      ),
    );
  }
}
