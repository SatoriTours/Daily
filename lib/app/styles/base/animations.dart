import 'package:flutter/material.dart';

/// 动画常量类
///
/// 统一管理应用中的动画时长和曲线，确保动画效果的一致性
class Animations {
  Animations._();

  // ========================================================================
  // 动画时长
  // ========================================================================

  /// 快速动画时长 - 150ms
  static const Duration durationFast = Duration(milliseconds: 150);

  /// 默认动画时长 - 300ms
  static const Duration durationNormal = Duration(milliseconds: 300);

  /// 慢速动画时长 - 500ms
  static const Duration durationSlow = Duration(milliseconds: 500);

  // ========================================================================
  // 动画曲线
  // ========================================================================

  /// 默认动画曲线
  static const Curve curveDefault = Curves.easeInOut;

  /// 进入动画曲线
  static const Curve curveEaseIn = Curves.easeIn;

  /// 退出动画曲线
  static const Curve curveEaseOut = Curves.easeOut;

  /// 弹跳曲线
  static const Curve curveBounce = Curves.bounceOut;

  /// 弹性曲线
  static const Curve curveElastic = Curves.elasticOut;
}
