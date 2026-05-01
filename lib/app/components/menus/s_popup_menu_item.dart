import 'package:flutter/material.dart';
import 'package:daily_satori/app/styles/styles.dart';

/// 通用的弹出菜单项（统一图标、间距、尺寸等样式）
class SPopupMenuItem<T> extends PopupMenuItem<T> {
  SPopupMenuItem({
    super.key,
    required T super.value,
    required IconData icon,
    required String text,
    Color? iconColor,
    double? iconSize,
    double? spacing,
    TextStyle? textStyle,
    EdgeInsets? padding,
    super.enabled,
  }) : super(
         padding:
             padding ??
             const EdgeInsets.symmetric(
               horizontal: Dimensions.spacingM,
               vertical: Dimensions.spacingS,
             ),
         child: Row(
           children: [
             Icon(
               icon,
               size: iconSize ?? Dimensions.iconSizeM,
               color: iconColor,
             ),
             SizedBox(width: spacing ?? Dimensions.spacingS),
             Text(text, style: textStyle),
           ],
         ),
       );
}
