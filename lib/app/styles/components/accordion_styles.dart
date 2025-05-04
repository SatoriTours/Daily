import 'package:flutter/material.dart';
import 'package:daily_satori/app/styles/base/colors.dart';
import 'package:daily_satori/app/styles/base/dimensions.dart';
import 'package:daily_satori/app/styles/base/typography.dart';

/// 手风琴组件样式类
/// 提供应用中折叠面板/手风琴组件的样式定义，参考shadcn/ui的设计风格
class AccordionStyles {
  // 私有构造函数，防止实例化
  AccordionStyles._();

  /// 获取手风琴容器装饰
  static BoxDecoration getContainerDecoration(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;

    return BoxDecoration(
      color: isDark ? AppColors.surfaceDark : AppColors.surface,
      border: Border.all(color: isDark ? AppColors.outlineDark : AppColors.outline, width: 1.0),
      borderRadius: BorderRadius.circular(Dimensions.radiusS),
    );
  }

  /// 获取无边框手风琴容器装饰
  static BoxDecoration getBorderlessContainerDecoration(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;

    return BoxDecoration(
      color: isDark ? AppColors.surfaceDark : AppColors.surface,
      borderRadius: BorderRadius.circular(Dimensions.radiusS),
    );
  }

  /// 获取手风琴项目装饰
  static BoxDecoration getItemDecoration(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;

    return BoxDecoration(
      color: Colors.transparent,
      border: Border(bottom: BorderSide(color: isDark ? AppColors.outlineDark : AppColors.outline, width: 1.0)),
    );
  }

  /// 获取最后一个手风琴项目装饰（无底部边框）
  static BoxDecoration getLastItemDecoration() {
    return const BoxDecoration(color: Colors.transparent);
  }

  /// 获取手风琴触发器容器内边距
  static EdgeInsets getTriggerPadding() =>
      const EdgeInsets.symmetric(horizontal: Dimensions.spacingM, vertical: Dimensions.spacingS);

  /// 获取手风琴内容容器内边距
  static EdgeInsets getContentPadding() =>
      const EdgeInsets.fromLTRB(Dimensions.spacingM, 0, Dimensions.spacingM, Dimensions.spacingM);

  /// 获取手风琴触发器标题样式
  static TextStyle getTriggerTextStyle(BuildContext context) {
    return AppTypography.getThemedStyle(
      context,
      AppTypography.titleMedium,
      lightColor: AppColors.onSurface,
      darkColor: AppColors.onSurfaceDark,
    );
  }

  /// 获取手风琴内容文本样式
  static TextStyle getContentTextStyle(BuildContext context) {
    return AppTypography.getThemedStyle(
      context,
      AppTypography.bodyMedium,
      lightColor: AppColors.onSurfaceVariant,
      darkColor: AppColors.onSurfaceVariantDark,
    );
  }

  /// 获取手风琴图标尺寸
  static double getIconSize() => Dimensions.iconSizeM;

  /// 获取手风琴图标颜色
  static Color getIconColor(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    return isDark ? AppColors.onSurfaceVariantDark : AppColors.onSurfaceVariant;
  }

  /// 获取手风琴动画持续时间
  static Duration getAnimationDuration() => const Duration(milliseconds: 200);

  /// 创建手风琴触发器组件
  static Widget createTrigger(
    BuildContext context, {
    required String title,
    required bool isExpanded,
    required VoidCallback onTap,
    Widget? icon,
  }) {
    final isDark = Theme.of(context).brightness == Brightness.dark;

    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(Dimensions.radiusXs),
      child: Padding(
        padding: getTriggerPadding(),
        child: Row(
          children: [
            Expanded(child: Text(title, style: getTriggerTextStyle(context))),
            icon ??
                AnimatedRotation(
                  duration: getAnimationDuration(),
                  turns: isExpanded ? 0.5 : 0.0,
                  child: Icon(
                    Icons.keyboard_arrow_down,
                    size: getIconSize(),
                    color: isDark ? AppColors.onSurfaceVariantDark : AppColors.onSurfaceVariant,
                  ),
                ),
          ],
        ),
      ),
    );
  }

  /// 创建手风琴内容包装器
  static Widget createContentWrapper({required Widget child, required bool isExpanded}) {
    return AnimatedSize(
      duration: getAnimationDuration(),
      curve: Curves.easeInOut,
      child: isExpanded ? Padding(padding: getContentPadding(), child: child) : const SizedBox.shrink(),
    );
  }
}
