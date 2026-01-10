import 'package:flutter/material.dart';
import 'package:daily_satori/app/styles/base/colors.dart';
import 'package:daily_satori/app/styles/base/dimensions.dart';

/// 应用边框样式
///
/// 提供统一的边框相关样式定义，所有方法使用主题感知颜色。
class AppBorders {
  AppBorders._();

  // ========================================================================
  // 基本边框
  // ========================================================================

  /// 获取基本边框
  static BorderSide getBaseBorder(BuildContext context, {double width = 1.0}) {
    return BorderSide(color: AppColors.getOutline(context), width: width);
  }

  /// 获取主题色边框
  static BorderSide getPrimaryBorder(
    BuildContext context, {
    double width = 1.0,
  }) {
    return BorderSide(color: AppColors.getPrimary(context), width: width);
  }

  /// 获取卡片边框
  static BorderSide getCardBorder(BuildContext context) {
    return BorderSide(color: AppColors.getOutline(context), width: 1.0);
  }

  /// 获取按钮边框 - 轮廓按钮
  static BorderSide getOutlinedButtonBorder(BuildContext context) {
    return BorderSide(color: AppColors.getPrimary(context), width: 1.5);
  }

  // ========================================================================
  // 输入框边框
  // ========================================================================

  /// 获取输入框边框 - 默认状态
  static InputBorder getInputBorder(BuildContext context) {
    return OutlineInputBorder(
      borderRadius: BorderRadius.circular(Dimensions.radiusS),
      borderSide: getBaseBorder(context),
    );
  }

  /// 获取输入框边框 - 聚焦状态
  static InputBorder getInputFocusedBorder(BuildContext context) {
    return OutlineInputBorder(
      borderRadius: BorderRadius.circular(Dimensions.radiusS),
      borderSide: getPrimaryBorder(context, width: 2.0),
    );
  }

  /// 获取输入框边框 - 禁用状态
  static InputBorder getInputDisabledBorder(BuildContext context) {
    return OutlineInputBorder(
      borderRadius: BorderRadius.circular(Dimensions.radiusS),
      borderSide: BorderSide(
        color: AppColors.getOutline(context).withValues(alpha: 0.5),
        width: 1.0,
      ),
    );
  }

  /// 获取输入框边框 - 错误状态
  static InputBorder getInputErrorBorder(BuildContext context) {
    return OutlineInputBorder(
      borderRadius: BorderRadius.circular(Dimensions.radiusS),
      borderSide: BorderSide(color: AppColors.getError(context), width: 1.5),
    );
  }

  // ========================================================================
  // 分隔线
  // ========================================================================

  /// 获取分隔线
  static Divider getDivider(
    BuildContext context, {
    double height = 1.0,
    double indent = 0.0,
  }) {
    return Divider(
      height: height,
      thickness: Dimensions.dividerHeight,
      color: AppColors.getOutline(context),
      indent: indent,
      endIndent: indent,
    );
  }

  /// 获取垂直分隔线
  static VerticalDivider getVerticalDivider(
    BuildContext context, {
    double width = 1.0,
    double indent = 0.0,
  }) {
    return VerticalDivider(
      width: width,
      thickness: Dimensions.dividerHeight,
      color: AppColors.getOutline(context),
      indent: indent,
      endIndent: indent,
    );
  }

  // ========================================================================
  // 装饰边框
  // ========================================================================

  /// 获取轻微边框装饰 - 适用于卡片、容器等
  static BoxDecoration getLightBorderDecoration(
    BuildContext context, {
    BorderRadius? borderRadius,
    Color? backgroundColor,
  }) {
    return BoxDecoration(
      color: backgroundColor ?? AppColors.getSurface(context),
      borderRadius: borderRadius ?? BorderRadius.circular(Dimensions.radiusS),
      border: Border.all(color: AppColors.getOutline(context), width: 1.0),
    );
  }

  /// 获取主题色边框装饰
  static BoxDecoration getPrimaryBorderDecoration(
    BuildContext context, {
    BorderRadius? borderRadius,
    Color? backgroundColor,
  }) {
    return BoxDecoration(
      color: backgroundColor ?? AppColors.getSurface(context),
      borderRadius: borderRadius ?? BorderRadius.circular(Dimensions.radiusS),
      border: Border.all(
        color: AppColors.getPrimary(context),
        width: Dimensions.borderWidthM,
      ),
    );
  }

  // ========================================================================
  // 单边边框
  // ========================================================================

  /// 获取顶部边框
  static Border getTopBorder(
    Color color, {
    double opacity = 0.3,
    double width = Dimensions.borderWidthXs,
  }) {
    return Border(
      top: BorderSide(
        color: color.withValues(alpha: opacity),
        width: width,
      ),
    );
  }

  /// 获取底部边框
  static Border getBottomBorder(
    Color color, {
    double opacity = 0.3,
    double width = Dimensions.borderWidthXs,
  }) {
    return Border(
      bottom: BorderSide(
        color: color.withValues(alpha: opacity),
        width: width,
      ),
    );
  }

  /// 获取左侧边框
  static Border getLeftBorder(
    Color color, {
    double opacity = 0.3,
    double width = Dimensions.borderWidthXs,
  }) {
    return Border(
      left: BorderSide(
        color: color.withValues(alpha: opacity),
        width: width,
      ),
    );
  }

  /// 获取右侧边框
  static Border getRightBorder(
    Color color, {
    double opacity = 0.3,
    double width = Dimensions.borderWidthXs,
  }) {
    return Border(
      right: BorderSide(
        color: color.withValues(alpha: opacity),
        width: width,
      ),
    );
  }
}
