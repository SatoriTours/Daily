import 'package:flutter/material.dart';
import 'package:daily_satori/app/styles/theme/theme_data.dart';

/// 应用主题管理类
/// 提供主题设置、获取和切换的功能
class AppTheme {
  // 私有构造函数，防止实例化
  AppTheme._();

  /// 获取亮色主题
  static ThemeData get light => AppThemeData.getLightTheme();

  /// 获取暗色主题
  static ThemeData get dark => AppThemeData.getDarkTheme();

  /// 获取当前主题
  static ThemeData getTheme(BuildContext context) {
    return Theme.of(context);
  }

  /// 判断当前是否为暗色模式
  static bool isDarkMode(BuildContext context) {
    return Theme.of(context).brightness == Brightness.dark;
  }

  /// 获取当前主题的颜色方案
  static ColorScheme getColorScheme(BuildContext context) {
    return Theme.of(context).colorScheme;
  }

  /// 获取当前主题的文本主题
  static TextTheme getTextTheme(BuildContext context) {
    return Theme.of(context).textTheme;
  }

  /// 获取基于亮暗模式的主题
  static ThemeData getThemeByBrightness(Brightness brightness) {
    return brightness == Brightness.light ? light : dark;
  }
}
