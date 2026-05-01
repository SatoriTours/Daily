import 'package:flutter/material.dart';
import 'package:daily_satori/app/styles/base/colors.dart';
import 'package:daily_satori/app/styles/base/dimensions.dart';
import 'package:daily_satori/app/styles/base/typography.dart';
import 'package:daily_satori/app/styles/base/shadows.dart';

/// 日记模块样式类
///
/// 提供日记模块的特定样式定义。
///
/// **使用指南**：
/// - 颜色获取优先使用 [AppColors] 中的主题感知方法
/// - 本类仅保留日记模块独有的样式方法
///
/// **迁移说明**：
/// - `getBackgroundColor` → `AppColors.getBackground(context)`
/// - `getCardBackgroundColor` → `AppColors.getSurface(context)`
/// - `getPrimaryTextColor` → `AppColors.getOnSurface(context)`
/// - `getSecondaryTextColor` → `AppColors.getOnSurfaceVariant(context)`
/// - `getInputBackgroundColor` → `AppColors.getSurfaceContainer(context)`
/// - `getAccentColor` → `AppColors.getPrimary(context)`
/// - `getDividerColor` → `AppColors.getOutline(context)`
/// - `getBottomSheetColor` → `AppColors.getSurface(context)`
class DiaryStyles {
  DiaryStyles._();

  // ========================================================================
  // 颜色方法 - 为了向后兼容保留，推荐直接使用 AppColors
  // ========================================================================

  /// 获取日记页面背景色
  /// @deprecated 使用 `AppColors.getBackground(context)` 替代
  static Color getBackgroundColor(BuildContext context) =>
      AppColors.getBackground(context);

  /// 获取日记卡片背景色
  /// @deprecated 使用 `AppColors.getSurface(context)` 替代
  static Color getCardBackgroundColor(BuildContext context) =>
      AppColors.getSurface(context);

  /// 获取卡片阴影
  /// @deprecated 使用 `AppShadows.getCardShadow(context)` 替代
  static List<BoxShadow> getCardShadow(BuildContext context) =>
      AppShadows.getCardShadow(context);

  /// 获取主要文本颜色
  /// @deprecated 使用 `AppColors.getOnSurface(context)` 替代
  static Color getPrimaryTextColor(BuildContext context) =>
      AppColors.getOnSurface(context);

  /// 获取次要文本颜色
  /// @deprecated 使用 `AppColors.getOnSurfaceVariant(context)` 替代
  static Color getSecondaryTextColor(BuildContext context) =>
      AppColors.getOnSurfaceVariant(context);

  /// 获取时间文本颜色
  static Color getTimeTextColor(BuildContext context) =>
      AppColors.getOnSurfaceVariant(context).withValues(alpha: 0.8);

  /// 获取输入框背景颜色
  /// @deprecated 使用 `AppColors.getSurfaceContainer(context)` 替代
  static Color getInputBackgroundColor(BuildContext context) =>
      AppColors.getSurfaceContainer(context);

  /// 获取强调色
  /// @deprecated 使用 `AppColors.getPrimary(context)` 替代
  static Color getAccentColor(BuildContext context) =>
      AppColors.getPrimary(context);

  /// 获取分割线颜色
  /// @deprecated 使用 `AppColors.getOutline(context)` 替代
  static Color getDividerColor(BuildContext context) =>
      AppColors.getOutline(context);

  /// 获取底部抽屉背景色
  /// @deprecated 使用 `AppColors.getSurface(context)` 替代
  static Color getBottomSheetColor(BuildContext context) =>
      AppColors.getSurface(context);

  // ========================================================================
  // 标签样式 - 日记模块独有
  // ========================================================================

  /// 获取标签背景颜色
  static Color getTagBackgroundColor(BuildContext context) =>
      AppColors.getPrimary(context).withValues(alpha: 0.12);

  /// 获取标签文本颜色
  static Color getTagTextColor(BuildContext context) =>
      AppColors.getPrimary(context);

  /// 获取标签装饰
  static BoxDecoration getTagDecoration(BuildContext context) {
    return BoxDecoration(
      color: getTagBackgroundColor(context),
      borderRadius: BorderRadius.circular(Dimensions.radiusL),
    );
  }

  /// 获取标签文本样式
  static TextStyle getTagTextStyle(BuildContext context) {
    return AppTypography.chipText.copyWith(color: getTagTextColor(context));
  }

  /// 获取标签内边距
  static EdgeInsets getTagPadding() => const EdgeInsets.symmetric(
    horizontal: Dimensions.spacingS,
    vertical: Dimensions.spacingXxs,
  );

  // ========================================================================
  // 卡片样式
  // ========================================================================

  /// 获取日记卡片装饰
  static BoxDecoration getCardDecoration(BuildContext context) {
    return BoxDecoration(
      color: AppColors.getSurface(context),
      borderRadius: BorderRadius.circular(Dimensions.radiusM),
      boxShadow: AppShadows.getCardShadow(context),
      border: Border.all(color: AppColors.getOutline(context), width: 1),
    );
  }

  /// 获取日记卡片内边距
  static EdgeInsets getCardPadding() => Dimensions.paddingCard;

  /// 获取日记项目外边距
  static EdgeInsets getDiaryItemMargin() => const EdgeInsets.symmetric(
    horizontal: Dimensions.spacingM,
    vertical: Dimensions.spacingS,
  );

  // ========================================================================
  // 文本样式
  // ========================================================================

  /// 获取日记标题文本样式
  static TextStyle getTitleTextStyle(BuildContext context) {
    return AppTypography.titleMedium.copyWith(
      color: AppColors.getOnSurface(context),
    );
  }

  /// 获取日记内容文本样式
  static TextStyle getContentTextStyle(BuildContext context) {
    return AppTypography.bodyMedium.copyWith(
      color: AppColors.getOnSurface(context),
    );
  }

  /// 获取日记时间文本样式
  static TextStyle getTimeTextStyle(BuildContext context) {
    return AppTypography.captionText.copyWith(color: getTimeTextColor(context));
  }

  // ========================================================================
  // 底部表单样式
  // ========================================================================

  /// 获取底部表单装饰
  static BoxDecoration getBottomSheetDecoration(BuildContext context) {
    return BoxDecoration(
      color: AppColors.getSurface(context),
      borderRadius: const BorderRadius.vertical(
        top: Radius.circular(Dimensions.radiusL),
      ),
      boxShadow: AppShadows.getBottomSheetShadow(context),
    );
  }

  /// 获取底部表单形状
  static RoundedRectangleBorder getBottomSheetShape() {
    return const RoundedRectangleBorder(
      borderRadius: BorderRadius.vertical(
        top: Radius.circular(Dimensions.radiusL),
      ),
    );
  }

  /// 获取底部表单内边距
  static EdgeInsets getBottomSheetPadding() => const EdgeInsets.fromLTRB(
    Dimensions.spacingM,
    Dimensions.spacingM,
    Dimensions.spacingM,
    Dimensions.spacingXxl,
  );

  // ========================================================================
  // 浮动按钮样式
  // ========================================================================

  /// 获取浮动按钮颜色
  /// @deprecated 使用 `AppColors.getPrimary(context)` 替代
  static Color getFabColor(BuildContext context) =>
      AppColors.getPrimary(context);

  /// 获取浮动按钮图标颜色
  static Color getFabIconColor(BuildContext context) => Colors.white;
}
