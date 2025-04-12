import 'package:flutter/material.dart';
import 'package:daily_satori/app/styles/base/colors.dart';
import 'package:daily_satori/app/styles/base/dimensions.dart';
import 'package:daily_satori/app/styles/base/typography.dart';
import 'package:daily_satori/app/styles/base/shadows.dart';

/// 日记模块样式类
/// 提供日记模块的特定样式定义，遵循统一的设计风格
class DiaryStyles {
  // 私有构造函数，防止实例化
  DiaryStyles._();

  /// 获取日记页面背景色
  static Color getBackgroundColor(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    return isDark ? AppColors.backgroundDark : AppColors.background;
  }

  /// 获取日记卡片背景色
  static Color getCardBackgroundColor(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    return isDark ? AppColors.surfaceDark : AppColors.surface;
  }

  /// 获取卡片阴影
  static List<BoxShadow> getCardShadow(BuildContext context) {
    return AppShadows.getCardShadow(context);
  }

  /// 获取主要文本颜色
  static Color getPrimaryTextColor(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    return isDark ? AppColors.onSurfaceDark : AppColors.onSurface;
  }

  /// 获取次要文本颜色
  static Color getSecondaryTextColor(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    return isDark ? AppColors.onSurfaceVariantDark : AppColors.onSurfaceVariant;
  }

  /// 获取时间文本颜色
  static Color getTimeTextColor(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    return isDark ? AppColors.onSurfaceVariantDark.withOpacity(0.8) : AppColors.onSurfaceVariant.withOpacity(0.8);
  }

  /// 获取输入框背景颜色
  static Color getInputBackgroundColor(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    return isDark ? AppColors.surfaceContainerDark : AppColors.surfaceContainer;
  }

  /// 获取标签背景颜色
  static Color getTagBackgroundColor(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    return isDark ? AppColors.primaryLight.withOpacity(0.15) : AppColors.primary.withOpacity(0.1);
  }

  /// 获取标签文本颜色
  static Color getTagTextColor(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    return isDark ? AppColors.primaryLight : AppColors.primary;
  }

  /// 获取强调色
  static Color getAccentColor(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    return isDark ? AppColors.primaryLight : AppColors.primary;
  }

  /// 获取分割线颜色
  static Color getDividerColor(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    return isDark ? AppColors.outlineDark : AppColors.outline;
  }

  /// 获取底部抽屉背景色
  static Color getBottomSheetColor(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    return isDark ? AppColors.surfaceDark : AppColors.surface;
  }

  /// 获取日记卡片装饰
  static BoxDecoration getCardDecoration(BuildContext context) {
    return BoxDecoration(
      color: getCardBackgroundColor(context),
      borderRadius: BorderRadius.circular(Dimensions.radiusM),
      boxShadow: getCardShadow(context),
      border: Border.all(color: getDividerColor(context), width: 1),
    );
  }

  /// 获取日记卡片内边距
  static EdgeInsets getCardPadding() => const EdgeInsets.all(Dimensions.spacingM);

  /// 获取日记项目外边距
  static EdgeInsets getDiaryItemMargin() =>
      const EdgeInsets.symmetric(horizontal: Dimensions.spacingM, vertical: Dimensions.spacingS);

  /// 获取日记标题文本样式
  static TextStyle getTitleTextStyle(BuildContext context) {
    return AppTypography.getThemedStyle(
      context,
      AppTypography.titleMedium,
      lightColor: getPrimaryTextColor(context),
      darkColor: getPrimaryTextColor(context),
    );
  }

  /// 获取日记内容文本样式
  static TextStyle getContentTextStyle(BuildContext context) {
    return AppTypography.getThemedStyle(
      context,
      AppTypography.bodyMedium,
      lightColor: getPrimaryTextColor(context),
      darkColor: getPrimaryTextColor(context),
    );
  }

  /// 获取日记时间文本样式
  static TextStyle getTimeTextStyle(BuildContext context) {
    return AppTypography.getThemedStyle(
      context,
      AppTypography.captionText,
      lightColor: getTimeTextColor(context),
      darkColor: getTimeTextColor(context),
    );
  }

  /// 获取标签文本样式
  static TextStyle getTagTextStyle(BuildContext context) {
    return AppTypography.getThemedStyle(
      context,
      AppTypography.chipText,
      lightColor: getTagTextColor(context),
      darkColor: getTagTextColor(context),
    );
  }

  /// 获取标签装饰
  static BoxDecoration getTagDecoration(BuildContext context) {
    return BoxDecoration(
      color: getTagBackgroundColor(context),
      borderRadius: BorderRadius.circular(Dimensions.radiusL),
    );
  }

  /// 获取标签内边距
  static EdgeInsets getTagPadding() =>
      const EdgeInsets.symmetric(horizontal: Dimensions.spacingS, vertical: Dimensions.spacingXxs);

  /// 获取底部表单装饰
  static BoxDecoration getBottomSheetDecoration(BuildContext context) {
    return BoxDecoration(
      color: getBottomSheetColor(context),
      borderRadius: const BorderRadius.vertical(top: Radius.circular(Dimensions.radiusL)),
      boxShadow: AppShadows.getBottomSheetShadow(context),
    );
  }

  /// 获取底部表单形状
  static RoundedRectangleBorder getBottomSheetShape() {
    return const RoundedRectangleBorder(borderRadius: BorderRadius.vertical(top: Radius.circular(Dimensions.radiusL)));
  }

  /// 获取底部表单内边距
  static EdgeInsets getBottomSheetPadding() =>
      const EdgeInsets.fromLTRB(Dimensions.spacingM, Dimensions.spacingM, Dimensions.spacingM, Dimensions.spacingXxl);

  /// 获取浮动按钮颜色
  static Color getFabColor(BuildContext context) {
    return getAccentColor(context);
  }

  /// 获取浮动按钮图标颜色
  static Color getFabIconColor(BuildContext context) {
    return Colors.white;
  }
}
