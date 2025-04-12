import 'package:flutter/material.dart';

/// 应用颜色常量
/// 提供统一的颜色定义，遵循 shadcn/ui 的设计风格
class AppColors {
  // 私有构造函数，防止实例化
  AppColors._();

  // 获取当前主题颜色的辅助方法
  static Color getThemeColor(BuildContext context, Color lightColor, Color darkColor) {
    return Theme.of(context).brightness == Brightness.light ? lightColor : darkColor;
  }

  // 品牌色
  static const Color primary = Color(0xFF5E8BFF); // 主色
  static const Color primaryLight = Color(0xFF8AB4F8); // 主色-亮
  static const Color primaryDark = Color(0xFF3A5CAA); // 主色-暗

  // 背景色
  static const Color background = Color(0xFFF7F7F7); // 背景色
  static const Color backgroundDark = Color(0xFF121212); // 背景色-暗

  // 表面色
  static const Color surface = Color(0xFFFFFFFF); // 表面色
  static const Color surfaceDark = Color(0xFF1E1E1E); // 表面色-暗

  // 表面容器色
  static const Color surfaceContainer = Color(0xFFF0F0F0); // 表面容器色
  static const Color surfaceContainerDark = Color(0xFF2C2C2C); // 表面容器色-暗

  // 表面容器高亮色（更深）
  static const Color surfaceContainerHighest = Color(0xFFE0E0E0); // 表面容器高亮色
  static const Color surfaceContainerHighestDark = Color(0xFF3A3A3A); // 表面容器高亮色-暗

  // 文本色
  static const Color onBackground = Color(0xFF212121); // 背景上文本
  static const Color onBackgroundDark = Color(0xFFE0E0E0); // 背景上文本-暗

  static const Color onSurface = Color(0xFF424242); // 表面上文本
  static const Color onSurfaceDark = Color(0xFFBDBDBD); // 表面上文本-暗

  static const Color onSurfaceVariant = Color(0xFF757575); // 表面上次要文本
  static const Color onSurfaceVariantDark = Color(0xFF9E9E9E); // 表面上次要文本-暗

  // 边框和分隔线色
  static const Color outline = Color(0xFFE0E0E0); // 边框色
  static const Color outlineDark = Color(0xFF424242); // 边框色-暗

  static const Color outlineVariant = Color(0xFFBDBDBD); // 次要边框色
  static const Color outlineVariantDark = Color(0xFF757575); // 次要边框色-暗

  // 功能色
  static const Color success = Color(0xFF4CAF50); // 成功色
  static const Color successDark = Color(0xFF66BB6A); // 成功色-暗

  static const Color error = Color(0xFFF44336); // 错误色
  static const Color errorDark = Color(0xFFE57373); // 错误色-暗

  static const Color warning = Color(0xFFFF9800); // 警告色
  static const Color warningDark = Color(0xFFFFB74D); // 警告色-暗

  static const Color info = Color(0xFF2196F3); // 信息色
  static const Color infoDark = Color(0xFF64B5F6); // 信息色-暗

  // 标签颜色组
  static const List<Color> tagColors = [
    Color(0xFF5E8BFF), // 蓝色
    Color(0xFF26A69A), // 青色
    Color(0xFF66BB6A), // 绿色
    Color(0xFF9CCC65), // 浅绿
    Color(0xFFD4E157), // 酸橙
    Color(0xFFFFEE58), // 黄色
    Color(0xFFFFCA28), // 琥珀
    Color(0xFFFFB74D), // 橙色
    Color(0xFFFF8A65), // 深橙
    Color(0xFFE57373), // 红色
  ];

  static const List<Color> tagColorsDark = [
    Color(0xFF8AB4F8), // 蓝色
    Color(0xFF4DB6AC), // 青色
    Color(0xFF81C784), // 绿色
    Color(0xFFAED581), // 浅绿
    Color(0xFFDCE775), // 酸橙
    Color(0xFFFFF176), // 黄色
    Color(0xFFFFD54F), // 琥珀
    Color(0xFFFFCC80), // 橙色
    Color(0xFFFFAB91), // 深橙
    Color(0xFFEF9A9A), // 红色
  ];

  // 主题相关颜色获取方法
  static Color getPrimary(BuildContext context) => getThemeColor(context, primary, primaryLight);

  static Color getBackground(BuildContext context) => getThemeColor(context, background, backgroundDark);

  static Color getSurface(BuildContext context) => getThemeColor(context, surface, surfaceDark);

  static Color getSurfaceContainer(BuildContext context) =>
      getThemeColor(context, surfaceContainer, surfaceContainerDark);

  static Color getSurfaceContainerHighest(BuildContext context) =>
      getThemeColor(context, surfaceContainerHighest, surfaceContainerHighestDark);

  static Color getOnBackground(BuildContext context) => getThemeColor(context, onBackground, onBackgroundDark);

  static Color getOnSurface(BuildContext context) => getThemeColor(context, onSurface, onSurfaceDark);

  static Color getOnSurfaceVariant(BuildContext context) =>
      getThemeColor(context, onSurfaceVariant, onSurfaceVariantDark);

  static Color getOutline(BuildContext context) => getThemeColor(context, outline, outlineDark);

  static Color getOutlineVariant(BuildContext context) => getThemeColor(context, outlineVariant, outlineVariantDark);

  static Color getSuccess(BuildContext context) => getThemeColor(context, success, successDark);

  static Color getError(BuildContext context) => getThemeColor(context, error, errorDark);

  static Color getWarning(BuildContext context) => getThemeColor(context, warning, warningDark);

  static Color getInfo(BuildContext context) => getThemeColor(context, info, infoDark);

  static List<Color> getTagColors(BuildContext context) =>
      Theme.of(context).brightness == Brightness.light ? tagColors : tagColorsDark;
}
