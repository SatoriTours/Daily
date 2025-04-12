import 'package:flutter/material.dart';
import 'package:daily_satori/app/styles/base/colors.dart';
import 'package:daily_satori/app/styles/base/dimensions.dart';
import 'package:daily_satori/app/styles/base/typography.dart';
import 'package:daily_satori/app/styles/base/borders.dart';

/// 输入框样式类
/// 提供应用中各种输入框的样式定义，遵循 shadcn/ui 的设计风格
class InputStyles {
  // 私有构造函数，防止实例化
  InputStyles._();

  /// 获取输入装饰主题
  static InputDecorationTheme getInputDecorationTheme(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;

    return InputDecorationTheme(
      filled: true,
      fillColor: isDark ? AppColors.surfaceContainerDark : AppColors.surfaceContainer,
      contentPadding: Dimensions.paddingInput,
      labelStyle: AppTypography.bodyMedium.copyWith(
        color: isDark ? AppColors.onSurfaceVariantDark : AppColors.onSurfaceVariant,
      ),
      hintStyle: AppTypography.bodyMedium.copyWith(
        color: isDark ? AppColors.onSurfaceVariantDark.withOpacity(0.7) : AppColors.onSurfaceVariant.withOpacity(0.7),
      ),
      helperStyle: AppTypography.captionText.copyWith(
        color: isDark ? AppColors.onSurfaceVariantDark : AppColors.onSurfaceVariant,
      ),
      errorStyle: AppTypography.errorText.copyWith(color: isDark ? AppColors.errorDark : AppColors.error),
      border: OutlineInputBorder(
        borderRadius: BorderRadius.circular(Dimensions.radiusS),
        borderSide: BorderSide(color: isDark ? AppColors.outlineDark : AppColors.outline, width: 1.0),
      ),
      enabledBorder: OutlineInputBorder(
        borderRadius: BorderRadius.circular(Dimensions.radiusS),
        borderSide: BorderSide(color: isDark ? AppColors.outlineDark : AppColors.outline, width: 1.0),
      ),
      focusedBorder: OutlineInputBorder(
        borderRadius: BorderRadius.circular(Dimensions.radiusS),
        borderSide: BorderSide(color: isDark ? AppColors.primaryLight : AppColors.primary, width: 2.0),
      ),
      errorBorder: OutlineInputBorder(
        borderRadius: BorderRadius.circular(Dimensions.radiusS),
        borderSide: BorderSide(color: isDark ? AppColors.errorDark : AppColors.error, width: 1.5),
      ),
      focusedErrorBorder: OutlineInputBorder(
        borderRadius: BorderRadius.circular(Dimensions.radiusS),
        borderSide: BorderSide(color: isDark ? AppColors.errorDark : AppColors.error, width: 1.5),
      ),
      disabledBorder: OutlineInputBorder(
        borderRadius: BorderRadius.circular(Dimensions.radiusS),
        borderSide: BorderSide(
          color: isDark ? AppColors.outlineDark.withOpacity(0.5) : AppColors.outline.withOpacity(0.5),
          width: 1.0,
        ),
      ),
    );
  }

  /// 获取搜索框装饰
  static InputDecoration getSearchDecoration(BuildContext context, {String hintText = '搜索...'}) {
    final isDark = Theme.of(context).brightness == Brightness.dark;

    return InputDecoration(
      hintText: hintText,
      prefixIcon: Icon(
        Icons.search,
        color: isDark ? AppColors.onSurfaceVariantDark : AppColors.onSurfaceVariant,
        size: Dimensions.iconSizeM,
      ),
      filled: true,
      fillColor: isDark ? AppColors.surfaceContainerDark : AppColors.surfaceContainer,
      contentPadding: Dimensions.paddingSearchBar,
      border: OutlineInputBorder(borderRadius: BorderRadius.circular(Dimensions.radiusS), borderSide: BorderSide.none),
      enabledBorder: OutlineInputBorder(
        borderRadius: BorderRadius.circular(Dimensions.radiusS),
        borderSide: BorderSide.none,
      ),
      focusedBorder: OutlineInputBorder(
        borderRadius: BorderRadius.circular(Dimensions.radiusS),
        borderSide: BorderSide(color: isDark ? AppColors.primaryLight : AppColors.primary, width: 1.0),
      ),
    );
  }

  /// 获取日记输入框装饰（无边框）
  static InputDecoration getCleanInputDecoration(BuildContext context, {String hintText = ''}) {
    final isDark = Theme.of(context).brightness == Brightness.dark;

    return InputDecoration(
      hintText: hintText,
      filled: false,
      isDense: true,
      contentPadding: EdgeInsets.zero,
      border: InputBorder.none,
      enabledBorder: InputBorder.none,
      focusedBorder: InputBorder.none,
      errorBorder: InputBorder.none,
      focusedErrorBorder: InputBorder.none,
      disabledBorder: InputBorder.none,
      hintStyle: AppTypography.bodyMedium.copyWith(
        color: isDark ? AppColors.onSurfaceVariantDark.withOpacity(0.5) : AppColors.onSurfaceVariant.withOpacity(0.5),
      ),
    );
  }

  /// 获取标题输入框样式（无边框，较大字体）
  static InputDecoration getTitleInputDecoration(BuildContext context, {String hintText = '标题'}) {
    final isDark = Theme.of(context).brightness == Brightness.dark;

    return InputDecoration(
      hintText: hintText,
      filled: false,
      isDense: true,
      contentPadding: EdgeInsets.zero,
      border: InputBorder.none,
      enabledBorder: InputBorder.none,
      focusedBorder: InputBorder.none,
      hintStyle: AppTypography.titleMedium.copyWith(
        color: isDark ? AppColors.onSurfaceVariantDark.withOpacity(0.5) : AppColors.onSurfaceVariant.withOpacity(0.5),
      ),
    );
  }
}
