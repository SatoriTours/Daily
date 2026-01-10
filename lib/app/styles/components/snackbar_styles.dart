import 'package:flutter/material.dart';

import 'package:daily_satori/app/styles/base/colors.dart';
import 'package:daily_satori/app/styles/base/dimensions.dart';

/// Snackbar 样式
/// 统一应用内的提示条（成功/错误等）外观，避免各处硬编码
class SnackbarStyles {
  SnackbarStyles._();

  /// 背景色（带透明度）
  static Color getBackgroundColor(
    BuildContext context, {
    required bool isError,
  }) {
    final base = isError
        ? AppColors.getError(context)
        : AppColors.getSuccess(context);
    return base.withAlpha(204); // ~80% 透明度
  }

  /// 文本颜色
  static Color getTextColor(BuildContext context) => Colors.white;

  /// 图标
  static Icon getIcon({required bool isError}) => Icon(
    isError ? Icons.error_outline : Icons.check_circle_outline,
    color: Colors.white,
  );

  /// 外边距
  static EdgeInsets getMargin() => const EdgeInsets.all(Dimensions.spacingS);

  /// 圆角
  static double getBorderRadius() => Dimensions.radiusS;

  /// 默认时长
  static Duration getDuration() => const Duration(seconds: 3);
}
