import 'package:flutter/material.dart';

/// 通用的弹出菜单项（统一图标、间距、尺寸等样式）
class SPopupMenuItem<T> extends PopupMenuItem<T> {
  SPopupMenuItem({
    super.key,
    required T super.value,
    required IconData icon,
    required String text,
    Color? iconColor,
    double iconSize = 20,
    double spacing = 8,
    TextStyle? textStyle,
    EdgeInsets? padding,
    super.enabled,
  }) : super(
         padding: padding ?? const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
         child: Row(
           children: [
             Icon(icon, size: iconSize, color: iconColor),
             SizedBox(width: spacing),
             Text(text, style: textStyle),
           ],
         ),
       );
}
