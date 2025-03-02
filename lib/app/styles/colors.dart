import 'package:flutter/material.dart';

class AppColors {
  // 私有构造函数，防止实例化
  AppColors._();

  // 主要颜色
  static const Color primary = Color(0xFF3F51B5); // 靛蓝色
  static const Color primaryLight = Color(0xFF757DE8); // 浅靛蓝色
  static const Color primaryDark = Color(0xFF002984); // 深靛蓝色

  // 强调色
  static const Color accent = Color(0xFFFF9800); // 橙色
  static const Color accentLight = Color(0xFFFFC947); // 浅橙色
  static const Color accentDark = Color(0xFFC66900); // 深橙色

  // 文本颜色
  static const Color textPrimary = Color(0xFF212121); // 主要文本
  static const Color textSecondary = Color(0xFF757575); // 次要文本
  static const Color textHint = Color(0xFFBDBDBD); // 提示文本

  // 背景颜色
  static const Color background = Color(0xFFF5F5F5); // 主背景
  static const Color cardBackground = Color(0xFFFFFFFF); // 卡片背景
  static const Color divider = Color(0xFFE0E0E0); // 分隔线

  // 功能性颜色
  static const Color success = Color(0xFF4CAF50); // 成功
  static const Color error = Color(0xFFF44336); // 错误
  static const Color warning = Color(0xFFFF9800); // 警告
  static const Color info = Color(0xFF2196F3); // 信息

  // 标签颜色
  static const List<Color> tagColors = [
    Color(0xFF3F51B5), // 靛蓝
    Color(0xFF009688), // 青色
    Color(0xFF4CAF50), // 绿色
    Color(0xFF8BC34A), // 浅绿
    Color(0xFFCDDC39), // 酸橙
    Color(0xFFFFEB3B), // 黄色
    Color(0xFFFFC107), // 琥珀
    Color(0xFFFF9800), // 橙色
    Color(0xFFFF5722), // 深橙
    Color(0xFFF44336), // 红色
  ];
}
