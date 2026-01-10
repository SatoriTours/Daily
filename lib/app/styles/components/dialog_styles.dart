import 'package:flutter/material.dart';
import 'package:daily_satori/app/styles/base/colors.dart';
import 'package:daily_satori/app/styles/base/dimensions.dart';
import 'package:daily_satori/app/styles/base/typography.dart';
import 'package:daily_satori/app/styles/base/shadows.dart';

/// 对话框样式类
/// 提供应用中各种对话框的样式定义，遵循 shadcn/ui 的设计风格
class DialogStyles {
  // 私有构造函数，防止实例化
  DialogStyles._();

  /// 获取对话框容器装饰
  static BoxDecoration getDialogDecoration(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;

    return BoxDecoration(
      color: isDark ? AppColors.surfaceDark : AppColors.surface,
      borderRadius: BorderRadius.circular(Dimensions.radiusL),
      boxShadow: AppShadows.getDialogShadow(context),
    );
  }

  /// 获取底部表单装饰
  static BoxDecoration getBottomSheetDecoration(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;

    return BoxDecoration(
      color: isDark ? AppColors.surfaceDark : AppColors.surface,
      borderRadius: const BorderRadius.vertical(
        top: Radius.circular(Dimensions.radiusL),
      ),
      boxShadow: AppShadows.getBottomSheetShadow(context),
    );
  }

  /// 获取对话框标题样式
  static TextStyle getDialogTitleStyle(BuildContext context) {
    return AppTypography.getThemedStyle(
      context,
      AppTypography.dialogTitle,
      lightColor: AppColors.onSurface,
      darkColor: AppColors.onSurfaceDark,
    );
  }

  /// 获取对话框内容样式
  static TextStyle getDialogContentStyle(BuildContext context) {
    return AppTypography.getThemedStyle(
      context,
      AppTypography.dialogContent,
      lightColor: AppColors.onSurfaceVariant,
      darkColor: AppColors.onSurfaceVariantDark,
    );
  }

  /// 获取对话框内边距
  static EdgeInsets getDialogPadding() => Dimensions.paddingDialog;

  /// 获取对话框内容内边距
  static EdgeInsets getDialogContentPadding() => const EdgeInsets.symmetric(
    horizontal: Dimensions.spacingM,
    vertical: Dimensions.spacingM,
  );

  /// 获取对话框操作区内边距
  static EdgeInsets getDialogActionsPadding() => const EdgeInsets.fromLTRB(
    Dimensions.spacingS,
    0,
    Dimensions.spacingM,
    Dimensions.spacingM,
  );

  /// 获取底部表单内边距
  static EdgeInsets getBottomSheetPadding() => const EdgeInsets.fromLTRB(
    Dimensions.spacingM,
    Dimensions.spacingM,
    Dimensions.spacingM,
    Dimensions.spacingXxl,
  );

  /// 获取对话框标题与内容间距
  static SizedBox getTitleContentSpacer() => Dimensions.verticalSpacerM;

  /// 获取对话框内容与操作区间距
  static SizedBox getContentActionsSpacer() => Dimensions.verticalSpacerM;

  /// 获取对话框形状
  static ShapeBorder getDialogShape() => RoundedRectangleBorder(
    borderRadius: BorderRadius.circular(Dimensions.radiusL),
  );

  /// 获取底部表单形状
  static ShapeBorder getBottomSheetShape() => const RoundedRectangleBorder(
    borderRadius: BorderRadius.vertical(
      top: Radius.circular(Dimensions.radiusL),
    ),
  );

  /// 创建标准对话框主题
  static DialogTheme getDialogTheme(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;

    return DialogTheme(
      backgroundColor: isDark ? AppColors.surfaceDark : AppColors.surface,
      elevation: 0,
      shape: getDialogShape(),
      titleTextStyle: getDialogTitleStyle(context),
      contentTextStyle: getDialogContentStyle(context),
      actionsPadding: getDialogActionsPadding(),
    );
  }

  /// 创建底部表单标题行
  static Widget createBottomSheetTitle(
    BuildContext context, {
    required String title,
    VoidCallback? onClose,
  }) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(
        Dimensions.spacingM,
        Dimensions.spacingM,
        Dimensions.spacingS,
        Dimensions.spacingS,
      ),
      child: Row(
        children: [
          Text(title, style: getDialogTitleStyle(context)),
          const Spacer(),
          if (onClose != null)
            IconButton(
              icon: const Icon(Icons.close),
              onPressed: onClose,
              padding: const EdgeInsets.all(Dimensions.spacingXs),
              iconSize: Dimensions.iconSizeM,
              color: Theme.of(context).brightness == Brightness.dark
                  ? AppColors.onSurfaceVariantDark
                  : AppColors.onSurfaceVariant,
              splashRadius: 24,
            ),
        ],
      ),
    );
  }

  /// 创建分隔线
  static Widget createDivider(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;

    return Divider(
      color: isDark ? AppColors.outlineDark : AppColors.outline,
      height: 1,
      thickness: 1,
    );
  }

  /// 获取抽屉内容最大宽度
  static double getDrawerMaxWidth(BuildContext context) =>
      MediaQuery.of(context).size.width * 0.85;

  /// 获取底部表单最大高度
  static double getBottomSheetMaxHeight(BuildContext context) =>
      MediaQuery.of(context).size.height * 0.85;
}
