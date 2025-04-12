import 'package:flutter/material.dart';
import 'package:daily_satori/app/styles/base/colors.dart';
import 'package:daily_satori/app/styles/base/dimensions.dart';
import 'package:daily_satori/app/styles/base/shadows.dart';

/// 卡片样式类
/// 提供应用中各种卡片的样式定义，遵循 shadcn/ui 的设计风格
class CardStyles {
  // 私有构造函数，防止实例化
  CardStyles._();

  /// 获取标准卡片样式
  static BoxDecoration getStandardDecoration(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;

    return BoxDecoration(
      color: isDark ? AppColors.surfaceDark : AppColors.surface,
      borderRadius: BorderRadius.circular(Dimensions.radiusM),
      border: Border.all(color: isDark ? AppColors.outlineDark : AppColors.outline, width: 1.0),
      boxShadow: AppShadows.getCardShadow(context),
    );
  }

  /// 获取无阴影卡片样式
  static BoxDecoration getFlatDecoration(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;

    return BoxDecoration(
      color: isDark ? AppColors.surfaceDark : AppColors.surface,
      borderRadius: BorderRadius.circular(Dimensions.radiusM),
      border: Border.all(color: isDark ? AppColors.outlineDark : AppColors.outline, width: 1.0),
    );
  }

  /// 获取无边框卡片样式
  static BoxDecoration getBorderlessDecoration(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;

    return BoxDecoration(
      color: isDark ? AppColors.surfaceDark : AppColors.surface,
      borderRadius: BorderRadius.circular(Dimensions.radiusM),
      boxShadow: AppShadows.getCardShadow(context),
    );
  }

  /// 获取无边框无阴影卡片样式
  static BoxDecoration getSimpleDecoration(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;

    return BoxDecoration(
      color: isDark ? AppColors.surfaceDark : AppColors.surface,
      borderRadius: BorderRadius.circular(Dimensions.radiusM),
    );
  }

  /// 获取强调卡片样式
  static BoxDecoration getAccentDecoration(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;

    return BoxDecoration(
      color: isDark ? AppColors.surfaceDark : AppColors.surface,
      borderRadius: BorderRadius.circular(Dimensions.radiusM),
      border: Border.all(color: isDark ? AppColors.primaryLight : AppColors.primary, width: 1.5),
      boxShadow: AppShadows.getCardShadow(context),
    );
  }

  /// 获取容器卡片样式（较浅的背景色）
  static BoxDecoration getContainerDecoration(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;

    return BoxDecoration(
      color: isDark ? AppColors.surfaceContainerDark : AppColors.surfaceContainer,
      borderRadius: BorderRadius.circular(Dimensions.radiusM),
      boxShadow: AppShadows.getXsShadow(context),
    );
  }

  /// 获取悬浮卡片样式（增强阴影）
  static BoxDecoration getElevatedDecoration(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;

    return BoxDecoration(
      color: isDark ? AppColors.surfaceDark : AppColors.surface,
      borderRadius: BorderRadius.circular(Dimensions.radiusM),
      boxShadow: AppShadows.getLShadow(context),
    );
  }

  /// 获取列表项卡片样式
  static BoxDecoration getListItemDecoration(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;

    return BoxDecoration(
      color: isDark ? AppColors.surfaceDark : AppColors.surface,
      borderRadius: BorderRadius.circular(Dimensions.radiusS),
      border: Border.all(color: isDark ? AppColors.outlineDark : AppColors.outline, width: 1.0),
    );
  }

  /// 获取圆形卡片样式
  static BoxDecoration getCircularDecoration(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;

    return BoxDecoration(
      color: isDark ? AppColors.surfaceDark : AppColors.surface,
      shape: BoxShape.circle,
      border: Border.all(color: isDark ? AppColors.outlineDark : AppColors.outline, width: 1.0),
      boxShadow: AppShadows.getSShadow(context),
    );
  }

  /// 获取标准卡片内边距
  static EdgeInsets getStandardPadding() => Dimensions.paddingCard;

  /// 获取紧凑卡片内边距
  static EdgeInsets getCompactPadding() => const EdgeInsets.all(Dimensions.spacingS);

  /// 获取列表项内边距
  static EdgeInsets getListItemPadding() => Dimensions.paddingListItem;

  /// 获取边距
  static EdgeInsets getMargin() => Dimensions.marginCard;

  /// 获取卡片主题
  static CardTheme getCardTheme(BuildContext context) {
    return CardTheme(
      elevation: 0,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(Dimensions.radiusM),
        side: BorderSide(
          color: Theme.of(context).brightness == Brightness.dark ? AppColors.outlineDark : AppColors.outline,
          width: 1.0,
        ),
      ),
      color: Theme.of(context).brightness == Brightness.dark ? AppColors.surfaceDark : AppColors.surface,
      margin: getMargin(),
      clipBehavior: Clip.antiAlias,
      shadowColor: Colors.transparent,
    );
  }
}
