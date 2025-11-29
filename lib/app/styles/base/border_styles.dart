/// 边框样式常量定义
///
/// 提供统一的边框样式，包括边框宽度、颜色透明度等
///
/// @deprecated 请使用 [Dimensions] 中的边框宽度常量（borderWidthXs/S/M/L/Xl）
/// 以及 [AppBorders] 中的主题感知边框方法
library;

import 'package:flutter/material.dart';
import 'opacities.dart';
import 'dimensions.dart';

/// 边框样式常量类
///
/// @deprecated 边框宽度常量已移至 [Dimensions]，建议迁移：
/// - BorderStyles.extraThin → Dimensions.borderWidthXs
/// - BorderStyles.thin → Dimensions.borderWidthS
/// - BorderStyles.medium → Dimensions.borderWidthM
/// - BorderStyles.thick → Dimensions.borderWidthL
/// - BorderStyles.extraThick → Dimensions.borderWidthXl
class BorderStyles {
  // 私有构造函数，防止实例化
  BorderStyles._();

  // 边框宽度常量（已移至 Dimensions）
  @Deprecated('使用 Dimensions.borderWidthXs 替代')
  static const double extraThin = Dimensions.borderWidthXs;

  @Deprecated('使用 Dimensions.borderWidthS 替代')
  static const double thin = Dimensions.borderWidthS;

  @Deprecated('使用 Dimensions.borderWidthM 替代')
  static const double medium = Dimensions.borderWidthM;

  @Deprecated('使用 Dimensions.borderWidthL 替代')
  static const double thick = Dimensions.borderWidthL;

  @Deprecated('使用 Dimensions.borderWidthXl 替代')
  static const double extraThick = Dimensions.borderWidthXl;

  // 边框透明度常量
  static const double borderOpacityLow = Opacities.medium;
  static const double borderOpacityMedium = Opacities.mediumHigh;
  static const double borderOpacityHigh = Opacities.high;

  /// 获取边框样式 - 极细边框
  @Deprecated('使用 AppBorders 替代')
  static BorderSide getExtraThinBorder(Color color, {double opacity = borderOpacityLow}) {
    return BorderSide(
      color: color.withValues(alpha: opacity),
      width: Dimensions.borderWidthXs,
    );
  }

  /// 获取边框样式 - 细边框
  @Deprecated('使用 AppBorders 替代')
  static BorderSide getThinBorder(Color color, {double opacity = borderOpacityMedium}) {
    return BorderSide(
      color: color.withValues(alpha: opacity),
      width: Dimensions.borderWidthS,
    );
  }

  /// 获取边框样式 - 中等边框
  @Deprecated('使用 AppBorders 替代')
  static BorderSide getMediumBorder(Color color, {double opacity = borderOpacityHigh}) {
    return BorderSide(
      color: color.withValues(alpha: opacity),
      width: Dimensions.borderWidthM,
    );
  }

  /// 获取边框样式 - 粗边框
  @Deprecated('使用 AppBorders 替代')
  static BorderSide getThickBorder(Color color, {double opacity = Opacities.opaque}) {
    return BorderSide(
      color: color.withValues(alpha: opacity),
      width: Dimensions.borderWidthL,
    );
  }

  /// 获取边框样式 - 极粗边框
  @Deprecated('使用 AppBorders 替代')
  static BorderSide getExtraThickBorder(Color color, {double opacity = Opacities.opaque}) {
    return BorderSide(
      color: color.withValues(alpha: opacity),
      width: Dimensions.borderWidthXl,
    );
  }

  /// 获取边框装饰 - 极细边框
  @Deprecated('使用 AppBorders 替代')
  static BoxDecoration getExtraThinBorderDecoration(
    Color color, {
    double opacity = borderOpacityLow,
    double radius = 0,
  }) {
    return BoxDecoration(
      border: Border.all(
        color: color.withValues(alpha: opacity),
        width: Dimensions.borderWidthXs,
      ),
      borderRadius: BorderRadius.circular(radius),
    );
  }

  /// 获取边框装饰 - 细边框
  @Deprecated('使用 AppBorders 替代')
  static BoxDecoration getThinBorderDecoration(Color color, {double opacity = borderOpacityMedium, double radius = 0}) {
    return BoxDecoration(
      border: Border.all(
        color: color.withValues(alpha: opacity),
        width: Dimensions.borderWidthS,
      ),
      borderRadius: BorderRadius.circular(radius),
    );
  }

  /// 获取边框装饰 - 中等边框
  @Deprecated('使用 AppBorders 替代')
  static BoxDecoration getMediumBorderDecoration(Color color, {double opacity = borderOpacityHigh, double radius = 0}) {
    return BoxDecoration(
      border: Border.all(
        color: color.withValues(alpha: opacity),
        width: Dimensions.borderWidthM,
      ),
      borderRadius: BorderRadius.circular(radius),
    );
  }

  /// 获取边框装饰 - 粗边框
  @Deprecated('使用 AppBorders 替代')
  static BoxDecoration getThickBorderDecoration(Color color, {double opacity = Opacities.opaque, double radius = 0}) {
    return BoxDecoration(
      border: Border.all(
        color: color.withValues(alpha: opacity),
        width: Dimensions.borderWidthL,
      ),
      borderRadius: BorderRadius.circular(radius),
    );
  }

  /// 获取顶部边框
  @Deprecated('使用 AppBorders.getTopBorder 替代')
  static Border getTopBorder(
    Color color, {
    double opacity = borderOpacityLow,
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
  @Deprecated('使用 AppBorders.getBottomBorder 替代')
  static Border getBottomBorder(
    Color color, {
    double opacity = borderOpacityLow,
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
  @Deprecated('使用 AppBorders.getLeftBorder 替代')
  static Border getLeftBorder(
    Color color, {
    double opacity = borderOpacityLow,
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
  @Deprecated('使用 AppBorders.getRightBorder 替代')
  static Border getRightBorder(
    Color color, {
    double opacity = borderOpacityLow,
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
