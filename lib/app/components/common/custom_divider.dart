import 'package:flutter/material.dart';

import 'package:daily_satori/app/styles/component_style.dart';

/// 自定义分隔线
class CustomDivider extends StatelessWidget {
  final double height;
  final double indent;
  final double endIndent;

  const CustomDivider({super.key, this.height = 1.0, this.indent = 0.0, this.endIndent = 0.0});

  @override
  Widget build(BuildContext context) {
    return ComponentStyle.customDivider(context, height: height, indent: indent, endIndent: endIndent);
  }
}
