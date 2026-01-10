import 'package:flutter/material.dart';
import 'package:daily_satori/app/styles/base/colors.dart';
import 'package:daily_satori/app/styles/base/dimensions.dart';
import 'package:daily_satori/app/styles/base/typography.dart';

/// 按钮样式类
/// 提供应用中各种按钮的样式定义，遵循 shadcn/ui 的设计风格
class ButtonStyles {
  // 私有构造函数，防止实例化
  ButtonStyles._();

  /// 获取标准按钮尺寸
  static Size getStandardSize() =>
      const Size.fromHeight(Dimensions.buttonHeight);

  /// 获取小按钮尺寸
  static Size getSmallSize() =>
      const Size.fromHeight(Dimensions.buttonHeightSmall);

  /// 获取主要按钮样式
  static ButtonStyle getPrimaryStyle(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;

    return ElevatedButton.styleFrom(
      backgroundColor: isDark ? AppColors.primaryLight : AppColors.primary,
      foregroundColor: Colors.white,
      elevation: 0,
      padding: Dimensions.paddingButton,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(Dimensions.radiusS),
      ),
      minimumSize: getStandardSize(),
      textStyle: AppTypography.buttonText,
      shadowColor: Colors.transparent,
    );
  }

  /// 获取次要按钮样式
  static ButtonStyle getSecondaryStyle(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;

    return ElevatedButton.styleFrom(
      backgroundColor: isDark
          ? Colors.white.withValues(alpha: 0.1)
          : AppColors.primary.withValues(alpha: 0.1),
      foregroundColor: isDark ? Colors.white : AppColors.primary,
      elevation: 0,
      padding: Dimensions.paddingButton,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(Dimensions.radiusS),
      ),
      minimumSize: getStandardSize(),
      textStyle: AppTypography.buttonText,
      shadowColor: Colors.transparent,
    );
  }

  /// 获取轮廓按钮样式
  static ButtonStyle getOutlinedStyle(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;

    return OutlinedButton.styleFrom(
      foregroundColor: isDark ? AppColors.primaryLight : AppColors.primary,
      side: BorderSide(
        color: isDark ? AppColors.primaryLight : AppColors.primary,
        width: 1.5,
      ),
      padding: Dimensions.paddingButton,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(Dimensions.radiusS),
      ),
      minimumSize: getStandardSize(),
      textStyle: AppTypography.buttonText,
    );
  }

  /// 获取文本按钮样式
  static ButtonStyle getTextStyle(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;

    return TextButton.styleFrom(
      foregroundColor: isDark ? AppColors.primaryLight : AppColors.primary,
      padding: Dimensions.paddingButton,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(Dimensions.radiusS),
      ),
      minimumSize: getStandardSize(),
      textStyle: AppTypography.buttonText,
    );
  }

  /// 获取危险按钮样式
  static ButtonStyle getDangerStyle(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;

    return ElevatedButton.styleFrom(
      backgroundColor: isDark ? AppColors.errorDark : AppColors.error,
      foregroundColor: Colors.white,
      elevation: 0,
      padding: Dimensions.paddingButton,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(Dimensions.radiusS),
      ),
      minimumSize: getStandardSize(),
      textStyle: AppTypography.buttonText,
      shadowColor: Colors.transparent,
    );
  }

  /// 获取成功按钮样式
  static ButtonStyle getSuccessStyle(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;

    return ElevatedButton.styleFrom(
      backgroundColor: isDark ? AppColors.successDark : AppColors.success,
      foregroundColor: Colors.white,
      elevation: 0,
      padding: Dimensions.paddingButton,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(Dimensions.radiusS),
      ),
      minimumSize: getStandardSize(),
      textStyle: AppTypography.buttonText,
      shadowColor: Colors.transparent,
    );
  }

  /// 获取警告按钮样式
  static ButtonStyle getWarningStyle(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;

    return ElevatedButton.styleFrom(
      backgroundColor: isDark ? AppColors.warningDark : AppColors.warning,
      foregroundColor: isDark ? Colors.black87 : Colors.white,
      elevation: 0,
      padding: Dimensions.paddingButton,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(Dimensions.radiusS),
      ),
      minimumSize: getStandardSize(),
      textStyle: AppTypography.buttonText,
      shadowColor: Colors.transparent,
    );
  }

  /// 获取小型主要按钮样式
  static ButtonStyle getPrimarySmallStyle(BuildContext context) {
    final style = getPrimaryStyle(context);
    return style.copyWith(
      padding: WidgetStateProperty.all(Dimensions.paddingButtonSmall),
      minimumSize: WidgetStateProperty.all(getSmallSize()),
      textStyle: WidgetStateProperty.all(AppTypography.buttonTextSmall),
    );
  }

  /// 获取小型次要按钮样式
  static ButtonStyle getSecondarySmallStyle(BuildContext context) {
    final style = getSecondaryStyle(context);
    return style.copyWith(
      padding: WidgetStateProperty.all(Dimensions.paddingButtonSmall),
      minimumSize: WidgetStateProperty.all(getSmallSize()),
      textStyle: WidgetStateProperty.all(AppTypography.buttonTextSmall),
    );
  }

  /// 获取小型轮廓按钮样式
  static ButtonStyle getOutlinedSmallStyle(BuildContext context) {
    final style = getOutlinedStyle(context);
    return style.copyWith(
      padding: WidgetStateProperty.all(Dimensions.paddingButtonSmall),
      minimumSize: WidgetStateProperty.all(getSmallSize()),
      textStyle: WidgetStateProperty.all(AppTypography.buttonTextSmall),
    );
  }

  /// 获取小型文本按钮样式
  static ButtonStyle getTextSmallStyle(BuildContext context) {
    final style = getTextStyle(context);
    return style.copyWith(
      padding: WidgetStateProperty.all(Dimensions.paddingButtonSmall),
      minimumSize: WidgetStateProperty.all(getSmallSize()),
      textStyle: WidgetStateProperty.all(AppTypography.buttonTextSmall),
    );
  }

  /// 获取图标按钮样式
  static ButtonStyle getIconButtonStyle(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;

    return IconButton.styleFrom(
      foregroundColor: isDark ? AppColors.onSurfaceDark : AppColors.onSurface,
      backgroundColor: Colors.transparent,
      shape: const CircleBorder(),
      padding: Dimensions.paddingIconButton,
    );
  }
}
