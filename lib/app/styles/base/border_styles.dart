/// 边框样式常量定义
///
/// 提供统一的边框样式，包括边框宽度、颜色透明度等
library;

import 'package:flutter/material.dart';
import 'opacities.dart';

/// 边框样式常量类
class BorderStyles {
  // 私有构造函数，防止实例化
  BorderStyles._();

  // 边框宽度常量
  static const double extraThin = 0.5;
  static const double thin = 1.0;
  static const double medium = 1.5;
  static const double thick = 2.0;
  static const double extraThick = 4.0;

  // 边框透明度常量
  static const double borderOpacityLow = Opacities.medium;
  static const double borderOpacityMedium = Opacities.mediumHigh;
  static const double borderOpacityHigh = Opacities.high;

  /// 获取边框样式 - 极细边框
  static BorderSide getExtraThinBorder(Color color, {double opacity = borderOpacityLow}) {
    return BorderSide(color: color.withValues(alpha: opacity), width: extraThin);
  }

  /// 获取边框样式 - 细边框
  static BorderSide getThinBorder(Color color, {double opacity = borderOpacityMedium}) {
    return BorderSide(color: color.withValues(alpha: opacity), width: thin);
  }

  /// 获取边框样式 - 中等边框
  static BorderSide getMediumBorder(Color color, {double opacity = borderOpacityHigh}) {
    return BorderSide(color: color.withValues(alpha: opacity), width: medium);
  }

  /// 获取边框样式 - 粗边框
  static BorderSide getThickBorder(Color color, {double opacity = Opacities.opaque}) {
    return BorderSide(color: color.withValues(alpha: opacity), width: thick);
  }

  /// 获取边框样式 - 极粗边框
  static BorderSide getExtraThickBorder(Color color, {double opacity = Opacities.opaque}) {
    return BorderSide(color: color.withValues(alpha: opacity), width: extraThick);
  }

  /// 获取边框装饰 - 极细边框
  static BoxDecoration getExtraThinBorderDecoration(Color color, {double opacity = borderOpacityLow, double radius = 0}) {
    return BoxDecoration(
      border: Border.all(color: color.withValues(alpha: opacity), width: extraThin),
      borderRadius: BorderRadius.circular(radius),
    );
  }

  /// 获取边框装饰 - 细边框
  static BoxDecoration getThinBorderDecoration(Color color, {double opacity = borderOpacityMedium, double radius = 0}) {
    return BoxDecoration(
      border: Border.all(color: color.withValues(alpha: opacity), width: thin),
      borderRadius: BorderRadius.circular(radius),
    );
  }

  /// 获取边框装饰 - 中等边框
  static BoxDecoration getMediumBorderDecoration(Color color, {double opacity = borderOpacityHigh, double radius = 0}) {
    return BoxDecoration(
      border: Border.all(color: color.withValues(alpha: opacity), width: medium),
      borderRadius: BorderRadius.circular(radius),
    );
  }

  /// 获取边框装饰 - 粗边框
  static BoxDecoration getThickBorderDecoration(Color color, {double opacity = Opacities.opaque, double radius = 0}) {
    return BoxDecoration(
      border: Border.all(color: color.withValues(alpha: opacity), width: thick),
      borderRadius: BorderRadius.circular(radius),
    );
  }

  /// 获取顶部边框
  static Border getTopBorder(Color color, {double opacity = borderOpacityLow, double width = extraThin}) {
    return Border(top: BorderSide(color: color.withValues(alpha: opacity), width: width));
  }

  /// 获取底部边框
  static Border getBottomBorder(Color color, {double opacity = borderOpacityLow, double width = extraThin}) {
    return Border(bottom: BorderSide(color: color.withValues(alpha: opacity), width: width));
  }

  /// 获取左侧边框
  static Border getLeftBorder(Color color, {double opacity = borderOpacityLow, double width = extraThin}) {
    return Border(left: BorderSide(color: color.withValues(alpha: opacity), width: width));
  }

  /// 获取右侧边框
  static Border getRightBorder(Color color, {double opacity = borderOpacityLow, double width = extraThin}) {
    return Border(right: BorderSide(color: color.withValues(alpha: opacity), width: width));
  }
}