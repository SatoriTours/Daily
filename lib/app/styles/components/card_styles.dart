import 'package:flutter/material.dart';
import 'package:daily_satori/app/styles/base/colors.dart';
import 'package:daily_satori/app/styles/base/dimensions.dart';
import 'package:daily_satori/app/styles/base/shadows.dart';

/// 卡片样式类
///
/// 提供应用中各种卡片的样式定义，遵循统一的设计风格。
/// 所有方法都使用主题感知的颜色，自动适配亮色/暗色模式。
class CardStyles {
  CardStyles._();

  // ========================================================================
  // 标准卡片装饰
  // ========================================================================

  /// 获取标准卡片样式 - 带边框和阴影
  static BoxDecoration getStandardDecoration(BuildContext context) {
    return BoxDecoration(
      color: AppColors.getSurface(context),
      borderRadius: BorderRadius.circular(Dimensions.radiusM),
      border: Border.all(color: AppColors.getOutline(context), width: 1.0),
      boxShadow: AppShadows.getCardShadow(context),
    );
  }

  /// 获取无阴影卡片样式 - 带边框，无阴影
  static BoxDecoration getFlatDecoration(BuildContext context) {
    return BoxDecoration(
      color: AppColors.getSurface(context),
      borderRadius: BorderRadius.circular(Dimensions.radiusM),
      border: Border.all(color: AppColors.getOutline(context), width: 1.0),
    );
  }

  /// 获取无边框卡片样式 - 带阴影，无边框
  static BoxDecoration getBorderlessDecoration(BuildContext context) {
    return BoxDecoration(
      color: AppColors.getSurface(context),
      borderRadius: BorderRadius.circular(Dimensions.radiusM),
      boxShadow: AppShadows.getCardShadow(context),
    );
  }

  /// 获取简洁卡片样式 - 无边框无阴影
  static BoxDecoration getSimpleDecoration(BuildContext context) {
    return BoxDecoration(color: AppColors.getSurface(context), borderRadius: BorderRadius.circular(Dimensions.radiusM));
  }

  // ========================================================================
  // 特殊卡片装饰
  // ========================================================================

  /// 获取强调卡片样式 - 主题色边框
  static BoxDecoration getAccentDecoration(BuildContext context) {
    return BoxDecoration(
      color: AppColors.getSurface(context),
      borderRadius: BorderRadius.circular(Dimensions.radiusM),
      border: Border.all(color: AppColors.getPrimary(context), width: 1.5),
      boxShadow: AppShadows.getCardShadow(context),
    );
  }

  /// 获取容器卡片样式 - 较浅的背景色，适合内嵌内容
  static BoxDecoration getContainerDecoration(BuildContext context) {
    return BoxDecoration(
      color: AppColors.getSurfaceContainer(context),
      borderRadius: BorderRadius.circular(Dimensions.radiusM),
      boxShadow: AppShadows.getXsShadow(context),
    );
  }

  /// 获取悬浮卡片样式 - 增强阴影，用于突出展示
  static BoxDecoration getElevatedDecoration(BuildContext context) {
    return BoxDecoration(
      color: AppColors.getSurface(context),
      borderRadius: BorderRadius.circular(Dimensions.radiusM),
      boxShadow: AppShadows.getLShadow(context),
    );
  }

  /// 获取列表项卡片样式 - 较小圆角
  static BoxDecoration getListItemDecoration(BuildContext context) {
    return BoxDecoration(
      color: AppColors.getSurface(context),
      borderRadius: BorderRadius.circular(Dimensions.radiusS),
      border: Border.all(color: AppColors.getOutline(context), width: 1.0),
    );
  }

  /// 获取圆形卡片样式
  static BoxDecoration getCircularDecoration(BuildContext context) {
    return BoxDecoration(
      color: AppColors.getSurface(context),
      shape: BoxShape.circle,
      border: Border.all(color: AppColors.getOutline(context), width: 1.0),
      boxShadow: AppShadows.getSShadow(context),
    );
  }

  // ========================================================================
  // 内边距和边距
  // ========================================================================

  /// 获取标准卡片内边距
  static EdgeInsets getStandardPadding() => Dimensions.paddingCard;

  /// 获取紧凑卡片内边距
  static EdgeInsets getCompactPadding() => const EdgeInsets.all(Dimensions.spacingS);

  /// 获取列表项内边距
  static EdgeInsets getListItemPadding() => Dimensions.paddingListItem;

  /// 获取外边距
  static EdgeInsets getMargin() => Dimensions.marginCard;

  // ========================================================================
  // 主题
  // ========================================================================

  /// 获取卡片主题 - 用于 ThemeData
  static CardTheme getCardTheme(BuildContext context) {
    return CardTheme(
      elevation: 0,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(Dimensions.radiusM),
        side: BorderSide(color: AppColors.getOutline(context), width: 1.0),
      ),
      color: AppColors.getSurface(context),
      margin: getMargin(),
      clipBehavior: Clip.antiAlias,
      shadowColor: Colors.transparent,
    );
  }
}
