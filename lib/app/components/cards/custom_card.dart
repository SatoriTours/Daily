import 'package:flutter/material.dart';
import 'package:daily_satori/app/styles/styles.dart';

/// 自定义卡片组件
///
/// 一个可定制的卡片组件，支持以下特性：
/// - 可点击
/// - 自定义内边距
/// - 自定义阴影高度
/// - 圆角边框
/// - 自动适应主题颜色
///
/// 使用示例:
/// ```dart
/// CustomCard(
///   onTap: () => print('卡片被点击'),
///   child: Text('卡片内容'),
///   elevation: 2.0,
///   padding: Dimensions.paddingM,
/// )
/// ```
class CustomCard extends StatelessWidget {
  /// 卡片内容
  final Widget child;

  /// 点击回调
  ///
  /// 如果为 null，卡片将不可点击
  final VoidCallback? onTap;

  /// 内边距
  ///
  /// 默认为 Dimensions.paddingM
  final EdgeInsetsGeometry? padding;

  /// 阴影高度
  ///
  /// 控制卡片的阴影效果，默认为 2.0
  final double elevation;

  /// 创建一个自定义卡片
  ///
  /// [child] 卡片内容
  /// [onTap] 点击回调，可选
  /// [padding] 内边距，可选
  /// [elevation] 阴影高度，默认为 2.0
  const CustomCard({
    super.key,
    required this.child,
    this.onTap,
    this.padding,
    this.elevation = 2.0,
  });
  @override
  Widget build(BuildContext context) {
    return Card(
      elevation: elevation,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
      color: AppColors.getSurface(context),
      child: _buildCardContent(),
    );
  }

  /// 构建卡片内容
  Widget _buildCardContent() {
    final cardChild = Padding(
      padding: padding ?? Dimensions.paddingM,
      child: child,
    );
    if (onTap == null) {
      return cardChild;
    }
    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(Dimensions.radiusM),
      child: cardChild,
    );
  }
}
