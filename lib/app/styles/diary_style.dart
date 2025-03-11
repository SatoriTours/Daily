import 'package:flutter/material.dart';

/// 日记模块的样式抽象
class DiaryStyle {
  /// 获取背景颜色
  static Color backgroundColor(BuildContext context) =>
      Theme.of(context).brightness == Brightness.dark ? Color(0xFF121212) : Color(0xFFF7F7F7);

  /// 获取卡片背景颜色
  static Color cardColor(BuildContext context) =>
      Theme.of(context).brightness == Brightness.dark ? Color(0xFF1E1E1E) : Colors.white;

  /// 获取卡片阴影
  static List<BoxShadow>? cardShadow(BuildContext context) {
    return Theme.of(context).brightness == Brightness.dark
        ? [BoxShadow(color: Colors.black.withAlpha(51), blurRadius: 8, offset: Offset(0, 2))]
        : [BoxShadow(color: Colors.black.withAlpha(8), blurRadius: 8, offset: Offset(0, 2))];
  }

  /// 获取主要文本颜色
  static Color primaryTextColor(BuildContext context) =>
      Theme.of(context).brightness == Brightness.dark ? Colors.white : Colors.black87;

  /// 获取次要文本颜色
  static Color secondaryTextColor(BuildContext context) =>
      Theme.of(context).brightness == Brightness.dark ? Colors.white70 : Colors.black54;

  /// 获取时间文本颜色
  static Color timeTextColor(BuildContext context) =>
      Theme.of(context).brightness == Brightness.dark ? Colors.grey[400]! : Colors.grey[500]!;

  /// 获取输入框背景颜色
  static Color inputBackgroundColor(BuildContext context) =>
      Theme.of(context).brightness == Brightness.dark ? Color(0xFF2C2C2C) : Color(0xFFF5F5F5);

  /// 获取标签背景颜色
  static Color tagBackgroundColor(BuildContext context) =>
      Theme.of(context).brightness == Brightness.dark ? Color(0xFF2E3A59) : Color(0xFFEDF3FF);

  /// 获取标签文本颜色
  static Color tagTextColor(BuildContext context) =>
      Theme.of(context).brightness == Brightness.dark ? Color(0xFF8AB4F8) : Color(0xFF5E8BFF);

  /// 获取强调色
  static Color accentColor(BuildContext context) =>
      Theme.of(context).brightness == Brightness.dark ? Color(0xFF8AB4F8) : Color(0xFF5E8BFF);

  /// 获取分割线颜色
  static Color dividerColor(BuildContext context) =>
      Theme.of(context).brightness == Brightness.dark ? Colors.grey[800]! : Colors.grey[200]!;

  /// 获取底部抽屉背景色
  static Color bottomSheetColor(BuildContext context) =>
      Theme.of(context).brightness == Brightness.dark ? Color(0xFF1E1E1E) : Colors.white;
}
