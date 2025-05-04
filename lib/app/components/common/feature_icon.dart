import 'package:flutter/material.dart';

/// 功能图标组件
/// 一个带背景色的圆角方形图标，用于在功能列表中显示
class FeatureIcon extends StatelessWidget {
  /// 图标
  final IconData icon;

  /// 图标颜色
  final Color iconColor;

  /// 背景透明度，默认为0.25
  final double backgroundOpacity;

  /// 图标大小，默认为20
  final double iconSize;

  /// 容器大小，默认为36
  final double containerSize;

  /// 圆角大小，默认为10
  final double borderRadius;

  const FeatureIcon({
    super.key,
    required this.icon,
    required this.iconColor,
    this.backgroundOpacity = 0.25,
    this.iconSize = 20,
    this.containerSize = 36,
    this.borderRadius = 10,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      width: containerSize,
      height: containerSize,
      decoration: BoxDecoration(
        color: iconColor.withValues(alpha: backgroundOpacity),
        borderRadius: BorderRadius.circular(borderRadius),
      ),
      child: Icon(icon, color: iconColor, size: iconSize),
    );
  }
}
