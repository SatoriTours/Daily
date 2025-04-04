import 'package:flutter/material.dart';

import 'package:daily_satori/app/styles/component_style.dart';

/// 自定义分隔线
///
/// 一个可自定义高度和缩进的分隔线组件。
/// 使用应用程序的主题样式，提供一致的视觉外观。
///
/// 使用示例:
/// ```dart
/// CustomDivider(
///   height: 1.0,
///   indent: 16.0,
///   endIndent: 16.0,
/// )
/// ```
class CustomDivider extends StatelessWidget {
  /// 分隔线高度
  ///
  /// 默认为 1.0 像素
  final double height;

  /// 起始缩进
  ///
  /// 从左边开始的缩进距离，默认为 0.0
  final double indent;

  /// 结束缩进
  ///
  /// 从右边开始的缩进距离，默认为 0.0
  final double endIndent;

  /// 创建一个自定义分隔线
  ///
  /// [height] 分隔线高度，默认为 1.0
  /// [indent] 起始缩进，默认为 0.0
  /// [endIndent] 结束缩进，默认为 0.0
  const CustomDivider({super.key, this.height = 1.0, this.indent = 0.0, this.endIndent = 0.0});

  @override
  Widget build(BuildContext context) {
    return ComponentStyle.customDivider(context, height: height, indent: indent, endIndent: endIndent);
  }
}
