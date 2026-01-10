import 'package:flutter/material.dart';
import 'package:daily_satori/app/styles/base/colors.dart';
import 'package:daily_satori/app/styles/base/dimensions.dart';
import 'package:daily_satori/app/styles/base/typography.dart';

/// 列表样式类
///
/// 提供应用中各种列表的样式定义，所有方法使用主题感知颜色。
class ListStyles {
  ListStyles._();

  // ========================================================================
  // 主题配置
  // ========================================================================

  /// 获取标准列表磁贴主题
  static ListTileThemeData getListTileTheme(BuildContext context) {
    return ListTileThemeData(
      contentPadding: Dimensions.paddingListItem,
      minLeadingWidth: 24,
      minVerticalPadding: 12,
      dense: false,
      style: ListTileStyle.list,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(Dimensions.radiusS),
      ),
      iconColor: AppColors.getOnSurfaceVariant(context),
      textColor: AppColors.getOnSurface(context),
      selectedColor: AppColors.getPrimary(context),
      selectedTileColor: AppColors.getPrimary(context).withValues(alpha: 0.1),
    );
  }

  // ========================================================================
  // ========================================================================

  /// 获取列表项容器装饰
  static BoxDecoration getListItemDecoration(
    BuildContext context, {
    bool isSelected = false,
    bool hasBorder = false,
  }) {
    return BoxDecoration(
      color: isSelected
          ? AppColors.getPrimary(context).withValues(alpha: 0.1)
          : Colors.transparent,
      borderRadius: BorderRadius.circular(Dimensions.radiusS),
      border: hasBorder
          ? Border.all(color: AppColors.getOutline(context), width: 1)
          : null,
    );
  }

  /// 获取列表组标题容器装饰
  static BoxDecoration getGroupTitleDecoration(BuildContext context) {
    return BoxDecoration(
      color: AppColors.getSurfaceContainer(context),
      borderRadius: BorderRadius.circular(Dimensions.radiusS),
    );
  }

  // ========================================================================
  // 分隔线
  // ========================================================================

  /// 获取列表分隔线
  static Widget getDivider(BuildContext context, {double indent = 0}) {
    return Divider(
      color: AppColors.getOutline(context),
      height: 1,
      thickness: 1,
      indent: indent,
      endIndent: indent,
    );
  }

  // ========================================================================
  // 文本样式
  // ========================================================================

  /// 获取列表组标题样式
  static TextStyle getGroupTitleStyle(BuildContext context) {
    return AppTypography.labelLarge.copyWith(
      color: AppColors.getOnSurfaceVariant(context),
      fontWeight: FontWeight.w500,
      letterSpacing: 0.5,
    );
  }

  /// 获取列表项标题样式
  static TextStyle getListItemTitleStyle(BuildContext context) {
    return AppTypography.listItemTitle.copyWith(
      color: AppColors.getOnSurface(context),
    );
  }

  /// 获取列表项副标题样式
  static TextStyle getListItemSubtitleStyle(BuildContext context) {
    return AppTypography.listItemSubtitle.copyWith(
      color: AppColors.getOnSurfaceVariant(context),
    );
  }

  // ========================================================================
  // 内边距和尺寸
  // ========================================================================

  /// 获取列表组标题内边距
  static EdgeInsets getGroupTitlePadding() => const EdgeInsets.symmetric(
    horizontal: Dimensions.spacingM,
    vertical: Dimensions.spacingS,
  );

  /// 获取列表单元格内边距
  static EdgeInsets getListCellPadding() => Dimensions.paddingListItem;

  /// 获取列表单元格外边距
  static EdgeInsets getListCellMargin() => Dimensions.marginListItem;

  /// 获取列表项高度 - 标准
  static double getListItemHeight() => Dimensions.listItemHeight;

  /// 获取列表项高度 - 紧凑
  static double getListItemHeightDense() => Dimensions.listItemHeightSmall;

  // ========================================================================
  // 组件构建器
  // ========================================================================

  /// 创建列表标题行组件
  static Widget createListHeader(
    BuildContext context, {
    required String title,
    VoidCallback? onAction,
    String actionText = '',
    EdgeInsetsGeometry? padding,
  }) {
    return Padding(
      padding:
          padding ??
          const EdgeInsets.only(
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
                padding: const EdgeInsets.symmetric(
                  horizontal: Dimensions.spacingXs,
                  vertical: Dimensions.spacingXxs,
                ),
                child: Text(
                  actionText,
                  style: AppTypography.labelMedium.copyWith(
                    color: AppColors.getPrimary(context),
                  ),
                ),
              ),
            ),
          ],
        ],
      ),
    );
  }
}
