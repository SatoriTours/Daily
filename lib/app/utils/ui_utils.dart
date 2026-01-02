import 'package:flutter/material.dart';
import 'package:daily_satori/app/styles/index.dart';
import 'package:daily_satori/app/utils/i18n_extension.dart';
import 'package:daily_satori/app/routes/app_navigation.dart';

/// UI工具类
class UIUtils {
  // 私有构造函数，防止实例化
  UIUtils._();

  /// 显示成功提示（统一样式）
  static void showSuccess(
    String content, {
    String title = 'message.success_title',
    bool isTop = true,
    BuildContext? context,
  }) {
    showSnackBar(title, content, isError: false, isTop: isTop, context: context);
  }

  /// 显示错误提示（统一样式）
  static void showError(
    String content, {
    String title = 'message.error_title',
    bool isTop = true,
    BuildContext? context,
  }) {
    showSnackBar(title, content, isError: true, isTop: isTop, context: context);
  }

  /// 显示通用提示条
  static void showSnackBar(
    String title,
    String message, {
    bool isError = false,
    bool isTop = true,
    Duration? duration,
    BuildContext? context,
  }) {
    final ctx = context ?? AppNavigation.navigatorKey.currentContext;
    if (ctx == null) return;

    final bg = SnackbarStyles.getBackgroundColor(ctx, isError: isError);
    final textColor = SnackbarStyles.getTextColor(ctx);
    final mediaQuery = MediaQuery.of(ctx);

    EdgeInsets margin;
    if (isTop) {
      // 顶部显示：考虑状态栏安全区域，在状态栏下方显示
      final topPadding = mediaQuery.padding.top;
      final bottomPadding = mediaQuery.padding.bottom;
      const snackBarHeight = 80.0; // 预估 SnackBar 高度
      const bottomNavHeight = 80.0; // 预估底部导航栏高度
      final topOffset = topPadding + 16; // 状态栏高度 + 16px 间距

      // 计算可用高度，考虑底部导航栏和安全区域
      final availableHeight =
          mediaQuery.size.height - topOffset - snackBarHeight - bottomNavHeight - bottomPadding - 16;

      margin = EdgeInsets.only(left: 16, right: 16, top: topOffset, bottom: availableHeight > 0 ? availableHeight : 16);
    } else {
      // 底部显示：考虑底部导航栏和安全区域
      final bottomPadding = mediaQuery.padding.bottom;
      const bottomNavHeight = 80.0; // 预估底部导航栏高度
      margin = EdgeInsets.only(left: 16, right: 16, top: 16, bottom: bottomNavHeight + bottomPadding + 16);
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
