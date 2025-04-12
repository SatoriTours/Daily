import 'package:flutter/material.dart';
import 'package:daily_satori/app/styles/base/colors.dart';
import 'package:daily_satori/app/styles/base/dimensions.dart';
import 'package:daily_satori/app/styles/base/typography.dart';
import 'package:daily_satori/app/styles/base/shadows.dart';

/// 文章模块样式类
/// 提供文章模块的特定样式定义，遵循统一的设计风格
class ArticlesStyles {
  // 私有构造函数，防止实例化
  ArticlesStyles._();

  /// 获取文章页面背景色
  static Color getBackgroundColor(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    return isDark ? AppColors.backgroundDark : AppColors.background;
  }

  /// 获取文章卡片背景色
  static Color getCardBackgroundColor(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    return isDark ? AppColors.surfaceDark : AppColors.surface;
  }

  /// 获取卡片阴影
  static List<BoxShadow> getCardShadow(BuildContext context) {
    return AppShadows.getCardShadow(context);
  }

  /// 获取文章卡片装饰
  static BoxDecoration getCardDecoration(BuildContext context) {
    return BoxDecoration(
      color: getCardBackgroundColor(context),
      borderRadius: BorderRadius.circular(Dimensions.radiusM),
      boxShadow: getCardShadow(context),
      border: Border.all(color: isDark(context) ? AppColors.outlineDark : AppColors.outline, width: 1),
    );
  }

  /// 获取文章卡片内边距
  static EdgeInsets getCardPadding() => Dimensions.paddingCard;

  /// 获取文章项目外边距
  static EdgeInsets getArticleItemMargin() => Dimensions.marginCard;

  /// 获取文章标题文本样式
  static TextStyle getTitleTextStyle(BuildContext context) {
    return AppTypography.getThemedStyle(
      context,
      AppTypography.titleMedium,
      lightColor: AppColors.onSurface,
      darkColor: AppColors.onSurfaceDark,
    );
  }

  /// 获取文章摘要文本样式
  static TextStyle getSummaryTextStyle(BuildContext context) {
    return AppTypography.getThemedStyle(
      context,
      AppTypography.bodyMedium,
      lightColor: AppColors.onSurfaceVariant,
      darkColor: AppColors.onSurfaceVariantDark,
    );
  }

  /// 获取文章日期文本样式
  static TextStyle getDateTextStyle(BuildContext context) {
    return AppTypography.getThemedStyle(
      context,
      AppTypography.captionText,
      lightColor: AppColors.onSurfaceVariant.withOpacity(0.8),
      darkColor: AppColors.onSurfaceVariantDark.withOpacity(0.8),
    );
  }

  /// 获取文章标签文本样式
  static TextStyle getTagTextStyle(BuildContext context) {
    return AppTypography.getThemedStyle(
      context,
      AppTypography.chipText,
      lightColor: AppColors.primary,
      darkColor: AppColors.primaryLight,
    );
  }

  /// 获取文章标签装饰
  static BoxDecoration getTagDecoration(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;

    return BoxDecoration(
      color: isDark ? AppColors.primaryLight.withOpacity(0.15) : AppColors.primary.withOpacity(0.1),
      borderRadius: BorderRadius.circular(Dimensions.radiusL),
    );
  }

  /// 获取文章标签内边距
  static EdgeInsets getTagPadding() =>
      const EdgeInsets.symmetric(horizontal: Dimensions.spacingS, vertical: Dimensions.spacingXxs);

  /// 获取搜索栏背景色
  static Color getSearchBarBackgroundColor(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    return isDark ? AppColors.surfaceContainerDark : AppColors.surfaceContainer;
  }

  /// 获取搜索栏文本颜色
  static Color getSearchBarTextColor(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    return isDark ? AppColors.onSurfaceDark : AppColors.onSurface;
  }

  /// 获取搜索栏提示文本颜色
  static Color getSearchBarHintColor(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    return isDark ? AppColors.onSurfaceVariantDark : AppColors.onSurfaceVariant;
  }

  /// 获取过滤指示器装饰
  static BoxDecoration getFilterIndicatorDecoration(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;

    return BoxDecoration(
      color: isDark ? AppColors.surfaceContainerHighestDark : AppColors.surfaceContainerHighest,
      borderRadius: BorderRadius.circular(Dimensions.radiusS),
    );
  }

  /// 获取过滤指示器内边距
  static EdgeInsets getFilterIndicatorPadding() =>
      const EdgeInsets.symmetric(horizontal: Dimensions.spacingM, vertical: Dimensions.spacingS);

  /// 获取过滤指示器文本样式
  static TextStyle getFilterIndicatorTextStyle(BuildContext context) {
    return AppTypography.getThemedStyle(
      context,
      AppTypography.labelMedium,
      lightColor: AppColors.onSurface,
      darkColor: AppColors.onSurfaceDark,
    );
  }

  /// 获取过滤指示器操作文本样式
  static TextStyle getFilterIndicatorActionTextStyle(BuildContext context) {
    return AppTypography.getThemedStyle(
      context,
      AppTypography.labelMedium,
      lightColor: AppColors.primary,
      darkColor: AppColors.primaryLight,
    );
  }

  /// 获取应用栏背景色
  static Color getAppBarBackgroundColor(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    return isDark ? const Color(0xFF121212) : const Color(0xFF5E8BFF);
  }

  /// 获取应用栏标题文本样式
  static TextStyle getAppBarTitleTextStyle() {
    return const TextStyle(fontSize: 18, color: Colors.white, fontWeight: FontWeight.w500);
  }

  /// 获取应用栏图标颜色
  static Color getAppBarIconColor() => Colors.white;

  /// 获取应用栏图标尺寸
  static double getAppBarIconSize() => Dimensions.iconSizeM;

  /// 获取底部表单装饰
  static BoxDecoration getBottomSheetDecoration(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;

    return BoxDecoration(
      color: isDark ? AppColors.surfaceDark : AppColors.surface,
      borderRadius: const BorderRadius.vertical(top: Radius.circular(Dimensions.radiusL)),
      boxShadow: AppShadows.getBottomSheetShadow(context),
    );
  }

  /// 获取底部表单形状
  static RoundedRectangleBorder getBottomSheetShape() {
    return const RoundedRectangleBorder(borderRadius: BorderRadius.vertical(top: Radius.circular(Dimensions.radiusL)));
  }

  /// 是否为暗色模式
  static bool isDark(BuildContext context) {
    return Theme.of(context).brightness == Brightness.dark;
  }
}
