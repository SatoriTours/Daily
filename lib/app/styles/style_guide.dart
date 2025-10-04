/// 统一风格指南
///
/// 定义应用的整体设计语言和风格规范，确保UI一致性
library;

import 'package:flutter/material.dart';
import 'package:daily_satori/app/styles/index.dart';

/// 应用风格指南
///
/// 提供统一的风格应用方法，遵循现代APP设计原则
class StyleGuide {
  // 私有构造函数，防止实例化
  StyleGuide._();

  /// 获取页面容器装饰
  static BoxDecoration getPageContainerDecoration(BuildContext context) {
    return BoxDecoration(
      color: AppColors.getSurface(context),
      borderRadius: BorderRadius.circular(Dimensions.radiusM),
    );
  }

  /// 获取卡片装饰
  static BoxDecoration getCardDecoration(BuildContext context) {
    return BoxDecoration(
      color: AppColors.getSurface(context),
      borderRadius: BorderRadius.circular(Dimensions.radiusM),
      boxShadow: [
        BoxShadow(
          color: Colors.black.withValues(alpha: Opacities.extraLow),
          blurRadius: 8,
          offset: const Offset(0, 2),
        ),
      ],
    );
  }

  /// 获取列表项装饰
  static BoxDecoration getListItemDecoration(BuildContext context) {
    return BoxDecoration(
      color: AppColors.getSurface(context),
      borderRadius: BorderRadius.circular(Dimensions.radiusS),
    );
  }

  /// 获取输入框装饰
  static InputDecoration getInputDecoration(
    BuildContext context, {
    String? hintText,
    Widget? prefixIcon,
    Widget? suffixIcon,
    String? errorText,
  }) {
    return InputDecoration(
      hintText: hintText,
      hintStyle: AppTypography.getThemedStyle(context, AppTypography.bodyMedium,
          lightColor: AppColors.onSurfaceVariant, darkColor: AppColors.onSurfaceVariantDark),
      prefixIcon: prefixIcon,
      suffixIcon: suffixIcon,
      errorText: errorText,
      filled: true,
      fillColor: AppColors.getSurfaceContainer(context),
      border: OutlineInputBorder(
        borderRadius: BorderRadius.circular(Dimensions.radiusS),
        borderSide: BorderSide(color: AppColors.getOutline(context)),
      ),
      enabledBorder: OutlineInputBorder(
        borderRadius: BorderRadius.circular(Dimensions.radiusS),
        borderSide: BorderSide(color: AppColors.getOutline(context)),
      ),
      focusedBorder: OutlineInputBorder(
        borderRadius: BorderRadius.circular(Dimensions.radiusS),
        borderSide: BorderSide(color: AppColors.getPrimary(context), width: 2),
      ),
      errorBorder: OutlineInputBorder(
        borderRadius: BorderRadius.circular(Dimensions.radiusS),
        borderSide: BorderSide(color: AppColors.getError(context)),
      ),
      contentPadding: Dimensions.paddingInput,
    );
  }

  /// 获取按钮样式
  static ButtonStyle getPrimaryButtonStyle(BuildContext context) {
    return ButtonStyles.getPrimaryStyle(context);
  }

  static ButtonStyle getSecondaryButtonStyle(BuildContext context) {
    return ButtonStyles.getSecondaryStyle(context);
  }

  static ButtonStyle getOutlinedButtonStyle(BuildContext context) {
    return ButtonStyles.getOutlinedStyle(context);
  }

  static ButtonStyle getTextButtonStyle(BuildContext context) {
    return ButtonStyles.getTextStyle(context);
  }

  /// 获取标签装饰
  static BoxDecoration getChipDecoration(BuildContext context, {bool selected = false}) {
    return BoxDecoration(
      color: selected ? AppColors.getPrimary(context) : AppColors.getSurfaceContainer(context),
      borderRadius: BorderRadius.circular(Dimensions.radiusCircular),
      border: selected ? null : Border.all(color: AppColors.getOutline(context)),
    );
  }

  /// 获取标签文本样式
  static TextStyle getChipTextStyle(BuildContext context, {bool selected = false}) {
    return AppTypography.chipText.copyWith(
      color: selected ? Colors.white : AppColors.getOnSurface(context),
    );
  }

  /// 获取分隔线
  static Widget getDivider(BuildContext context) {
    return Divider(
      height: Dimensions.dividerHeight,
      thickness: Dimensions.dividerHeight,
      color: AppColors.getOutline(context),
      indent: Dimensions.dividerIndent,
      endIndent: Dimensions.dividerIndent,
    );
  }

  /// 获取空状态组件
  static Widget getEmptyState(
    BuildContext context, {
    required String message,
    IconData? icon,
    Widget? action,
  }) {
    return Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Icon(
            icon ?? Icons.inbox_outlined,
            size: Dimensions.iconSizeXxl,
            color: AppColors.getOnSurfaceVariant(context),
          ),
          Dimensions.verticalSpacerM,
          Text(
            message,
            style: AppTypography.getThemedStyle(context, AppTypography.bodyMedium,
                lightColor: AppColors.onSurfaceVariant, darkColor: AppColors.onSurfaceVariantDark),
            textAlign: TextAlign.center,
          ),
          if (action != null) ...[
            Dimensions.verticalSpacerM,
            action,
          ],
        ],
      ),
    );
  }

  /// 获取加载状态组件
  static Widget getLoadingState(BuildContext context, {String message = '加载中...'}) {
    return Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          SizedBox(
            width: Dimensions.iconSizeXl,
            height: Dimensions.iconSizeXl,
            child: CircularProgressIndicator(
              strokeWidth: 2,
              valueColor: AlwaysStoppedAnimation<Color>(AppColors.getPrimary(context)),
            ),
          ),
          Dimensions.verticalSpacerM,
          Text(
            message,
            style: AppTypography.getThemedStyle(context, AppTypography.bodyMedium,
                lightColor: AppColors.onSurfaceVariant, darkColor: AppColors.onSurfaceVariantDark),
          ),
        ],
      ),
    );
  }

  /// 获取错误状态组件
  static Widget getErrorState(
    BuildContext context, {
    required String message,
    VoidCallback? onRetry,
  }) {
    return Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Icon(
            Icons.error_outline,
            size: Dimensions.iconSizeXxl,
            color: AppColors.getError(context),
          ),
          Dimensions.verticalSpacerM,
          Text(
            message,
            style: AppTypography.getThemedStyle(context, AppTypography.bodyMedium,
                lightColor: AppColors.onSurfaceVariant, darkColor: AppColors.onSurfaceVariantDark),
            textAlign: TextAlign.center,
          ),
          if (onRetry != null) ...[
            Dimensions.verticalSpacerM,
            ElevatedButton(
              onPressed: onRetry,
              style: getPrimaryButtonStyle(context),
              child: const Text('重试'),
            ),
          ],
        ],
      ),
    );
  }

  /// 获取页面标题样式
  static TextStyle getPageTitleStyle(BuildContext context) {
    return AppTypography.headingMedium;
  }

  /// 获取页面副标题样式
  static TextStyle getPageSubtitleStyle(BuildContext context) {
    return AppTypography.bodyMedium.copyWith(
      color: AppColors.getOnSurfaceVariant(context),
    );
  }

  /// 获取列表项标题样式
  static TextStyle getListItemTitleStyle(BuildContext context) {
    return AppTypography.listItemTitle;
  }

  /// 获取列表项副标题样式
  static TextStyle getListItemSubtitleStyle(BuildContext context) {
    return AppTypography.listItemSubtitle.copyWith(
      color: AppColors.getOnSurfaceVariant(context),
    );
  }

  /// 获取卡片标题样式
  static TextStyle getCardTitleStyle(BuildContext context) {
    return AppTypography.cardTitle;
  }

  /// 获取卡片内容样式
  static TextStyle getCardContentStyle(BuildContext context) {
    return AppTypography.cardContent.copyWith(
      color: AppColors.getOnSurfaceVariant(context),
    );
  }

  /// 获取标准页面布局
  static Widget getStandardPageLayout({
    required BuildContext context,
    required Widget child,
    bool hasAppBar = true,
    bool hasPadding = true,
  }) {
    return Scaffold(
      backgroundColor: AppColors.getBackground(context),
      body: SafeArea(
        child: hasPadding
            ? Padding(
                padding: Dimensions.paddingPage,
                child: child,
              )
            : child,
      ),
    );
  }

  /// 获取标准列表布局
  static Widget getStandardListLayout({
    required BuildContext context,
    required List<Widget> children,
    EdgeInsets? padding,
  }) {
    return ListView.separated(
      padding: padding ?? Dimensions.paddingPage,
      itemCount: children.length,
      separatorBuilder: (context, index) => Dimensions.verticalSpacerS,
      itemBuilder: (context, index) => children[index],
    );
  }

  /// 获取标准网格布局
  static Widget getStandardGridLayout({
    required BuildContext context,
    required List<Widget> children,
    int crossAxisCount = 2,
    double childAspectRatio = 1.0,
    EdgeInsets? padding,
  }) {
    return GridView.count(
      padding: padding ?? Dimensions.paddingPage,
      crossAxisCount: crossAxisCount,
      childAspectRatio: childAspectRatio,
      crossAxisSpacing: Dimensions.spacingM,
      mainAxisSpacing: Dimensions.spacingM,
      children: children,
    );
  }
}