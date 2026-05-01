import 'package:flutter/material.dart';
import 'package:daily_satori/app/styles/styles.dart';

/// 按钮组件
///
/// 一个排列多个按钮的容器组件
/// 可以水平排列按钮并保持统一的间距
///
/// 使用示例:
/// ```dart
/// ButtonGroup(
///   children: [
///     AppButton(title: '取消', type: AppButtonType.secondary, onPressed: () {}),
///     AppButton(title: '保存', type: AppButtonType.primary, onPressed: () {}),
///   ],
/// )
/// ```
class ButtonGroup extends StatelessWidget {
  /// 按钮子组件列表
  final List<Widget> children;

  /// 按钮间距，默认使用中等水平间距
  final double? spacing;

  /// 布局方向，默认为水平
  final Axis direction;

  /// 底部边距，默认为24
  final double bottomPadding;

  /// 创建一个按钮组
  ///
  /// [children] 按钮子组件列表
  /// [spacing] 按钮间的间距
  /// [direction] 布局方向
  /// [bottomPadding] 底部边距
  const ButtonGroup({
    super.key,
    required this.children,
    this.spacing,
    this.direction = Axis.horizontal,
    this.bottomPadding = 24,
  });
  @override
  Widget build(BuildContext context) {
    final finalSpacing = spacing ?? Dimensions.spacingM;
    return Padding(
      padding: EdgeInsets.only(bottom: bottomPadding),
      child: direction == Axis.horizontal
          ? Row(children: _addSpacingBetween(children, finalSpacing))
          : Column(children: _addSpacingBetween(children, finalSpacing)),
    );
  }

  /// 在子组件之间添加间距
  List<Widget> _addSpacingBetween(List<Widget> widgets, double spacing) {
    if (widgets.length <= 1) return widgets;
    final result = <Widget>[];
    for (int i = 0; i < widgets.length; i++) {
      // 添加子组件
      result.add(Expanded(child: widgets[i]));
      // 如果不是最后一个子组件，添加间距
      if (i < widgets.length - 1) {
        result.add(
          direction == Axis.horizontal
              ? SizedBox(width: spacing)
              : SizedBox(height: spacing),
        );
      }
    }
    return result;
  }
}
