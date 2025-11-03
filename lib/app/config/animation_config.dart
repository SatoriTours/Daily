import 'package:flutter/material.dart';

/// 动画配置
class AnimationConfig {
  AnimationConfig._();

  static const Duration duration = Duration(milliseconds: 300); // 默认动画时长
  static const Duration shortDuration = Duration(milliseconds: 150); // 短动画时长
  static const Duration longDuration = Duration(milliseconds: 500); // 长动画时长

  static const Curve curve = Curves.easeInOut; // 默认动画曲线
  static const Curve bounceCurve = Curves.bounceOut; // 弹跳曲线
}
