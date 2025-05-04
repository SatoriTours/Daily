import 'package:flutter/material.dart';
import 'package:daily_satori/app/styles/base/colors.dart';
import 'package:daily_satori/app/styles/base/dimensions.dart';

/// 应用边框样式常量
/// 提供统一的边框相关样式定义，遵循 shadcn/ui 的设计风格
class AppBorders {
  // 私有构造函数，防止实例化
  AppBorders._();

  /// 获取基本边框 - 浅色模式
  static BorderSide getBaseBorder(BuildContext context, {double width = 1.0}) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    return BorderSide(color: isDark ? AppColors.outlineDark : AppColors.outline, width: width);
  }

  /// 获取主题色边框
  static BorderSide getPrimaryBorder(BuildContext context, {double width = 1.0}) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    return BorderSide(color: isDark ? AppColors.primaryLight : AppColors.primary, width: width);
  }

  /// 获取输入框边框 - 默认状态
  static InputBorder getInputBorder(BuildContext context) {
    return OutlineInputBorder(
      borderRadius: BorderRadius.circular(Dimensions.radiusS),
      borderSide: getBaseBorder(context),
    );
  }

  /// 获取输入框边框 - 聚焦状态
  static InputBorder getInputFocusedBorder(BuildContext context) {
    return OutlineInputBorder(
      borderRadius: BorderRadius.circular(Dimensions.radiusS),
      borderSide: getPrimaryBorder(context, width: 2.0),
    );
  }

  /// 获取输入框边框 - 禁用状态
  static InputBorder getInputDisabledBorder(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    return OutlineInputBorder(
      borderRadius: BorderRadius.circular(Dimensions.radiusS),
      borderSide: BorderSide(
        color: isDark ? AppColors.outlineDark.withValues(alpha: 0.5) : AppColors.outline.withValues(alpha: 0.5),
        width: 1.0,
      ),
    );
  }

  /// 获取输入框边框 - 错误状态
  static InputBorder getInputErrorBorder(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    return OutlineInputBorder(
      borderRadius: BorderRadius.circular(Dimensions.radiusS),
      borderSide: BorderSide(color: isDark ? AppColors.errorDark : AppColors.error, width: 1.5),
    );
  }

  /// 获取按钮边框 - 轮廓按钮
  static BorderSide getOutlinedButtonBorder(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    return BorderSide(color: isDark ? AppColors.primaryLight : AppColors.primary, width: 1.5);
  }

  /// 获取卡片边框
  static BorderSide getCardBorder(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    return BorderSide(color: isDark ? AppColors.outlineDark : AppColors.outline, width: 1.0);
  }

  /// 获取分隔线
  static Divider getDivider(BuildContext context, {double height = 1.0, double indent = 0.0}) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    return Divider(
      height: height,
      thickness: Dimensions.dividerHeight,
      color: isDark ? AppColors.outlineDark : AppColors.outline,
      indent: indent,
      endIndent: indent,
    );
  }

  /// 获取垂直分隔线
  static VerticalDivider getVerticalDivider(BuildContext context, {double width = 1.0, double indent = 0.0}) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    return VerticalDivider(
      width: width,
      thickness: Dimensions.dividerHeight,
      color: isDark ? AppColors.outlineDark : AppColors.outline,
      indent: indent,
      endIndent: indent,
    );
  }

  /// 获取轻微边框装饰 - 适用于卡片、容器等
  static BoxDecoration getLightBorderDecoration(
    BuildContext context, {
    BorderRadius? borderRadius,
    Color? backgroundColor,
  }) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    return BoxDecoration(
      color: backgroundColor ?? (isDark ? AppColors.surfaceDark : AppColors.surface),
      borderRadius: borderRadius ?? BorderRadius.circular(Dimensions.radiusS),
      border: Border.all(color: isDark ? AppColors.outlineDark : AppColors.outline, width: 1.0),
    );
  }

  /// 获取主题色边框装饰
  static BoxDecoration getPrimaryBorderDecoration(
    BuildContext context, {
    BorderRadius? borderRadius,
    Color? backgroundColor,
  }) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    return BoxDecoration(
      color: backgroundColor ?? (isDark ? AppColors.surfaceDark : AppColors.surface),
      borderRadius: borderRadius ?? BorderRadius.circular(Dimensions.radiusS),
      border: Border.all(color: isDark ? AppColors.primaryLight : AppColors.primary, width: 1.5),
    );
  }
}
