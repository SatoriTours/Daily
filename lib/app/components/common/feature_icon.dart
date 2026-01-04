import 'package:flutter/material.dart';
import 'package:daily_satori/app/styles/styles.dart';

/// 功能图标组件
///
/// 一个带背景色的圆角方形图标，用于在功能列表中显示
class FeatureIcon extends StatelessWidget {
  /// 图标
  final IconData icon;

  /// 图标颜色
  final Color iconColor;

  /// 背景透明度，默认为0.25
  final double backgroundOpacity;

  /// 图标大小，默认为 Dimensions.iconSizeM
  final double? iconSize;

  /// 容器大小，默认为 36
  final double? containerSize;

  /// 圆角大小，默认为 Dimensions.radiusS
  final double? borderRadius;

  const FeatureIcon({
    super.key,
    required this.icon,
    required this.iconColor,
    this.backgroundOpacity = 0.25,
    this.iconSize,
    this.containerSize,
    this.borderRadius,
  });

  @override
  Widget build(BuildContext context) {
    final size = containerSize ?? 36;
    return Container(
      width: size,
      height: size,
      decoration: BoxDecoration(
        color: iconColor.withValues(alpha: backgroundOpacity),
        borderRadius: BorderRadius.circular(borderRadius ?? Dimensions.radiusS),
      ),
      child: Icon(icon, color: iconColor, size: iconSize ?? Dimensions.iconSizeM),
    );
  }
}
