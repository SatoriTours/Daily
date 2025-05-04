import 'package:flutter/material.dart';
import 'package:daily_satori/app/styles/base/colors.dart';
import 'package:daily_satori/app/styles/base/dimensions.dart';
import 'package:daily_satori/app/styles/base/typography.dart';

/// 列表样式类
/// 提供应用中各种列表的样式定义，遵循 shadcn/ui 的设计风格
class ListStyles {
  // 私有构造函数，防止实例化
  ListStyles._();

  /// 获取标准列表磁贴样式
  static ListTileThemeData getListTileTheme(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;

    return ListTileThemeData(
      contentPadding: Dimensions.paddingListItem,
      minLeadingWidth: 24,
      minVerticalPadding: 12,
      dense: false,
      style: ListTileStyle.list,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(Dimensions.radiusS)),
      iconColor: isDark ? AppColors.onSurfaceVariantDark : AppColors.onSurfaceVariant,
      textColor: isDark ? AppColors.onSurfaceDark : AppColors.onSurface,
      selectedColor: isDark ? AppColors.primaryLight : AppColors.primary,
      selectedTileColor:
          isDark ? AppColors.primaryLight.withValues(alpha: 0.1) : AppColors.primary.withValues(alpha: 0.1),
    );
  }

  /// 获取列表项容器装饰
  static BoxDecoration getListItemDecoration(BuildContext context, {bool isSelected = false, bool hasBorder = false}) {
    final isDark = Theme.of(context).brightness == Brightness.dark;

    return BoxDecoration(
      color:
          isSelected
              ? (isDark ? AppColors.primaryLight.withValues(alpha: 0.1) : AppColors.primary.withValues(alpha: 0.1))
              : Colors.transparent,
      borderRadius: BorderRadius.circular(Dimensions.radiusS),
      border: hasBorder ? Border.all(color: isDark ? AppColors.outlineDark : AppColors.outline, width: 1) : null,
    );
  }

  /// 获取列表分隔线
  static Widget getDivider(BuildContext context, {double indent = 0}) {
    final isDark = Theme.of(context).brightness == Brightness.dark;

    return Divider(
      color: isDark ? AppColors.outlineDark : AppColors.outline,
      height: 1,
      thickness: 1,
      indent: indent,
      endIndent: indent,
    );
  }

  /// 获取列表组标题样式
  static TextStyle getGroupTitleStyle(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;

    return AppTypography.labelLarge.copyWith(
      color: isDark ? AppColors.onSurfaceVariantDark : AppColors.onSurfaceVariant,
      fontWeight: FontWeight.w500,
      letterSpacing: 0.5,
    );
  }

  /// 获取列表组标题容器装饰
  static BoxDecoration getGroupTitleDecoration(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;

    return BoxDecoration(
      color: isDark ? AppColors.surfaceContainerDark : AppColors.surfaceContainer,
      borderRadius: BorderRadius.circular(Dimensions.radiusS),
    );
  }

  /// 获取列表组标题内边距
  static EdgeInsets getGroupTitlePadding() =>
      const EdgeInsets.symmetric(horizontal: Dimensions.spacingM, vertical: Dimensions.spacingS);

  /// 获取列表项标题样式
  static TextStyle getListItemTitleStyle(BuildContext context) {
    return AppTypography.getThemedStyle(
      context,
      AppTypography.listItemTitle,
      lightColor: AppColors.onSurface,
      darkColor: AppColors.onSurfaceDark,
    );
  }

  /// 获取列表项副标题样式
  static TextStyle getListItemSubtitleStyle(BuildContext context) {
    return AppTypography.getThemedStyle(
      context,
      AppTypography.listItemSubtitle,
      lightColor: AppColors.onSurfaceVariant,
      darkColor: AppColors.onSurfaceVariantDark,
    );
  }

  /// 获取列表单元格内边距
  static EdgeInsets getListCellPadding() => Dimensions.paddingListItem;

  /// 获取列表单元格外边距
  static EdgeInsets getListCellMargin() => Dimensions.marginListItem;

  /// 获取列表项高度 - 标准
  static double getListItemHeight() => Dimensions.listItemHeight;

  /// 获取列表项高度 - 紧凑
  static double getListItemHeightDense() => Dimensions.listItemHeightSmall;

  /// 创建用于列表标题的行组件
  static Widget createListHeader(
    BuildContext context, {
    required String title,
    VoidCallback? onAction,
    String actionText = '',
    EdgeInsetsGeometry? padding,
  }) {
    final isDark = Theme.of(context).brightness == Brightness.dark;

    return Padding(
      padding:
          padding ??
          EdgeInsets.only(
            left: Dimensions.spacingM,
            right: Dimensions.spacingM,
            top: Dimensions.spacingM,
            bottom: Dimensions.spacingS,
          ),
      child: Row(
        children: [
          Text(title, style: getGroupTitleStyle(context)),
          if (onAction != null && actionText.isNotEmpty) ...[
            const Spacer(),
            InkWell(
              onTap: onAction,
              borderRadius: BorderRadius.circular(Dimensions.radiusS),
              child: Padding(
                padding: const EdgeInsets.symmetric(horizontal: Dimensions.spacingXs, vertical: Dimensions.spacingXxs),
                child: Text(
                  actionText,
                  style: AppTypography.labelMedium.copyWith(color: isDark ? AppColors.primaryLight : AppColors.primary),
                ),
              ),
            ),
          ],
        ],
      ),
    );
  }
}
